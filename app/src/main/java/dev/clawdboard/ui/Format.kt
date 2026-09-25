package dev.clawdboard.ui

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.roundToInt

private val WEEK_SHORT = mapOf(
    DayOfWeek.MONDAY to "seg", DayOfWeek.TUESDAY to "ter", DayOfWeek.WEDNESDAY to "qua",
    DayOfWeek.THURSDAY to "qui", DayOfWeek.FRIDAY to "sex", DayOfWeek.SATURDAY to "sáb", DayOfWeek.SUNDAY to "dom",
)
private val WEEK_LONG = mapOf(
    DayOfWeek.MONDAY to "segunda-feira", DayOfWeek.TUESDAY to "terça-feira", DayOfWeek.WEDNESDAY to "quarta-feira",
    DayOfWeek.THURSDAY to "quinta-feira", DayOfWeek.FRIDAY to "sexta-feira", DayOfWeek.SATURDAY to "sábado", DayOfWeek.SUNDAY to "domingo",
)
private val MONTHS = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")

fun zoned(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

fun weekShort(d: DayOfWeek) = WEEK_SHORT[d] ?: ""

fun fmtPct(p: Double?): String = p?.let { "${it.roundToInt()}%" } ?: "--"

fun fmtLeft(ms: Long): String {
    if (ms <= 0) return "agora"
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
        n.toLocalDate() -> "hoje às $hm"
        n.toLocalDate().plusDays(1) -> "amanhã às $hm"
        else -> "${weekShort(t.dayOfWeek)} ${t.dayOfMonth}/%02d às $hm".format(t.monthValue)
    }
}

fun fmtAgo(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 5 -> "agora"
        s < 60 -> "há ${s}s"
        s < 3600 -> "há ${s / 60} min"
        else -> "há ${s / 3600}h"
    }
}

fun fmtDateLong(t: ZonedDateTime): String = "${WEEK_LONG[t.dayOfWeek]}, ${t.dayOfMonth} de ${MONTHS[t.monthValue - 1]}"

fun fmtShortDate(ms: Long?): String {
    if (ms == null) return ""
    val t = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC)
    return "${t.dayOfMonth} ${MONTHS[t.monthValue - 1].take(3)}"
}
