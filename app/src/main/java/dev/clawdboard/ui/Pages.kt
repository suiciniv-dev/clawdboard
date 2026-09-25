package dev.clawdboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.clawdboard.core.History
import dev.clawdboard.core.MODELS
import dev.clawdboard.core.Mood
import dev.clawdboard.core.NewsItem
import dev.clawdboard.core.Repository
import dev.clawdboard.core.Sample
import dev.clawdboard.core.UsageSnapshot
import dev.clawdboard.core.UsageWindow
import dev.clawdboard.core.feelOf
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

private val PAGE_PAD = Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 30.dp)
private val PAGE_PAD_COMPACT = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp)

@Composable
fun DashboardPage(st: Repository.State, landscape: Boolean, compact: Boolean = false) {
    val usage = st.usage
    val pad = if (compact) PAGE_PAD_COMPACT else PAGE_PAD
    if (landscape) {
        val big = if (compact) 52.sp else 60.sp
        Row(Modifier.fillMaxSize().then(pad)) {
            Column(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                UsageBlock("Sessão", "janela de 5 horas", usage?.fiveHour, big = big, compact = compact)
                UsageBlock("Semana", "janela de 7 dias", usage?.sevenDay, scoped = usage?.scoped.orEmpty(), big = big, compact = compact)
            }
            Spacer(Modifier.width(if (compact) 24.dp else 32.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                SmallClock()
                MascotRow(st.status, usage = usage)
                StatusLine(st.status)
                Footer(st, compact)
            }
        }
    } else {
        val big = if (compact) 52.sp else 72.sp
        Column(Modifier.fillMaxSize().then(pad), verticalArrangement = Arrangement.SpaceEvenly) {
            SmallClock()
            UsageBlock("Sessão", "janela de 5 horas", usage?.fiveHour, big = big, compact = compact)
            UsageBlock("Semana", "janela de 7 dias", usage?.sevenDay, scoped = usage?.scoped.orEmpty(), big = big, compact = compact)
            MascotRow(st.status, usage = usage, clawdWidth = 64.dp)
            StatusLine(st.status)
            Footer(st, compact)
        }
    }
}

@Composable
private fun SmallClock() {
    val t = zoned(LocalNow.current)
    Row(verticalAlignment = Alignment.Bottom) {
        Text("%02d:%02d".format(t.hour, t.minute), color = C.text, fontSize = 44.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, lineHeight = 44.sp)
        Spacer(Modifier.width(12.dp))
        Text(fmtDateLong(t), color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
private fun Footer(st: Repository.State, compact: Boolean) {
    val now = LocalNow.current
    Column {
        val at = st.lastPushAt
        if (st.usage != null && at != null) {
            Text("atualizado ${fmtAgo(now - at)} · Claude Code", color = C.dim, fontSize = 13.sp)
        } else {
            Text("aguardando o Claude Code · conecte pelo painel no PC", color = C.warn, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!compact) st.panelUrl?.let { Text("painel: ${it.removePrefix("http://")}", color = C.dim, fontSize = 12.sp) }
    }
}

@Composable
fun ChartPage(history: List<Sample>, st: Repository.State, landscape: Boolean = true) {
    val now = LocalNow.current
    val tm = rememberTextMeasurer()
    val t0 = now - History.WINDOW_MS
    val inWindow = history.filter { it.t >= t0 }
    Column(Modifier.fillMaxSize().then(PAGE_PAD)) {
        if (landscape) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Últimos 7 dias", color = C.text, fontSize = 26.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Legend(C.clawd, "sessão 5h")
                Spacer(Modifier.width(18.dp))
                Legend(C.lav, "semana 7d")
            }
        } else {
            Text("Últimos 7 dias", color = C.text, fontSize = 26.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row {
                Legend(C.clawd, "sessão 5h")
                Spacer(Modifier.width(18.dp))
                Legend(C.lav, "semana 7d")
            }
        }
        Spacer(Modifier.height(10.dp))
        val labelStyle = TextStyle(color = C.dim, fontSize = 11.sp)
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val left = 38.dp.toPx()
            val right = 6.dp.toPx()
            val top = 6.dp.toPx()
            val bottom = 22.dp.toPx()
            val w = size.width - left - right
            val h = size.height - top - bottom
            fun x(t: Long) = left + ((t - t0).toFloat() / History.WINDOW_MS.toFloat()) * w
            fun y(p: Double) = top + h - (p.coerceIn(0.0, 100.0).toFloat() / 100f) * h

            listOf(0, 25, 50, 75, 100).forEach { g ->
                val yy = y(g.toDouble())
                drawLine(C.line, Offset(left, yy), Offset(left + w, yy), 1.dp.toPx())
                val lay = tm.measure("$g%", labelStyle)
                drawText(lay, topLeft = Offset(left - lay.size.width - 6.dp.toPx(), yy - lay.size.height / 2f))
            }
            val zone = ZoneId.systemDefault()
            var day = zoned(t0).toLocalDate().plusDays(1).atStartOfDay(zone)
            while (day.toInstant().toEpochMilli() < now) {
                val ms = day.toInstant().toEpochMilli()
                val xx = x(ms)
                drawLine(C.line.copy(alpha = 0.55f), Offset(xx, top), Offset(xx, top + h), 1.dp.toPx())
                val mid = ms + 12 * 3_600_000L
                if (mid < now) {
                    val lay = tm.measure("${weekShort(day.dayOfWeek)} ${day.dayOfMonth}", labelStyle)
                    drawText(lay, topLeft = Offset(x(mid) - lay.size.width / 2f, top + h + 5.dp.toPx()))
                }
                day = day.plusDays(1)
            }

            val stroke = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun series(sel: (Sample) -> Double?, color: Color) {
                var path: Path? = null
                var count = 0
                var prevT = 0L
                var last = Offset.Zero
                fun flush() {
                    val p = path
                    if (p != null) {
                        if (count > 1) drawPath(p, color, style = stroke) else drawCircle(color, 2.6.dp.toPx(), last)
                    }
                    path = null
                    count = 0
                }
                for (s in inWindow) {
                    val v = sel(s)
                    if (v == null) {
                        flush(); continue
                    }
                    val pt = Offset(x(s.t), y(v))
                    val current = path
                    if (current == null || s.t - prevT > History.SLOT_MS * 3 / 2) {
                        flush()
                        path = Path().apply { moveTo(pt.x, pt.y) }
                        count = 1
                    } else {
                        current.lineTo(pt.x, pt.y)
                        count++
                    }
                    prevT = s.t
                    last = pt
                }
                flush()
            }
            series({ it.p7 }, C.lav)
            series({ it.p5 }, C.clawd)
        }
        Spacer(Modifier.height(8.dp))
        val peak5 = inWindow.mapNotNull { it.p5 }.maxOrNull()
        val stats: @Composable () -> Unit = {
            Text("pico 5h: ${fmtPct(peak5)}", color = C.muted, fontSize = 14.sp)
            Text("semana agora: ${fmtPct(st.usage?.sevenDay?.percent)}", color = C.muted, fontSize = 14.sp)
            Text(
                if (inWindow.isEmpty()) "sem amostras ainda, uma a cada 30 min" else "${inWindow.size} de 336 amostras",
                color = C.dim, fontSize = 14.sp,
            )
        }
        if (landscape) Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) { stats() } else Column { stats() }
    }
}

@Composable
fun NewsPage(news: List<NewsItem>, landscape: Boolean) {
    Column(Modifier.fillMaxSize().then(PAGE_PAD)) {
        if (landscape) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Anthropic News", color = C.text, fontSize = 26.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("anthropic.com/news", color = C.dim, fontSize = 13.sp)
            }
        } else {
            Text("Anthropic News", color = C.text, fontSize = 26.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold)
            Text("anthropic.com/news", color = C.dim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (news.isEmpty()) {
            Text("Carregando notícias...", color = C.muted, fontSize = 16.sp)
        }
        FitColumn(Modifier.fillMaxWidth().weight(1f)) {
            news.take(if (landscape) 5 else 9).forEachIndexed { i, item ->
                Column(Modifier.fillMaxWidth()) {
                    if (i > 0) Spacer(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
                        Text(fmtShortDate(item.date), color = C.clawd, fontSize = 14.sp, modifier = Modifier.width(64.dp).padding(top = 2.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = C.text, fontSize = 18.sp, maxLines = if (landscape) 1 else 2, overflow = TextOverflow.Ellipsis)
                            item.category?.let { Text(it, color = C.dim, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FitColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val shown = mutableListOf<Placeable>()
        var used = 0
        for (m in measurables) {
            val p = m.measure(loose)
            if (used + p.height > constraints.maxHeight) break
            shown += p
            used += p.height
        }
        layout(constraints.maxWidth, maxOf(used, constraints.minHeight)) {
            var y = 0
            shown.forEach { it.place(0, y); y += it.height }
        }
    }
}

@Composable
fun ClockPage(st: Repository.State, landscape: Boolean) {
    val now = LocalNow.current
    val t = zoned(now)
    val digits = "%02d%02d".format(t.hour, t.minute)
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 30.dp)) {
        val fsVal = min(maxWidth.value / 3.1f, maxHeight.value / (if (landscape) 2.15f else 3.4f))
        val fs = fsVal.sp
        val big = min(maxWidth.value / 1.5f, maxHeight.value / 4.3f).sp
        val stackMinis = maxWidth < 400.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (landscape) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    digits.forEachIndexed { i, ch ->
                        if (i == 2) {
                            val on = (now / 1000) % 2 == 0L
                            Text(":", color = C.muted.copy(alpha = if (on) 0.85f else 0.3f), fontSize = fs * 0.8f, fontFamily = Fredoka, fontWeight = FontWeight.Bold, lineHeight = fs)
                        }
                        Text(ch.toString(), color = C.clockDigits[i], fontSize = fs, fontFamily = Fredoka, fontWeight = FontWeight.Bold, lineHeight = fs)
                    }
                }
            } else {
                listOf(0, 2).forEach { start ->
                    Row {
                        (start..start + 1).forEach { i ->
                            Text(digits[i].toString(), color = C.clockDigits[i], fontSize = big, fontFamily = Fredoka, fontWeight = FontWeight.Bold, lineHeight = big * 0.8f)
                        }
                    }
                }
            }
            Text(fmtDateLong(t), color = C.muted, fontSize = 20.sp)
            Spacer(Modifier.height(18.dp))
            val minis: @Composable () -> Unit = {
                MiniUsage("5h", st.usage?.fiveHour?.percent)
                MiniUsage("7d", st.usage?.sevenDay?.percent)
            }
            if (stackMinis) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { minis() }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) { minis() }
            }
            Spacer(Modifier.height(14.dp))
            MascotRow(st.status, Modifier.width(260.dp), usage = st.usage, clawdWidth = 34.dp, labels = false)
        }
    }
}

@Composable
private fun MiniUsage(label: String, p: Double?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.muted, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        UsageBar(p, Modifier.width(110.dp), height = 8.dp)
        Spacer(Modifier.width(8.dp))
        Text(p?.let { "${it.roundToInt()}%" } ?: "--", color = p?.let { C.level(it) } ?: C.dim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private data class ModelUsage(val percent: Double?, val own: Boolean)

private fun modelUsage(usage: UsageSnapshot?, model: String): ModelUsage {
    val scoped = usage?.scoped?.firstOrNull { it.label.contains(model, ignoreCase = true) }
    return if (scoped != null) ModelUsage(scoped.percent, true) else ModelUsage(usage?.sevenDay?.percent, false)
}

@Composable
fun MascotsPage(st: Repository.State, landscape: Boolean, compact: Boolean = false) {
    val usage = st.usage
    if (landscape) {
        Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = if (compact) 10.dp else 16.dp, bottom = 28.dp)) {
            Column(
                (if (compact) Modifier.padding(bottom = 8.dp) else Modifier.weight(1f)).fillMaxWidth(),
                verticalArrangement = if (compact) Arrangement.spacedBy(4.dp) else Arrangement.SpaceEvenly,
            ) {
                WideUsageRow("Sessão", "5 horas", usage?.fiveHour, compact)
                WideUsageRow("Semana", "7 dias", usage?.sevenDay, compact)
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            Row(
                Modifier.weight(if (compact) 1f else 1.15f).fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                MODELS.forEachIndexed { i, m ->
                    BigMascot(m, i, st, modelUsage(usage, m), Modifier.weight(1f), maxClawd = 132.dp, compact = compact)
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp)) {
            SmallClockLine()
            val big = if (compact) 52.sp else 64.sp
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.SpaceEvenly) {
                UsageBlock("Sessão", "janela de 5 horas", usage?.fiveHour, big = big, compact = compact)
                UsageBlock("Semana", "janela de 7 dias", usage?.sevenDay, big = big, compact = compact)
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(C.line))
            Column(Modifier.weight(1.1f).fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                MODELS.chunked(2).forEachIndexed { r, pair ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        pair.forEachIndexed { c, m ->
                            BigMascot(m, r * 2 + c, st, modelUsage(usage, m), Modifier.weight(1f), maxClawd = 128.dp, compact = compact)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallClockLine() {
    val t = zoned(LocalNow.current)
    Row(verticalAlignment = Alignment.Bottom) {
        Text("%02d:%02d".format(t.hour, t.minute), color = C.text, fontSize = 36.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp)
        Spacer(Modifier.width(10.dp))
        Text(fmtDateLong(t), color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 5.dp))
    }
}

@Composable
private fun WideUsageRow(title: String, window: String, w: UsageWindow?, compact: Boolean = false) {
    val now = LocalNow.current
    if (compact) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = C.text, fontSize = 18.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.width(80.dp))
            Text(
                fmtPct(w?.percent), color = w?.percent?.let { C.level(it) } ?: C.dim,
                fontSize = 36.sp, fontFamily = Fredoka, fontWeight = FontWeight.Bold, lineHeight = 36.sp, maxLines = 1,
                modifier = Modifier.width(100.dp),
            )
            UsageBar(w?.percent, Modifier.weight(1f), height = 14.dp)
            Text(
                w?.resetsAt?.let { fmtLeft(it - now) } ?: "--", color = C.text, fontSize = 20.sp, fontFamily = Fredoka,
                fontWeight = FontWeight.Medium, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(110.dp),
            )
        }
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(190.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, color = C.text, fontSize = 20.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Text(window, color = C.dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
            Text(
                fmtPct(w?.percent), color = w?.percent?.let { C.level(it) } ?: C.dim,
                fontSize = 58.sp, fontFamily = Fredoka, fontWeight = FontWeight.Bold, lineHeight = 58.sp,
            )
        }
        UsageBar(w?.percent, Modifier.weight(1f), height = 18.dp)
        Spacer(Modifier.width(22.dp))
        Column(Modifier.width(150.dp), horizontalAlignment = Alignment.End) {
            val reset = w?.resetsAt
            if (reset != null) {
                Text("libera em", color = C.muted, fontSize = 12.sp)
                Text(fmtLeft(reset - now), color = C.text, fontSize = 24.sp, fontFamily = Fredoka, fontWeight = FontWeight.Medium)
                Text(fmtAt(reset, now), color = C.muted, fontSize = 12.sp)
            } else {
                Text(if (w == null) "aguardando dados" else "sem reset", color = C.dim, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun BigMascot(model: String, index: Int, st: Repository.State, mu: ModelUsage, modifier: Modifier, maxClawd: Dp, compact: Boolean) {
    val down = st.status?.down?.contains(model) == true
    val feel = feelOf(st.usage, model)
    val out = feel.mood == Mood.EXHAUSTED
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val barW = minOf(maxClawd * 0.8f, maxWidth * 0.7f)
        val arrangement = if (compact) Arrangement.Center else Arrangement.Bottom
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = arrangement) {
            Text(
                fmtPct(mu.percent),
                color = when {
                    mu.percent == null -> C.dim
                    mu.own -> C.level(mu.percent)
                    else -> C.muted
                },
                fontSize = 18.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            if (mu.own) {
                UsageBar(mu.percent, Modifier.width(barW), height = 8.dp)
            } else {
                SharedBar(mu.percent, Modifier.width(barW))
            }
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                Clawd(
                    Modifier.width(minOf(maxClawd, maxWidth * 0.85f, maxHeight * CLAWD_ASPECT)),
                    model = model, alive = !down, seed = index + 11, feel = feel,
                )
            }
            Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
            Text(model, color = if (down || out) C.bad else C.text, fontSize = 17.sp, fontFamily = Fredoka, fontWeight = FontWeight.Medium)
            if (!compact) Text(
                when {
                    down -> "instável"
                    out -> "esgotado"
                    else -> " "
                },
                color = C.bad, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SharedBar(percent: Double?, modifier: Modifier) {
    val f = ((percent ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
    Box(
        modifier.height(8.dp).clip(RoundedCornerShape(50)).background(C.track)
    ) {
        if (f > 0f) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(50)).background(C.muted.copy(alpha = 0.55f))
            )
        }
    }
}
