package dev.clawdboard.core

import android.content.Context
import dev.clawdboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.SecretKey

class Repository(private val app: Context) {
    val settings = SettingsStore(app)
    val vault = Vault(app)
    val history = History(app)
    val pairing = Pairing(app)
    val panel = PanelServer(app, this)
    val nudge = Nudge(app)
    val music = Music(app)

    data class State(
        val provisioned: Boolean = false,
        val unlocked: Boolean = false,
        val failures: Int = 0,
        val usage: UsageSnapshot? = null,
        val lastPushAt: Long? = null,
        val status: StatusSnapshot? = null,
        val news: List<NewsItem> = emptyList(),
        val panelUrl: String? = null,
        val wiped: Boolean = false,
    )

    sealed interface Outcome {
        data object Ok : Outcome
        data class Error(val message: String) : Outcome
        data class WrongPin(val remaining: Int) : Outcome
        data object Wiped : Outcome
    }

    private val _state = MutableStateFlow(
        State(
            provisioned = vault.isProvisioned,
            failures = vault.failures,
            usage = pairing.restore(System.currentTimeMillis()),
            lastPushAt = pairing.lastPushAt,
        )
    )
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var pairKey: String? = null
    @Volatile private var sessionKey: SecretKey? = null
    private val vaultLock = Mutex()

    fun startPanel() {
        if (settings.value.panelEnabled) panel.start()
        refreshPanelUrl()
    }

    fun refreshPanelUrl() {
        val ip = localIpv4(app)
        val url = if (panel.port > 0 && ip != null) "http://$ip:${panel.port}" else null
        _state.update { it.copy(panelUrl = url) }
    }

    private fun adopt(secret: String, key: SecretKey): String {
        if (secret.startsWith("sk-ant-") || secret.isBlank()) {
            val fresh = Pairing.newKey()
            vault.reseal(key, fresh)
            pairing.remember(fresh)
            return fresh
        }
        if (!pairing.matches(secret)) pairing.remember(secret)
        return secret
    }

    suspend fun unlock(pin: String): Outcome = withContext(Dispatchers.Default) {
        vaultLock.withLock {
            when (val r = vault.unlock(pin)) {
                is Vault.Unlock.Ok -> {
                    pairKey = adopt(r.token, r.key)
                    sessionKey = r.key
                    _state.update { it.copy(unlocked = true, failures = 0, wiped = false) }
                    Outcome.Ok
                }
                is Vault.Unlock.Wrong -> {
                    _state.update { it.copy(failures = vault.failures) }
                    Outcome.WrongPin(r.remaining)
                }
                Vault.Unlock.Wiped -> {
                    factoryResetInternal()
                    Outcome.Wiped
                }
                Vault.Unlock.NotProvisioned -> Outcome.Error("O Clawdboard ainda não foi configurado")
                is Vault.Unlock.Broken -> Outcome.Error(r.reason)
            }
        }
    }

    suspend fun provision(pin: String): Outcome {
        Vault.pinProblem(pin)?.let { return Outcome.Error(it) }
        return withContext(Dispatchers.Default) {
            vaultLock.withLock {
                if (vault.isProvisioned) return@withLock Outcome.Error("O Clawdboard já está configurado. Faça login com o PIN.")
                val fresh = Pairing.newKey()
                sessionKey = vault.provision(pin, fresh)
                pairing.remember(fresh)
                pairKey = fresh
                _state.update { it.copy(provisioned = true, unlocked = true, failures = 0, wiped = false) }
                Outcome.Ok
            }
        }
    }

    suspend fun newPairKey(): Outcome {
        val key = sessionKey ?: return Outcome.Error("Desbloqueie com o PIN primeiro")
        return withContext(Dispatchers.Default) {
            vaultLock.withLock {
                val fresh = Pairing.newKey()
                vault.reseal(key, fresh)
                pairing.remember(fresh)
                pairKey = fresh
                Outcome.Ok
            }
        }
    }

    suspend fun changePin(currentPin: String, newPin: String): Outcome {
        Vault.pinProblem(newPin)?.let { return Outcome.Error(it) }
        return withContext(Dispatchers.Default) {
            vaultLock.withLock {
                when (val r = vault.unlock(currentPin)) {
                    is Vault.Unlock.Ok -> {
                        val key = vault.provision(newPin, r.token)
                        sessionKey = key
                        pairKey = adopt(r.token, key)
                        _state.update { it.copy(unlocked = true, failures = 0) }
                        Outcome.Ok
                    }
                    is Vault.Unlock.Wrong -> {
                        _state.update { it.copy(failures = vault.failures) }
                        Outcome.WrongPin(r.remaining)
                    }
                    Vault.Unlock.Wiped -> {
                        factoryResetInternal()
                        Outcome.Wiped
                    }
                    Vault.Unlock.NotProvisioned -> Outcome.Error("O Clawdboard ainda não foi configurado")
                    is Vault.Unlock.Broken -> Outcome.Error(r.reason)
                }
            }
        }
    }

    fun lock() {
        pairKey = null
        sessionKey = null
        panel.invalidateSessions()
        _state.update { it.copy(unlocked = false) }
    }

    fun factoryReset() = factoryResetInternal()

