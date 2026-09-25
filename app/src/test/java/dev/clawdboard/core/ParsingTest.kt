package dev.clawdboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException

class ParsingTest {
    private val usageJson = """
        {"five_hour":{"utilization":6.0,"resets_at":"2026-09-25T01:30:00.385171+00:00","limit_dollars":null},
         "seven_day":{"utilization":41.0,"resets_at":"2026-09-29T22:00:00.385191+00:00"},
         "seven_day_opus":null,"seven_day_sonnet":null,
         "limits":[
           {"kind":"session","percent":6,"resets_at":"2026-09-25T01:30:00.385171+00:00","scope":null},
           {"kind":"weekly_all","percent":41,"resets_at":"2026-09-29T22:00:00.385191+00:00","scope":null},
           {"kind":"weekly_scoped","percent":22,"resets_at":"2026-09-29T22:00:00.385340+00:00","scope":{"model":{"id":null,"display_name":"Fable"},"surface":null}}
         ]}
    """.trimIndent()

    @Test
    fun usageEndpointIsPercentAndIso() {
        val s = ClaudeApi.parseUsageJson(usageJson, 1L)
        assertEquals(6.0, s.fiveHour!!.percent, 0.001)
        assertEquals(41.0, s.sevenDay!!.percent, 0.001)
        assertEquals(1790299800385L, s.fiveHour!!.resetsAt)
        assertEquals(1, s.scoped.size)
        assertEquals("Fable", s.scoped[0].label)
        assertEquals(22.0, s.scoped[0].percent, 0.001)
        assertEquals(DataSource.USAGE, s.source)
    }

    @Test
    fun probeHeadersAreFractionAndEpoch() {
        val h = mapOf(
            "anthropic-ratelimit-unified-5h-utilization" to "0.05",
            "anthropic-ratelimit-unified-5h-reset" to "1790299800",
            "anthropic-ratelimit-unified-7d-utilization" to "0.41",
            "anthropic-ratelimit-unified-7d-reset" to "1790719200",
        )
        val s = ClaudeApi.parseProbeHeaders(h, 1L)!!
        assertEquals(5.0, s.fiveHour!!.percent, 0.001)
        assertEquals(41.0, s.sevenDay!!.percent, 0.001)
        assertEquals(1790299800000L, s.fiveHour!!.resetsAt)
        assertEquals(DataSource.PROBE, s.source)
        assertTrue(s.scoped.isEmpty())
        assertNull(ClaudeApi.parseProbeHeaders(emptyMap(), 1L))
    }

    @Test
    fun probeHeadersPickUpPerModelWeekly() {
        val h = mapOf(
            "anthropic-ratelimit-unified-5h-utilization" to "0.05",
            "anthropic-ratelimit-unified-7d-utilization" to "0.41",
            "anthropic-ratelimit-unified-7d_fable-utilization" to "0.22",
            "anthropic-ratelimit-unified-7d_fable-reset" to "1790719200",
            "anthropic-ratelimit-unified-status" to "allowed",
        )
        val s = ClaudeApi.parseProbeHeaders(h, 1L)!!
        assertEquals(1, s.scoped.size)
        assertEquals("Fable", s.scoped[0].label)
        assertEquals(22.0, s.scoped[0].percent, 0.001)
        assertEquals(1790719200000L, s.scoped[0].resetsAt)
    }

    @Test
    fun prefsZoomIsClamped() {
        assertEquals(150, Prefs().merge(org.json.JSONObject("""{"zoom":400}""")).zoom)
        assertEquals(90, Prefs().merge(org.json.JSONObject("""{"zoom":10}""")).zoom)
        assertEquals(115, Prefs().merge(org.json.JSONObject("""{"zoom":115}""")).zoom)
    }

    @Test
    fun tokenFormatChecks() {
        assertNull(ClaudeApi.tokenFormatProblem("sk-ant-oat01-" + "x".repeat(90)))
        assertNotNull(ClaudeApi.tokenFormatProblem("sk-ant-api03-" + "x".repeat(90)))
        assertNotNull(ClaudeApi.tokenFormatProblem("abc"))
        assertNotNull(ClaudeApi.tokenFormatProblem("sk-ant-oat01-xx yy" + "x".repeat(40)))
    }

    @Test
    fun statusMarksNamedModelsDown() {
        val body = """{"incidents":[{"name":"Elevated errors on Claude Opus 4.6","status":"investigating","impact":"minor","shortlink":"https://stspg.io/x","incident_updates":[{"body":"We are investigating."}]}]}"""
        val s = StatusApi.parse(body, 1L)
        assertEquals(setOf("Opus"), s.down)
        assertEquals("Elevated errors on Claude Opus 4.6", s.incidents[0].name)
        assertTrue(StatusApi.parse("""{"incidents":[]}""", 1L).down.isEmpty())
    }

    @Test
    fun pinKeyOpensOnlyWithRightPin() {
        val salt = Crypto.random(16)
        val sealed = Crypto.seal(Crypto.derive("1234", salt, 1000), "sk-ant-oat01-segredo".toByteArray())
        assertEquals("sk-ant-oat01-segredo", String(Crypto.open(Crypto.derive("1234", salt, 1000), sealed)))
        try {
            Crypto.open(Crypto.derive("1235", salt, 1000), sealed)
            fail("PIN errado não pode abrir o token")
        } catch (_: GeneralSecurityException) {
        }
    }

    @Test
    fun prefsMergeIgnoresUnknownAndClamps() {
        val p = Prefs().merge(org.json.JSONObject("""{"refreshSec":5,"source":"PROBE","mode":"XYZ","dwellSec":999}"""))
        assertEquals(30, p.refreshSec)
        assertEquals(DataSource.PROBE, p.source)
        assertEquals(ScreenMode.CAROUSEL, p.mode)
        assertEquals(120, p.dwellSec)
        assertEquals(600, Prefs().merge(org.json.JSONObject("""{"refreshSec":600}""")).refreshSec)
        assertEquals(600, Prefs().merge(org.json.JSONObject("""{"refreshSec":9999}""")).refreshSec)
    }
}
