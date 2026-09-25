package dev.clawdboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun trackTime() {
        assertEquals("0:00", fmtTrack(0L))
        assertEquals("1:23", fmtTrack(83_999L))
        assertEquals("1:02:03", fmtTrack(3_723_000L))
        assertEquals("0:00", fmtTrack(-5_000L))
    }
}
