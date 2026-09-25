package dev.clawdboard.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FeelTest {
    private fun usage(p5: Double, p7: Double, fable: Double) =
        UsageSnapshot(UsageWindow(p5, null), UsageWindow(p7, null), listOf(ScopedLimit("Fable", fable, null)), DataSource.USAGE, 1L)

    @Test
    fun generalLimitAtFullExhaustsEveryone() {
        val u = usage(p5 = 40.0, p7 = 100.0, fable = 22.0)
        listOf("Haiku", "Sonnet", "Opus", "Fable").forEach { assertEquals(it, Mood.EXHAUSTED, feelOf(u, it).mood) }
    }

    @Test
    fun fableOwnLimitAtFullExhaustsOnlyFable() {
        val u = usage(p5 = 40.0, p7 = 60.0, fable = 100.0)
        assertEquals(Mood.EXHAUSTED, feelOf(u, "Fable").mood)
        assertEquals(Mood.NORMAL, feelOf(u, "Opus").mood)
    }

    @Test
    fun heatRampsFrom90To100() {
        assertEquals(0f, feelOf(usage(40.0, 89.0, 0.0), "Opus").heat, 0.001f)
        assertEquals(0.5f, feelOf(usage(40.0, 95.0, 0.0), "Opus").heat, 0.001f)
        assertEquals(Mood.SWEATY, feelOf(usage(40.0, 95.0, 0.0), "Opus").mood)
    }

    @Test
    fun emptySessionSleeps() {
        assertEquals(Mood.SLEEPY, feelOf(usage(0.0, 30.0, 10.0), "Haiku").mood)
        assertEquals(Mood.NORMAL, feelOf(null, "Haiku").mood)
    }
}
