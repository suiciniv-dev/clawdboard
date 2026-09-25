package dev.clawdboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import dev.clawdboard.core.ARM_LIFT
import dev.clawdboard.core.ARM_ROWS
import dev.clawdboard.core.Accessory
import dev.clawdboard.core.EAR_ROWS
import dev.clawdboard.core.EYE_COLS
import dev.clawdboard.core.EYE_ROW
import dev.clawdboard.core.FEET_ROW
import dev.clawdboard.core.FUR
import dev.clawdboard.core.Feel
import dev.clawdboard.core.LEFT_EAR
import dev.clawdboard.core.LOOK_ROWS
import dev.clawdboard.core.LOOK_TOP
import dev.clawdboard.core.MASK
import dev.clawdboard.core.Mood
import dev.clawdboard.core.NOSE
import dev.clawdboard.core.RIGHT_EAR
import dev.clawdboard.core.SPRITE
import dev.clawdboard.core.SPRITE_TOP
import dev.clawdboard.core.Skin
import dev.clawdboard.core.Species
import dev.clawdboard.core.Tint
import dev.clawdboard.core.accessoryFor
import dev.clawdboard.core.accessoryPixels
import dev.clawdboard.core.bodyArgb
import kotlinx.coroutines.delay
import kotlin.random.Random

private val FUR_COLOR = Color(FUR)
private val MASK_COLOR = Color(MASK)
private val NOSE_COLOR = Color(NOSE)
private val SHINE = Color(0xFFF3EFEA)
private val SWEAT = Color(0xFF8FD3F4)
private val HOT = Color(0xFFFF3B2F)
private val HOT_FUR = Color(0xFFFFB0A3)
private val CHARRED = Color(0xFF4E3530)
private val CHARRED_FUR = Color(0xFF6E5A52)
private val CHARRED_MASK = Color(0xFF2A1F1C)
private val DEAD_FUR = Color(0xFF7A7064)
private val DEAD_MASK = Color(0xFF3A342E)
private val X_EYE = Color(0xFFD8CFC2)
private val FLASH = Color(0xFFFFE9A8)
private val SPARK = Color(0xFFF5B83C)
private val SMOKE = Color(0xFF8A8078)
private val NOTE = Color(0xFFB9A6F2)
private const val BEAT = 500L

private val CLAWD_BODY = arrayOf(
    "..############..",
    "..##E######E##..",
    "################",
    "..############..",
    "...#.#....#.#...",
)
private val CLAWD_EYES = intArrayOf(4, 11)
private val CLAWD_LEGS = intArrayOf(3, 5, 10, 12)

private val SPARKS_NEAR = listOf(1 to 3, 14 to 3, 0 to 7, 15 to 7, 2 to 13, 13 to 13, 7 to 1, 9 to 2)
private val SPARKS_FAR = listOf(0 to 0, 15 to 0, 0 to 12, 15 to 12, 4 to 0, 11 to 1, 6 to 13, 10 to 13)

const val MASCOT_ASPECT = 16f / LOOK_ROWS

data class Look(
    val skin: Skin = Skin.MODELS,
    val tint: Tint = Tint.NATURAL,
    val animations: Boolean = true,
    val species: Species = Species.RACCOON,
)

val LocalLook = compositionLocalOf { Look() }

val LocalDance = compositionLocalOf { false }

private data class Pose(
    val dy: Int = 0,
    val look: Int = 0,
    val legs: Int = 0,
    val arm: Int = 0,
    val drop: Int = -1,
    val z: Boolean = false,
    val throb: Boolean = false,
    val shake: Int = 0,
    val boom: Int = 0,
    val smoke: Int = -1,
    val foot: Int = -1,
    val note: Int = 0,
    val noteDy: Int = 0,
)

