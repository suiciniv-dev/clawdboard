package dev.clawdboard.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicTest {
    @Test
    fun playingAdvancesFromLastUpdate() {
        assertEquals(13_000L, livePosition(10_000L, 1_000L, 1f, true, 200_000L, 4_000L))
        assertEquals(16_000L, livePosition(10_000L, 1_000L, 2f, true, 200_000L, 4_000L))
    }

    @Test
    fun pausedStaysPut() {
        assertEquals(10_000L, livePosition(10_000L, 1_000L, 1f, false, 200_000L, 90_000L))
    }

    @Test
    fun neverPassesTheEndOrGoesNegative() {
        assertEquals(200_000L, livePosition(199_000L, 1_000L, 1f, true, 200_000L, 60_000L))
        assertEquals(0L, livePosition(-1L, 1_000L, 1f, true, 200_000L, 60_000L))
        assertEquals(70_000L, livePosition(10_000L, 1_000L, 1f, true, 0L, 61_000L))
    }
}
