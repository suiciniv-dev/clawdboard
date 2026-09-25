package dev.clawdboard.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTest {
    private val push = """{"five_hour":{"used_percentage":31,"resets_at":1790367000},"seven_day":{"used_percentage":53,"resets_at":1790719200}}"""

    @Test
    fun pushIsPercentAndEpochSeconds() {
        val s = parsePush(JSONObject(push), 5L)!!
        assertEquals(31.0, s.fiveHour!!.percent, 0.001)
        assertEquals(1790367000000L, s.fiveHour!!.resetsAt)
        assertEquals(53.0, s.sevenDay!!.percent, 0.001)
        assertEquals(5L, s.fetchedAt)
        assertTrue(s.scoped.isEmpty())
    }

    @Test
    fun pushNeedsAtLeastOneWindow() {
        assertNull(parsePush(JSONObject("{}"), 1L))
        assertNull(parsePush(JSONObject("""{"five_hour":{"resets_at":1}}"""), 1L))
        val only = parsePush(JSONObject("""{"seven_day":{"used_percentage":140}}"""), 1L)!!
        assertNull(only.fiveHour)
        assertEquals(100.0, only.sevenDay!!.percent, 0.001)
        assertNull(only.sevenDay!!.resetsAt)
    }

    @Test
    fun expiredWindowsDropToZero() {
        val s = parsePush(JSONObject(push), 1L)!!
        assertSame(s, s.settled(1790366999000L))
        val later = s.settled(1790367000000L)
        assertEquals(0.0, later.fiveHour!!.percent, 0.001)
        assertNull(later.fiveHour!!.resetsAt)
        assertEquals(53.0, later.sevenDay!!.percent, 0.001)
    }

    @Test
    fun perModelLimitsComeAlongAndExpire() {
        val body = """{"five_hour":{"used_percentage":39,"resets_at":1790370540},"seven_day":{"used_percentage":55,"resets_at":1790719140},"scoped":[{"used_percentage":25,"resets_at":1790719140,"label":"Fable"},{"label":"Sem numero"}]}"""
        val s = parsePush(JSONObject(body), 1L)!!
        assertEquals(1, s.scoped.size)
        assertEquals("Fable", s.scoped[0].label)
        assertEquals(25.0, s.scoped[0].percent, 0.001)
        assertEquals(1790719140000L, s.scoped[0].resetsAt)
        assertTrue(s.settled(1790719140000L).scoped.isEmpty())
        assertEquals(1, s.settled(1790719139000L).scoped.size)
    }

    @Test
    fun keysAreRandomHexAndHashStable() {
        val a = Pairing.newKey()
        assertEquals(32, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
        assertNotEquals(a, Pairing.newKey())
        assertEquals(Pairing.hash(a), Pairing.hash(a))
        assertEquals("irm http://10.0.0.5:8080/pc/install.ps1?k=$a | iex", Pairing.command("http://10.0.0.5:8080", a))
    }
}
