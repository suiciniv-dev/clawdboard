package dev.clawdboard.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpriteTest {
    @Test
    fun spriteFillsTheWholeGrid() {
        assertEquals(LOOK_ROWS, SPRITE.size)
        SPRITE.forEach { assertEquals(it, 16, it.length) }
        assertTrue(SPRITE.joinToString("").all { it in ".BbLMN" })
        assertTrue(SPRITE.take(SPRITE_TOP).all { row -> row.all { it == '.' } })
    }

    @Test
    fun eyesSitOnTheMaskEvenWhenLookingAside() {
        EYE_COLS.forEach { e ->
            for (shift in -1..1) for (dx in 0..1) for (dy in 0..1) {
                assertEquals("olho $e desvio $shift", 'M', SPRITE[EYE_ROW + dy][e + shift + dx])
            }
        }
    }

    @Test
    fun panelDrawsTheSameSprite() {
        val html = File("src/main/assets/panel.html").readText()
        val rows = Regex("const SPRITE = \\[(.*?)];", RegexOption.DOT_MATCHES_ALL).find(html)!!.groupValues[1]
        assertEquals(SPRITE.toList(), Regex("\"([^\"]*)\"").findAll(rows).map { it.groupValues[1] }.toList())
    }

    @Test
    fun clawdOnlyShowsWhenUnlocked() {
        assertEquals(Species.RACCOON, Prefs(species = Species.CLAWD).mascot())
        assertEquals(Species.RACCOON, Prefs().merge(JSONObject().put("species", "CLAWD")).mascot())
        assertEquals("RACCOON", lookJson(Prefs(species = Species.CLAWD)).getString("species"))
        assertEquals(Species.CLAWD, Prefs(species = Species.CLAWD, clawdUnlocked = true).mascot())
    }
}
