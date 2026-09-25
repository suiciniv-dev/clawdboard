package dev.clawdboard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.clawdboard.core.MODELS
import dev.clawdboard.core.Mood
import dev.clawdboard.core.feelOf
import dev.clawdboard.core.ScopedLimit
import dev.clawdboard.core.StatusSnapshot
import dev.clawdboard.core.UsageSnapshot
import dev.clawdboard.core.UsageWindow
import dev.clawdboard.core.txt

@Composable
fun UsageBar(percent: Double?, modifier: Modifier = Modifier, height: Dp = 14.dp) {
    val target = ((percent ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
    val anim by animateFloatAsState(target, tween(900), label = "bar")
    val color = percent?.let { C.level(it) } ?: C.dim
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(C.track)
    ) {
        if (anim > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(anim)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

@Composable
fun UsageBlock(
    title: String,
    subtitle: String,
    window: UsageWindow?,
    modifier: Modifier = Modifier,
    scoped: List<ScopedLimit> = emptyList(),
    big: TextUnit = 60.sp,
    compact: Boolean = false,
) {
    val now = LocalNow.current
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, color = C.text, fontSize = 20.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            if (compact && scoped.isNotEmpty()) {
                ScopedChips(scoped, Modifier.padding(bottom = 3.dp))
            } else {
                Text(subtitle, color = C.dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                fmtPct(window?.percent),
                color = window?.percent?.let { C.level(it) } ?: C.dim,
                fontSize = big,
                fontFamily = Fredoka,
                fontWeight = FontWeight.Bold,
                lineHeight = big,
            )
            Spacer(Modifier.weight(1f))
            val reset = window?.resetsAt
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 8.dp)) {
                if (reset != null) {
                    if (!compact) Text(txt.resetsIn, color = C.muted, fontSize = 12.sp)
                    Text(fmtLeft(reset - now), color = C.text, fontSize = 24.sp, fontFamily = Fredoka, fontWeight = FontWeight.Medium)
                    Text(fmtAt(reset, now), color = C.muted, fontSize = 12.sp)
                } else {
                    Text(if (window == null) txt.waitingData else txt.noResetScheduled, color = C.dim, fontSize = 13.sp)
                }
            }
        }
        UsageBar(window?.percent)
        if (!compact && scoped.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ScopedChips(scoped)
        }
    }
}

@Composable
private fun ScopedChips(scoped: List<ScopedLimit>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        scoped.take(3).forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(7.dp).height(7.dp).clip(RoundedCornerShape(50)).background(C.level(s.percent)))
                Spacer(Modifier.width(5.dp))
                Text("${s.label} ${fmtPct(s.percent)}", color = C.muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun MascotRow(
    status: StatusSnapshot?,
    modifier: Modifier = Modifier,
    usage: UsageSnapshot? = null,
    mascotWidth: Dp = 56.dp,
    labels: Boolean = true,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = minOf(mascotWidth, maxWidth / MODELS.size * 0.82f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MODELS.forEachIndexed { i, m ->
                val down = status?.down?.contains(m) == true
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val feel = feelOf(usage, m)
                    val bad = down || feel.mood == Mood.EXHAUSTED
                    Mascot(Modifier.width(w), model = m, alive = !down, seed = i + 1, feel = feel)
                    if (labels) {
                        Spacer(Modifier.height(6.dp))
                        Text(m, color = if (bad) C.bad else C.muted, fontSize = 13.sp, fontWeight = if (bad) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusLine(status: StatusSnapshot?, modifier: Modifier = Modifier) {
    val (dot, text) = when {
        status == null -> C.dim to txt.checkingStatus
        status.incidents.isEmpty() -> C.ok to txt.allOperational
        else -> {
            val first = status.incidents.first().name
            val more = status.incidents.size - 1
            C.warn to (if (more > 0) "$first  (+$more)" else first)
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(8.dp).clip(RoundedCornerShape(50)).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, color = if (dot == C.warn) C.text else C.muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) C.clawd.copy(alpha = 0.18f) else C.card2)
            .border(1.dp, if (selected) C.clawd else C.line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) C.clawd else C.text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun PageDots(count: Int, index: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .width(if (i == index) 16.dp else 6.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i == index) C.muted.copy(alpha = 0.7f) else C.dim.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(4.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = C.muted, fontSize = 13.sp)
    }
}
