package dev.clawdboard.core

import android.content.Context

class Nudge(context: Context) {
    private val sp = context.getSharedPreferences("nudge", Context.MODE_PRIVATE)

    fun due(now: Long): Boolean {
        if (sp.getBoolean("never", false)) return false
        var first = sp.getLong("firstSeen", 0L)
        if (first == 0L) {
            first = now
            sp.edit().putLong("firstSeen", now).apply()
        }
        return now >= sp.getLong("nextAt", first + FIRST_MS)
    }

    fun later(now: Long) = sp.edit().putLong("nextAt", now + EVERY_MS).apply()

    fun wrote(now: Long) = sp.edit().putLong("nextAt", now + AFTER_MAIL_MS).apply()

    fun never() = sp.edit().putBoolean("never", true).apply()

    fun force() = sp.edit().putLong("nextAt", 0L).putBoolean("never", false).apply()

    companion object {
        const val EMAIL = "vinips00@gmail.com"
        private const val DAY = 86_400_000L
        const val FIRST_MS = 3 * DAY
        const val EVERY_MS = 10 * DAY
        const val AFTER_MAIL_MS = 60 * DAY
    }
}
