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
import dev.clawdboard.core.Accessory
import dev.clawdboard.core.Feel
import dev.clawdboard.core.Mood
import dev.clawdboard.core.LOOK_ROWS
import dev.clawdboard.core.LOOK_TOP
import dev.clawdboard.core.Skin
import dev.clawdboard.core.Tint
import dev.clawdboard.core.accessoryFor
import dev.clawdboard.core.accessoryPixels
import dev.clawdboard.core.bodyArgb
import kotlinx.coroutines.delay
import kotlin.random.Random

private val BODY = arrayOf(
    "..############..",
    "..##E######E##..",
    "################",
    "..############..",
    "...#.#....#.#...",
)
private val EYE_COLS = intArrayOf(4, 11)
private val LEG_COLS = intArrayOf(3, 5, 10, 12)
private val SWEAT = Color(0xFF8FD3F4)
private val HOT = Color(0xFFFF3B2F)
private val CHARRED = Color(0xFF4E3530)
private val FLASH = Color(0xFFFFE9A8)
private val SPARK = Color(0xFFF5B83C)
private val SMOKE = Color(0xFF8A8078)

private val SPARKS_NEAR = listOf(1 to 3, 14 to 3, 0 to 7, 15 to 7, 2 to 13, 13 to 13, 7 to 1, 9 to 2)
private val SPARKS_FAR = listOf(0 to 0, 15 to 0, 0 to 12, 15 to 12, 4 to 0, 11 to 1, 6 to 13, 10 to 13)

const val CLAWD_ASPECT = 16f / LOOK_ROWS

data class Look(val skin: Skin = Skin.MODELS, val tint: Tint = Tint.CORAL, val animations: Boolean = true)

val LocalLook = compositionLocalOf { Look() }

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
)

@Composable
fun Clawd(
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
    val mood = if (alive) feel.mood else Mood.NORMAL
    val out = mood == Mood.EXHAUSTED
    val sleepy = mood == Mood.SLEEPY
    val trembling = feel.heat >= 0.5f
    var blink by remember { mutableStateOf(false) }
    var pose by remember { mutableStateOf(Pose()) }
    var wasOut by remember { mutableStateOf(out) }

    LaunchedEffect(mood, animate) { pose = Pose() }

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

    if (animate && !sleepy && !out) {
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

    val base = Color(bodyArgb(look.tint, model))
    Canvas(modifier.aspectRatio(if (reserveTop) CLAWD_ASPECT else 16f / 10f)) {
        val u = size.width / 16f
        val oy = if (reserveTop) 0f else -LOOK_TOP * u
        val p = pose
        val ox = p.shake * 0.5f * u
        val dy = p.dy
        val body = when {
            !alive -> C.deadBody
            p.boom == 1 -> FLASH
            out -> CHARRED
            feel.heat > 0f -> lerp(base, HOT, feel.heat * (if (p.throb) 1f else 0.72f))
            else -> base
        }
        fun rect(x: Float, y: Float, w: Float, h: Float, c: Color) =
            drawRect(c, Offset(ox + x * u, oy + y * u), Size(w * u + 0.6f, h * u + 0.6f))
        fun rect(x: Int, y: Int, w: Int, h: Int, c: Color) = rect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), c)

        BODY.forEachIndexed { r, row ->
            val y = LOOK_TOP + r * 2
            row.forEachIndexed { c, ch ->
                if (ch == '.') return@forEachIndexed
                when {
                    r == 4 -> {
                        val leg = LEG_COLS.indexOf(c)
                        val lifted = (p.legs == 1 && leg % 2 == 0) || (p.legs == 2 && leg % 2 == 1)
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
        }

        val eyeY = LOOK_TOP + 2 + dy
        if (alive && !out) {
            EYE_COLS.forEach { e ->
                val x = (e + p.look).toFloat()
                if (blink || sleepy) rect(x, eyeY + 1.2f, 1f, 0.4f, C.eye) else rect(x, eyeY.toFloat(), 1f, 2f, C.eye)
            }
        } else {
            val stroke = u * 0.5f
            EYE_COLS.forEach { col ->
                val cx = ox + (col + 0.5f) * u
                val cy = oy + (eyeY + 1) * u
                val hw = u * 0.95f
                val hh = u * 1.24f
                drawLine(C.eye, Offset(cx - hw, cy - hh), Offset(cx + hw, cy + hh), stroke, StrokeCap.Square)
                drawLine(C.eye, Offset(cx - hw, cy + hh), Offset(cx + hw, cy - hh), stroke, StrokeCap.Square)
            }
        }

        acc?.let { a -> accessoryPixels(a).forEach { rect(it.x, it.y + dy, it.w, it.h, Color(it.argb)) } }

        if (p.drop >= 0) rect(14, p.drop + dy, 1, 2, SWEAT)
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
