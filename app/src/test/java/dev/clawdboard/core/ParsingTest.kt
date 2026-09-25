package dev.clawdboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException

class ParsingTest {
    @Test
    fun prefsZoomIsClamped() {
        assertEquals(150, Prefs().merge(org.json.JSONObject("""{"zoom":400}""")).zoom)
        assertEquals(90, Prefs().merge(org.json.JSONObject("""{"zoom":10}""")).zoom)
        assertEquals(115, Prefs().merge(org.json.JSONObject("""{"zoom":115}""")).zoom)
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
        assertEquals(ScreenMode.CAROUSEL, p.mode)
        assertEquals(120, p.dwellSec)
        assertEquals(5, Prefs().merge(org.json.JSONObject("""{"dwellSec":1}""")).dwellSec)
    }
}