@Composable
fun Mascot(
    modifier: Modifier = Modifier,
    model: String? = null,
    alive: Boolean = true,
    seed: Int = 0,
    feel: Feel = Feel(),
    reserveTop: Boolean = true,
) {
    val look = LocalLook.current
    val acc = accessoryFor(look.skin, model)
    val animate = look.animations && alive
    val dancing = LocalDance.current && animate
    val mood = when {
        !alive -> Mood.NORMAL
        dancing && feel.mood == Mood.SLEEPY -> Mood.NORMAL
        else -> feel.mood
    }
    val out = mood == Mood.EXHAUSTED
    val sleepy = mood == Mood.SLEEPY
    val trembling = feel.heat >= 0.5f && !dancing
    var blink by remember { mutableStateOf(false) }
    var pose by remember { mutableStateOf(Pose()) }
    var wasOut by remember { mutableStateOf(out) }

    LaunchedEffect(mood, animate, dancing) { pose = Pose() }

    if (alive && !sleepy && !out) {
        LaunchedEffect(seed) {
            blink = false
            val rnd = Random(seed * 7919 + (System.nanoTime() % 100_000).toInt())
            delay(rnd.nextLong(400, 3000))
            while (true) {
                blink = true
                delay(140)
                blink = false
                if (rnd.nextInt(4) == 0) {
                    delay(120); blink = true; delay(120); blink = false
                }
                delay(2400L + rnd.nextLong(4200))
            }
        }
    }

    if (animate && !sleepy && !out && !dancing) {
        LaunchedEffect(seed, mood, acc) {
            val rnd = Random(seed * 131 + (System.nanoTime() % 100_000).toInt())
            delay(rnd.nextLong(1_500, 6_000))
            while (true) {
                when (rnd.nextInt(4)) {
                    0 -> if (acc != Accessory.GLASSES) {
                        pose = pose.copy(look = if (rnd.nextBoolean()) 1 else -1)
                        delay(1_100)
                    }
                    1 -> repeat(3) {
                        pose = pose.copy(legs = 1); delay(160)
                        pose = pose.copy(legs = 2); delay(160)
                    }
                    2 -> {
                        val side = if (rnd.nextBoolean()) 1 else -1
                        repeat(3) {
                            pose = pose.copy(arm = side); delay(200)
                            pose = pose.copy(arm = 0); delay(160)
                        }
                    }
                    else -> repeat(2) {
                        pose = pose.copy(dy = 1); delay(140)
                        pose = pose.copy(dy = 0); delay(160)
                    }
                }
                pose = pose.copy(dy = 0, look = 0, legs = 0, arm = 0)
                delay(if (mood == Mood.SWEATY) 1_200L + rnd.nextLong(2_000) else 4_000L + rnd.nextLong(7_000))
            }
        }
    }

    if (dancing && !out) {
        LaunchedEffect(seed, acc) {
            val side = if (seed % 2 == 0) 1 else -1
            try {
                while (true) {
                    delay(BEAT - System.currentTimeMillis() % BEAT)
                    val beat = System.currentTimeMillis() / BEAT
                    val arm = if (beat % 2 == 0L) side else -side
                    val note = if ((beat / 2 + seed) % 2 == 0L) 1 else -1
                    pose = pose.copy(dy = 1, legs = 0, arm = arm, look = if (acc == Accessory.GLASSES) 0 else arm, note = note, noteDy = 1)
                    delay(BEAT / 2)
                    pose = pose.copy(dy = 0, legs = if (beat % 2 == 0L) 1 else 2, noteDy = 0)
                }
            } finally {
                pose = pose.copy(dy = 0, legs = 0, arm = 0, look = 0, note = 0, noteDy = 0)
            }
        }
    }

    if (dancing && out) {
        LaunchedEffect(seed) {
            val foot = if (seed % 2 == 0) 2 else 1
            try {
                while (true) {
                    delay(BEAT - System.currentTimeMillis() % BEAT)
                    pose = pose.copy(foot = foot)
                    delay(BEAT / 2)
                    pose = pose.copy(foot = -1)
                }
            } finally {
                pose = pose.copy(foot = -1)
            }
        }
    }

    if (animate && mood == Mood.SWEATY) {
        LaunchedEffect(seed) {
            delay(Random(seed).nextLong(0, 1_500))
            while (true) {
                for (y in 3..6) {
                    pose = pose.copy(drop = y); delay(180)
                }
                pose = pose.copy(drop = -1); delay(1_400)
            }
        }
    }

    if (animate && feel.heat > 0f && !out) {
        LaunchedEffect(seed, trembling) {
            var tick = 0
            try {
                while (true) {
                pose = pose.copy(throb = !pose.throb)
                if (trembling && tick % 3 == 0) {
                    repeat(4) { k ->
                        pose = pose.copy(shake = if (k % 2 == 0) 1 else -1); delay(70)
                    }
                    pose = pose.copy(shake = 0)
                    delay(100)
                } else {
                    delay(380)
                }
                tick++
                }
            } finally {
                pose = pose.copy(shake = 0, throb = false)
            }
        }
    }

    LaunchedEffect(out, animate) {
        if (out && !wasOut && animate) {
            pose = pose.copy(boom = 1); delay(120)
            pose = pose.copy(boom = 2); delay(160)
            pose = pose.copy(boom = 3); delay(220)
            pose = pose.copy(boom = 0)
        }
        wasOut = out
        if (out && animate) {
            delay(Random(seed).nextLong(0, 1_000))
            while (true) {
                for (y in 3 downTo 0) {
                    pose = pose.copy(smoke = y); delay(260)
                }
                pose = pose.copy(smoke = -1); delay(1_600)
            }
        }
    }

    if (sleepy) {
        LaunchedEffect(seed, animate) {
            delay(Random(seed).nextLong(0, 1_200))
            pose = Pose(z = true)
            while (animate) {
                pose = pose.copy(z = true, dy = 0); delay(1_300)
                pose = pose.copy(dy = 1); delay(700)
                pose = pose.copy(z = false, dy = 0); delay(900)
            }
        }
    }

    val clawd = look.species == Species.CLAWD
    val top = if (clawd) LOOK_TOP else SPRITE_TOP
    val base = Color(bodyArgb(look.tint, model, look.species))
    Canvas(modifier.aspectRatio(if (reserveTop) MASCOT_ASPECT else 16f / (LOOK_ROWS - top))) {
        val u = size.width / 16f
        val oy = if (reserveTop) 0f else -top * u
        val p = pose
        val ox = p.shake * 0.5f * u
        val dy = p.dy
        val flash = p.boom == 1
        val warm = feel.heat * (if (p.throb) 1f else 0.72f)
        val body = when {
            !alive -> C.deadBody
            flash -> FLASH
            out -> CHARRED
            feel.heat > 0f -> lerp(base, HOT, warm)
            else -> base
        }
        val shade = if (flash) FLASH else lerp(body, Color.Black, 0.24f)
        val fur = when {
            !alive -> DEAD_FUR
            flash -> FLASH
            out -> CHARRED_FUR
            feel.heat > 0f -> lerp(FUR_COLOR, HOT_FUR, warm)
            else -> FUR_COLOR
        }
        val mask = when {
            !alive -> DEAD_MASK
            flash -> FLASH
            out -> CHARRED_MASK
            else -> MASK_COLOR
        }
        fun paint(ch: Char) = when (ch) {
            'B' -> body
            'b' -> shade
            'L' -> fur
            'M' -> mask
            else -> if (flash) FLASH else NOSE_COLOR
        }
        fun rect(x: Float, y: Float, w: Float, h: Float, c: Color) =
            drawRect(c, Offset(ox + x * u, oy + y * u), Size(w * u + 0.6f, h * u + 0.6f))
        fun rect(x: Int, y: Int, w: Int, h: Int, c: Color) = rect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), c)

        if (clawd) CLAWD_BODY.forEachIndexed { r, row ->
            val y = LOOK_TOP + r * 2
            val footLeg = when (p.foot) { 1 -> 0; 2 -> 3; else -> -1 }
            row.forEachIndexed { c, ch ->
                if (ch == '.') return@forEachIndexed
                when {
                    r == 4 -> {
                        val leg = CLAWD_LEGS.indexOf(c)
                        val lifted = (p.legs == 1 && leg % 2 == 0) || (p.legs == 2 && leg % 2 == 1) || leg == footLeg
                        when {
                            dy > 0 -> rect(c, y + 1, 1, 1, body)
                            lifted -> rect(c, y, 1, 1, body)
                            else -> rect(c, y, 1, 2, body)
                        }
                    }
                    r == 2 && ((p.arm < 0 && c <= 1) || (p.arm > 0 && c >= 14)) -> rect(c, y - 1 + dy, 1, 2, body)
                    else -> rect(c, y + dy, 1, 2, body)
                }
            }
        } else SPRITE.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                if (ch == '.') return@forEachIndexed
                val color = paint(ch)
                when {
                    r == FEET_ROW -> {
                        val side = if (c < 8) 1 else 2
                        when {
                            dy > 0 -> rect(c.toFloat(), r + 0.5f, 1f, 0.5f, color)
                            p.legs == side || p.foot == side -> rect(c.toFloat(), r.toFloat(), 1f, 0.5f, color)
                            else -> rect(c, r, 1, 1, color)
                        }
                    }
                    r in ARM_ROWS && ((p.arm < 0 && c == 0) || (p.arm > 0 && c == 15)) -> rect(c, r - ARM_LIFT + dy, 1, 1, color)
                    r in EAR_ROWS && ((p.arm < 0 && c in LEFT_EAR) || (p.arm > 0 && c in RIGHT_EAR)) -> {
                        rect(c, r - 1 + dy, 1, 1, color)
                        if (r == EAR_ROWS.last) rect(c, r + dy, 1, 1, color)
                    }
                    else -> rect(c, r + dy, 1, 1, color)
                }
            }
        }

        if (clawd) {
            val eyeY = LOOK_TOP + 2 + dy
            if (alive && !out) {
                CLAWD_EYES.forEach { e ->
                    val x = (e + p.look).toFloat()
                    if (blink || sleepy) rect(x, eyeY + 1.2f, 1f, 0.4f, C.eye) else rect(x, eyeY.toFloat(), 1f, 2f, C.eye)
                }
            } else {
                val stroke = u * 0.5f
                CLAWD_EYES.forEach { col ->
                    val cx = ox + (col + 0.5f) * u
                    val cy = oy + (eyeY + 1) * u
                    val hw = u * 0.95f
                    val hh = u * 1.24f
                    drawLine(C.eye, Offset(cx - hw, cy - hh), Offset(cx + hw, cy + hh), stroke, StrokeCap.Square)
                    drawLine(C.eye, Offset(cx - hw, cy + hh), Offset(cx + hw, cy - hh), stroke, StrokeCap.Square)
                }
            }
        } else {
            val eyeY = EYE_ROW + dy
            if (alive && !out) {
                EYE_COLS.forEach { e ->
                    val x = (e + p.look).toFloat()
                    if (blink || sleepy) {
                        rect(x, eyeY + 1.6f, 2f, 0.4f, C.eye)
                    } else {
                        rect(x, eyeY.toFloat(), 2f, 2f, C.eye)
                        rect(x, eyeY.toFloat(), 1f, 1f, SHINE)
                    }
                }
            } else {
                val stroke = u * 0.45f
                EYE_COLS.forEach { col ->
                    val cx = ox + (col + 1f) * u
                    val cy = oy + (eyeY + 1f) * u
                    val hw = u * 0.95f
                    drawLine(X_EYE, Offset(cx - hw, cy - hw), Offset(cx + hw, cy + hw), stroke, StrokeCap.Square)
                    drawLine(X_EYE, Offset(cx - hw, cy + hw), Offset(cx + hw, cy - hw), stroke, StrokeCap.Square)
                }
            }
        }

        acc?.let { a -> accessoryPixels(a, look.species).forEach { rect(it.x, it.y + dy, it.w, it.h, Color(it.argb)) } }

        if (p.drop >= 0) rect(if (clawd) 14 else 15, p.drop + dy, 1, 2, SWEAT)
        if (p.note != 0) {
            val nx = if (p.note > 0) 13 else 0
            val ny = p.noteDy
            rect(nx + 1, ny, 2, 1, NOTE); rect(nx + 1, ny + 1, 1, 1, NOTE); rect(nx, ny + 2, 2, 1, NOTE)
        }
        if (p.z) {
            rect(13, 0, 3, 1, C.muted); rect(15, 1, 1, 1, C.muted)
            rect(14, 2, 1, 1, C.muted); rect(13, 3, 3, 1, C.muted)
        }
        when (p.boom) {
            2 -> SPARKS_NEAR.forEach { (x, y) -> rect(x, y, 1, 1, SPARK) }
            3 -> SPARKS_FAR.forEach { (x, y) -> rect(x, y, 1, 1, SPARK.copy(alpha = 0.6f)) }
        }
        if (p.smoke >= 0) {
            rect(7, p.smoke, 1, 1, SMOKE.copy(alpha = 0.8f))
            if (p.smoke >= 1) rect(8, p.smoke - 1, 1, 1, SMOKE.copy(alpha = 0.5f))
        }
    }
}
