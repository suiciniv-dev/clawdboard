package dev.clawdboard.ui

import dev.clawdboard.core.txt
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.roundToInt

fun zoned(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

fun weekShort(d: DayOfWeek) = txt.weekShort(d)

fun fmtPct(p: Double?): String = p?.let { "${it.roundToInt()}%" } ?: "--"

fun fmtLeft(ms: Long): String {
    if (ms <= 0) return txt.now
    val s = ms / 1000
    val d = s / 86_400
    val h = (s % 86_400) / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h %02dm".format(m)
        else -> "${m}m %02ds".format(sec)
    }
}

fun fmtAt(epoch: Long, now: Long): String {
    val t = zoned(epoch)
    val n = zoned(now)
    val hm = "%02d:%02d".format(t.hour, t.minute)
    return when (t.toLocalDate()) {
        n.toLocalDate() -> txt.todayAt(hm)
        n.toLocalDate().plusDays(1) -> txt.tomorrowAt(hm)
        else -> txt.dayAt(t.dayOfWeek, t.dayOfMonth, t.monthValue, hm)
    }
}

fun fmtAgo(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 5 -> txt.now
        s < 60 -> txt.agoSec(s)
        s < 3600 -> txt.agoMin(s / 60)
        else -> txt.agoHour(s / 3600)
    }
}

fun fmtTrack(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun fmtDateLong(t: ZonedDateTime): String = txt.dateLong(t.dayOfWeek, t.dayOfMonth, t.monthValue)

fun fmtShortDate(ms: Long?): String {
    if (ms == null) return ""
    val t = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC)
    return txt.dateShort(t.dayOfMonth, t.monthValue)
}
