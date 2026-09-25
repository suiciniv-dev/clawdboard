package dev.clawdboard.ui

import dev.clawdboard.core.I18n
import dev.clawdboard.core.Language
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FormatTest {
    @After
    fun reset() {
        I18n.language = Language.AUTO
    }

    @Test
    fun trackTime() {
        assertEquals("0:00", fmtTrack(0L))
        assertEquals("1:23", fmtTrack(83_999L))
        assertEquals("1:02:03", fmtTrack(3_723_000L))
        assertEquals("0:00", fmtTrack(-5_000L))
    }

    @Test
    fun agoInBothLanguages() {
        I18n.language = Language.PT
        assertEquals("há 12s", fmtAgo(12_000L))
        assertEquals("agora", fmtAgo(1_000L))
        I18n.language = Language.EN
        assertEquals("12s ago", fmtAgo(12_000L))
        assertEquals("3 min ago", fmtAgo(185_000L))
    }

    @Test
    fun resetTimeInBothLanguages() {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.of(2026, 9, 25, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val today = ZonedDateTime.of(2026, 9, 25, 19, 44, 0, 0, zone).toInstant().toEpochMilli()
        val monday = ZonedDateTime.of(2026, 9, 28, 12, 54, 0, 0, zone).toInstant().toEpochMilli()
        I18n.language = Language.PT
        assertEquals("hoje às 19:44", fmtAt(today, now))
        assertEquals("seg 28/09 às 12:54", fmtAt(monday, now))
        I18n.language = Language.EN
        assertEquals("today at 19:44", fmtAt(today, now))
        assertEquals("Mon, Sep 28 at 12:54", fmtAt(monday, now))
        assertEquals("Friday, September 25", fmtDateLong(ZonedDateTime.of(2026, 9, 25, 10, 0, 0, 0, zone)))
    }
}
