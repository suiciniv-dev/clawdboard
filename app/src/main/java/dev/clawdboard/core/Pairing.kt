package dev.clawdboard.core

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

class Pairing(private val app: Context) {
    private val sp = app.getSharedPreferences("pairing", Context.MODE_PRIVATE)

    val lastPushAt: Long? get() = sp.getLong("lastPushAt", 0L).takeIf { it > 0 }

    fun remember(key: String) {
        sp.edit().putString("hash", hash(key)).commit()
    }

    fun matches(key: String?): Boolean {
        val stored = sp.getString("hash", null) ?: return false
        if (key.isNullOrBlank()) return false
        return MessageDigest.isEqual(stored.toByteArray(), hash(key.trim()).toByteArray())
    }

    fun save(body: JSONObject, at: Long) {
        sp.edit().putString("last", body.toString()).putLong("lastPushAt", at).apply()
    }

    fun restore(now: Long): UsageSnapshot? {
        val raw = sp.getString("last", null) ?: return null
        val at = lastPushAt ?: return null
        return runCatching { parsePush(JSONObject(raw), at)?.settled(now) }.getOrNull()
    }

    fun clear() {
        sp.edit().clear().commit()
    }

    fun installer(url: String, key: String): String {
        val usage = asset("pc/usage.ps1").replace("__URL__", url).replace("__KEY__", key)
        return asset("pc/install.ps1").replace("__USAGE__", usage.trimEnd())
            .replace("__T_CONNECTED__", txt.installConnected).replace("__T_BACKUP__", txt.installBackup(""))
            .replace("__T_EVERY__", txt.installEvery(url)).replace("__URL__", url)
    }

    private fun asset(name: String) = app.assets.open(name).use { it.readBytes().toString(Charsets.UTF_8) }

    companion object {
        fun newKey(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

        fun hash(key: String): String =
            MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        fun command(url: String, key: String) = "irm $url/pc/install.ps1?k=$key | iex"
    }
}

fun parsePush(o: JSONObject, now: Long): UsageSnapshot? {
    fun window(name: String): UsageWindow? {
        val w = o.optJSONObject(name) ?: return null
        val pct = w.num("used_percentage") ?: return null
        val reset = w.optLong("resets_at", 0L).takeIf { it > 0 }?.times(1000)
        return UsageWindow(pct.coerceIn(0.0, 100.0), reset)
    }
    val five = window("five_hour")
    val seven = window("seven_day")
    if (five == null && seven == null) return null
    val scoped = o.optJSONArray("scoped")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            val label = s.str("label") ?: return@mapNotNull null
            val pct = s.num("used_percentage") ?: return@mapNotNull null
            ScopedLimit(label, pct.coerceIn(0.0, 100.0), s.optLong("resets_at", 0L).takeIf { it > 0 }?.times(1000))
        }
    }.orEmpty()
    return UsageSnapshot(five, seven, scoped, now)
}

fun UsageSnapshot.settled(now: Long): UsageSnapshot {
    fun settle(w: UsageWindow?) = if (w?.resetsAt != null && w.resetsAt <= now) UsageWindow(0.0, null) else w
    val five = settle(fiveHour)
    val seven = settle(sevenDay)
    val alive = scoped.filter { it.resetsAt == null || it.resetsAt > now }
    return if (five == fiveHour && seven == sevenDay && alive.size == scoped.size) this else copy(fiveHour = five, sevenDay = seven, scoped = alive)
}