    private fun factoryResetInternal() {
        pairKey = null
        sessionKey = null
        vault.wipe()
        pairing.clear()
        history.clear()
        settings.clear()
        panel.invalidateSessions()
        if (!panel.isRunning) panel.start()
        _state.value = State(provisioned = false, wiped = true, news = _state.value.news, status = _state.value.status)
        refreshPanelUrl()
    }

    fun updateSettings(f: (Prefs) -> Prefs): Prefs {
        val old = settings.value
        val new = settings.update(f)
        if (old.panelEnabled != new.panelEnabled) {
            if (new.panelEnabled) panel.start() else panel.stop()
            refreshPanelUrl()
        }
        return new
    }

    fun pushKeyValid(key: String?): Boolean = pairing.matches(key)

    fun receivePush(body: JSONObject): Boolean {
        val now = System.currentTimeMillis()
        val snap = parsePush(body, now) ?: return false
        pairing.save(body, now)
        history.record(snap)
        _state.update { it.copy(usage = snap.settled(now), lastPushAt = now) }
        return true
    }

    fun pairCommand(host: String): String? = pairKey?.let { Pairing.command("http://$host", it) }

    fun installerFor(host: String, key: String?): String? =
        if (key != null && pairing.matches(key)) pairing.installer("http://$host", key.trim()) else null

    @Volatile private var demo = false

    fun enterDemo(p5: Double = 37.0, p7: Double = 64.0, pf: Double = 22.0): Boolean {
        if (vault.isProvisioned) return false
        demo = true
        val now = System.currentTimeMillis()
        val snap = UsageSnapshot(
            UsageWindow(p5, now + 2 * 3_600_000L + 13 * 60_000L),
            UsageWindow(p7, now + 3 * 86_400_000L + 5 * 3_600_000L),
            listOf(ScopedLimit("Fable", pf, null)),
            now - 12_000L,
        )
        val status = StatusSnapshot(setOf("Opus"), listOf(Incident("Elevated errors on Claude Opus", "minor", "investigating", null)), now)
        val slot = History.SLOT_MS
        val start = (now - History.WINDOW_MS) / slot * slot + slot
        val samples = mutableListOf<Sample>()
        var t = start
        var i = 0
        while (t <= now) {
            val gap = t in (now - 4 * 86_400_000L)..(now - 4 * 86_400_000L + 9 * 3_600_000L)
            if (!gap) {
                val p5 = ((i % 10) * 9.5 + (i / 10 % 3) * 4.0).coerceAtMost(96.0)
                val p7 = (i.toDouble() / 336.0 * 64.0).coerceAtMost(64.0)
                samples += Sample(t, p5, p7)
            }
            t += slot
            i++
        }
        history.preview(samples)
        _state.update { it.copy(provisioned = true, unlocked = true, usage = snap, lastPushAt = now - 12_000L, status = status) }
        return true
    }

    suspend fun runLoops() = coroutineScope {
        launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                _state.update { s ->
                    val u = s.usage ?: return@update s
                    val settled = u.settled(now)
                    if (settled === u) s else s.copy(usage = settled)
                }
                delay(30_000)
            }
        }
        launch {
            while (isActive) {
                refreshPanelUrl()
                if (!demo) withContext(Dispatchers.IO) { StatusApi.fetch() }?.let { s -> _state.update { it.copy(status = s) } }
                delay(120_000)
            }
        }
        launch {
            while (isActive) {
                val n = withContext(Dispatchers.IO) { NewsApi.fetch() }
                if (n != null) {
                    _state.update { it.copy(news = n) }
                    delay(30 * 60_000L)
                } else {
                    delay(5 * 60_000L)
                }
            }
        }
    }

    fun infoJson(): JSONObject {
        val s = _state.value
        return JSONObject()
            .put("name", "Clawdboard")
            .put("version", BuildConfig.VERSION_NAME)
            .put("provisioned", vault.isProvisioned)
            .put("unlocked", s.unlocked)
            .put("remaining", vault.remaining)
            .put("wiped", s.wiped)
            .put("contact", Nudge.EMAIL)
    }

    fun stateJson(host: String): JSONObject {
        val s = _state.value
        val news = JSONArray()
        s.news.take(8).forEach {
            news.put(JSONObject().put("title", it.title).put("link", it.link).put("date", it.date ?: JSONObject.NULL).put("category", it.category ?: JSONObject.NULL))
        }
        return infoJson()
            .put("now", System.currentTimeMillis())
            .put("usage", s.usage?.toJson() ?: JSONObject.NULL)
            .put("lastPushAt", s.lastPushAt ?: JSONObject.NULL)
            .put("pairCommand", pairCommand(host) ?: JSONObject.NULL)
            .put("status", s.status?.toJson() ?: JSONObject.NULL)
            .put("history", history.toJson())
            .put("news", news)
            .put("settings", settings.value.toJson())
            .put("options", Prefs.optionsJson())
            .put("look", lookJson(settings.value))
            .put("mascots", mascotsJson(s.usage, s.status))
            .put("music", musicJson())
            .put("panelUrl", s.panelUrl ?: JSONObject.NULL)
    }

    private fun musicJson(): Any {
        val t = music.track.value
        if (!settings.value.music || t == null) return JSONObject.NULL
        return JSONObject().put("playing", t.playing).put("title", t.title).put("artist", t.artist).put("app", t.app)
    }
}
