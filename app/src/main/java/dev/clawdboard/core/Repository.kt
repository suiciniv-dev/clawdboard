package dev.clawdboard.core

import android.content.Context
import android.util.Log
import dev.clawdboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
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
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.SecretKey

class Repository(private val app: Context) {
    private companion object {
        const val TAG = "Clawdboard"
        const val SCOPED_KEEP_MS = 3 * 60 * 60_000L
    }

    val settings = SettingsStore(app)
    val vault = Vault(app)
    val history = History(app)
    val panel = PanelServer(app, this)
    val nudge = Nudge(app)

    data class State(
        val provisioned: Boolean = false,
        val unlocked: Boolean = false,
        val failures: Int = 0,
        val usage: UsageSnapshot? = null,
        val usageError: String? = null,
        val usageNote: String? = null,
        val authError: Boolean = false,
        val lastAttemptAt: Long? = null,
        val nextRefreshAt: Long? = null,
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

    private val _state = MutableStateFlow(State(provisioned = vault.isProvisioned, failures = vault.failures))
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var token: String? = null
    @Volatile private var sessionKey: SecretKey? = null
    private val vaultLock = Mutex()
    private val refreshSignal = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var usageDeniedUntil = 0L
    @Volatile private var usageLimitedUntil = 0L
    @Volatile private var lastScoped: Pair<Long, List<ScopedLimit>>? = null
    @Volatile private var loggedRateHeaders: Map<String, String>? = null

    fun startPanel() {
        if (settings.value.panelEnabled) panel.start()
        refreshPanelUrl()
    }

    fun refreshPanelUrl() {
        val ip = localIpv4(app)
        val url = if (panel.port > 0 && ip != null) "http://$ip:${panel.port}" else null
        _state.update { it.copy(panelUrl = url) }
    }

    suspend fun unlock(pin: String): Outcome = withContext(Dispatchers.Default) {
        vaultLock.withLock {
            when (val r = vault.unlock(pin)) {
                is Vault.Unlock.Ok -> {
                    token = r.token
                    sessionKey = r.key
                    _state.update { it.copy(unlocked = true, failures = 0, wiped = false) }
                    refreshSignal.trySend(Unit)
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
                Vault.Unlock.NotProvisioned -> Outcome.Error("Nenhum token configurado")
                is Vault.Unlock.Broken -> Outcome.Error(r.reason)
            }
        }
    }

    suspend fun provision(pin: String, rawToken: String): Outcome {
        val tok = rawToken.trim()
        Vault.pinProblem(pin)?.let { return Outcome.Error(it) }
        ClaudeApi.tokenFormatProblem(tok)?.let { return Outcome.Error(it) }
        if (vault.isProvisioned) return Outcome.Error("Já existe um token. Faça login para trocar.")
        val check = withContext(Dispatchers.IO) { verify(tok) }
        if (check !is FetchResult.Ok) return Outcome.Error(describe(check))
        return withContext(Dispatchers.Default) {
            vaultLock.withLock {
                if (vault.isProvisioned) return@withLock Outcome.Error("Já existe um token.")
                val key = vault.provision(pin, tok)
                token = tok
                sessionKey = key
                applySnapshot(check.snap)
                _state.update { it.copy(provisioned = true, unlocked = true, failures = 0, wiped = false) }
                refreshSignal.trySend(Unit)
                Outcome.Ok
            }
        }
    }

    suspend fun rotateToken(rawToken: String): Outcome {
        val tok = rawToken.trim()
        ClaudeApi.tokenFormatProblem(tok)?.let { return Outcome.Error(it) }
        val key = sessionKey ?: return Outcome.Error("Desbloqueie com o PIN primeiro")
        val check = withContext(Dispatchers.IO) { verify(tok) }
        if (check !is FetchResult.Ok) return Outcome.Error(describe(check))
        return withContext(Dispatchers.Default) {
            vaultLock.withLock {
                vault.reseal(key, tok)
                token = tok
                usageDeniedUntil = 0
                _state.update { it.copy(usageNote = null) }
                applySnapshot(check.snap)
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
                        sessionKey = vault.provision(newPin, r.token)
                        token = r.token
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
                    Vault.Unlock.NotProvisioned -> Outcome.Error("Nenhum token configurado")
                    is Vault.Unlock.Broken -> Outcome.Error(r.reason)
                }
            }
        }
    }

    fun lock() {
        token = null
        sessionKey = null
        panel.invalidateSessions()
        _state.update { it.copy(unlocked = false) }
    }

    fun factoryReset() = factoryResetInternal()

    private fun factoryResetInternal() {
        token = null
        sessionKey = null
        vault.wipe()
        history.clear()
        settings.clear()
        panel.invalidateSessions()
        usageDeniedUntil = 0
        usageLimitedUntil = 0
        lastScoped = null
        if (!panel.isRunning) panel.start()
        _state.value = State(provisioned = false, wiped = true, news = _state.value.news, status = _state.value.status)
        refreshPanelUrl()
    }

    fun updateSettings(f: (Prefs) -> Prefs): Prefs {
        val old = settings.value
        val new = settings.update(f)
        if (old.source != new.source) {
            usageDeniedUntil = 0
            usageLimitedUntil = 0
        }
        if (old.refreshSec != new.refreshSec || old.source != new.source) refreshSignal.trySend(Unit)
        if (old.panelEnabled != new.panelEnabled) {
            if (new.panelEnabled) panel.start() else panel.stop()
            refreshPanelUrl()
        }
        return new
    }

    fun requestRefresh() {
        refreshSignal.trySend(Unit)
    }

    @Volatile private var demo = false

    fun enterDemo(p5: Double = 37.0, p7: Double = 64.0, pf: Double = 22.0): Boolean {
        if (vault.isProvisioned) return false
        demo = true
        val now = System.currentTimeMillis()
        val snap = UsageSnapshot(
            UsageWindow(p5, now + 2 * 3_600_000L + 13 * 60_000L),
            UsageWindow(p7, now + 3 * 86_400_000L + 5 * 3_600_000L),
            listOf(ScopedLimit("Fable", pf, null)),
            DataSource.USAGE, now - 12_000L,
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
        _state.update { it.copy(provisioned = true, unlocked = true, usage = snap, status = status, usageError = null) }
        return true
    }

    private fun verify(tok: String): FetchResult = when (settings.value.source) {
        DataSource.USAGE -> ClaudeApi.fetchUsage(tok)
        DataSource.PROBE -> ClaudeApi.fetchProbe(tok)
        DataSource.AUTO -> when (val r = ClaudeApi.fetchUsage(tok)) {
            is FetchResult.Ok -> r
            is FetchResult.Failed -> r
            else -> ClaudeApi.fetchProbe(tok)
        }
    }

    private fun fetch(tok: String): FetchResult {
        val now = System.currentTimeMillis()
        return when (settings.value.source) {
            DataSource.USAGE -> ClaudeApi.fetchUsage(tok)
            DataSource.PROBE -> ClaudeApi.fetchProbe(tok)
            DataSource.AUTO -> {
                if (now < usageDeniedUntil || now < usageLimitedUntil) {
                    probe(tok)
                } else when (val r = ClaudeApi.fetchUsage(tok)) {
                    is FetchResult.Ok -> r.also { _state.update { it.copy(usageNote = null) } }
                    is FetchResult.Failed -> r
                    is FetchResult.Auth -> {
                        usageDeniedUntil = now + 60 * 60_000L
                        Log.i(TAG, "endpoint de uso recusou (HTTP ${r.code}): ${r.message}")
                        _state.update { it.copy(usageNote = "uso: HTTP ${r.code}") }
                        probe(tok)
                    }
                    is FetchResult.Limited -> {
                        usageLimitedUntil = now + ((r.retryAfterSec ?: 300L) * 1000L).coerceIn(60_000L, 30 * 60_000L)
                        Log.i(TAG, "endpoint de uso limitado, retry-after=${r.retryAfterSec}")
                        _state.update { it.copy(usageNote = "uso: limitado") }
                        probe(tok)
                    }
                }
            }
        }
    }

    private fun probe(tok: String): FetchResult {
        val r = ClaudeApi.fetchProbe(tok)
        val h = ClaudeApi.lastRateHeaders
        if (h.keys != loggedRateHeaders?.keys) {
            Log.i(TAG, "headers da sondagem: $h")
            loggedRateHeaders = h
        }
        return r
    }

    private fun describe(r: FetchResult): String = when (r) {
        is FetchResult.Ok -> "ok"
        is FetchResult.Auth -> "Token recusado pela API (HTTP ${r.code}): ${r.message}"
        is FetchResult.Limited -> r.message
        is FetchResult.Failed -> r.message
    }

    private fun applySnapshot(fresh: UsageSnapshot) {
        val snap = withKnownScoped(fresh)
        history.record(snap)
        _state.update { it.copy(usage = snap, usageError = null, authError = false) }
    }

    private fun withKnownScoped(snap: UsageSnapshot): UsageSnapshot {
        if (snap.scoped.isNotEmpty()) {
            if (snap.source == DataSource.USAGE) lastScoped = snap.fetchedAt to snap.scoped
            return snap
        }
        val (at, scoped) = lastScoped ?: return snap
        if (snap.fetchedAt - at > SCOPED_KEEP_MS) return snap
        val alive = scoped.filter { it.resetsAt == null || it.resetsAt > snap.fetchedAt }
        return if (alive.isEmpty()) snap else snap.copy(scoped = alive)
    }

    suspend fun runLoops() = coroutineScope {
        launch { usageLoop() }
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

    private suspend fun usageLoop() {
        var backoffMs = 0L
        _state.value.lastAttemptAt?.let { last ->
            val wait = last + settings.value.refreshSec * 1000L - System.currentTimeMillis()
            if (wait > 0 && _state.value.usage != null) withTimeoutOrNull(wait) { refreshSignal.receive() }
        }
        while (currentCoroutineContext().isActive) {
            val tok = token
            if (tok == null) {
                refreshSignal.receive()
                continue
            }
            var waitMs = settings.value.refreshSec * 1000L
            val now = System.currentTimeMillis()
            when (val r = withContext(Dispatchers.IO) { fetch(tok) }) {
                is FetchResult.Ok -> {
                    backoffMs = 0
                    applySnapshot(r.snap)
                }
                is FetchResult.Auth -> _state.update {
                    it.copy(usageError = "Token recusado (HTTP ${r.code}). Troque o token no painel.", authError = true)
                }
                is FetchResult.Limited -> {
                    backoffMs = if (backoffMs == 0L) waitMs * 2 else (backoffMs * 2)
                    backoffMs = backoffMs.coerceAtMost(15 * 60_000L)
                    waitMs = maxOf(waitMs, backoffMs, (r.retryAfterSec ?: 0L) * 1000L)
                    _state.update { it.copy(usageError = "${r.message}. Nova tentativa em ${waitMs / 60_000L + 1} min") }
                }
                is FetchResult.Failed -> {
                    waitMs = minOf(waitMs, 30_000L)
                    _state.update { it.copy(usageError = r.message) }
                }
            }
            _state.update { it.copy(lastAttemptAt = now, nextRefreshAt = now + waitMs) }
            withTimeoutOrNull(waitMs) { refreshSignal.receive() }
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

    fun stateJson(): JSONObject {
        val s = _state.value
        val news = JSONArray()
        s.news.take(8).forEach {
            news.put(JSONObject().put("title", it.title).put("link", it.link).put("date", it.date ?: JSONObject.NULL).put("category", it.category ?: JSONObject.NULL))
        }
        return infoJson()
            .put("now", System.currentTimeMillis())
            .put("usage", s.usage?.toJson() ?: JSONObject.NULL)
            .put("error", s.usageError ?: JSONObject.NULL)
            .put("usageNote", s.usageNote ?: JSONObject.NULL)
            .put("authError", s.authError)
            .put("lastAttemptAt", s.lastAttemptAt ?: JSONObject.NULL)
            .put("nextRefreshAt", s.nextRefreshAt ?: JSONObject.NULL)
            .put("status", s.status?.toJson() ?: JSONObject.NULL)
            .put("history", history.toJson())
            .put("news", news)
            .put("settings", settings.value.toJson())
            .put("options", Prefs.optionsJson())
            .put("look", lookJson(settings.value))
            .put("mascots", mascotsJson(s.usage, s.status))
            .put("panelUrl", s.panelUrl ?: JSONObject.NULL)
    }
}
