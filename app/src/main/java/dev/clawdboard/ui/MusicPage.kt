package dev.clawdboard.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.clawdboard.core.MediaApp
import dev.clawdboard.core.Music
import dev.clawdboard.core.NOTE_PIXELS
import dev.clawdboard.core.Repository
import dev.clawdboard.core.Track
import kotlin.math.roundToInt

private enum class Glyph { PLAY, PAUSE, NEXT, PREV }

@Composable
fun MusicPage(music: Music, st: Repository.State, landscape: Boolean, compact: Boolean) {
    val track by music.track.collectAsStateWithLifecycle()
    val access by music.access.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val t = track
    when {
        !access -> MusicEmpty(
            st,
            "Falta liberar o acesso",
            "Em Configurações → Música, toque em \"Liberar acesso\". O Android pede acesso às notificações para mostrar o que está tocando.",
            emptyList(),
        ) {}
        t == null -> {
            val apps = remember { music.apps() }
            MusicEmpty(
                st,
                "Nada tocando",
                "Dê play no Spotify, no YouTube Music ou em outro app de música. Os mascotes dançam junto.",
                apps,
            ) { music.launch(context, it.pkg) }
        }
        landscape -> Row(
            Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(t, Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
            Spacer(Modifier.width(if (compact) 22.dp else 32.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppChip(t.appIcon, t.app, "abrir") { music.open(context) }
                    Spacer(Modifier.width(20.dp))
                    VolumeBar(music, Modifier.weight(1f))
                }
                TrackText(t, compact, TextAlign.Start)
                Column {
                    Progress(t, music::seek)
                    Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                    Controls(t, music, compact)
                }
                if (!compact) MascotRow(st.status, Modifier.widthIn(max = 300.dp), usage = st.usage, clawdWidth = 40.dp, labels = false)
            }
        }
        else -> Column(
            Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            AppChip(t.appIcon, t.app, "abrir") { music.open(context) }
            Cover(t, Modifier.weight(1f, fill = false).padding(vertical = 12.dp).widthIn(max = 360.dp).aspectRatio(1f))
            TrackText(t, compact, TextAlign.Center)
            Progress(t, music::seek)
            Controls(t, music, compact)
            VolumeBar(music, Modifier.widthIn(max = 360.dp).padding(top = 4.dp, bottom = 10.dp))
            MascotRow(st.status, Modifier.widthIn(max = 300.dp), usage = st.usage, clawdWidth = 48.dp, labels = false)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusicEmpty(st: Repository.State, title: String, text: String, apps: List<MediaApp>, onApp: (MediaApp) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PixelNote(Modifier.width(64.dp), C.lav)
        Spacer(Modifier.height(16.dp))
        Text(title, color = C.text, fontSize = 26.sp, fontFamily = Fredoka, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(text, color = C.muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 480.dp))
        if (apps.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                apps.forEach { a -> AppChip(a.icon, a.label, "abrir") { onApp(a) } }
            }
        }
        Spacer(Modifier.height(22.dp))
        MascotRow(st.status, Modifier.widthIn(max = 300.dp), usage = st.usage, clawdWidth = 44.dp, labels = false)
    }
}

@Composable
private fun Cover(t: Track, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(C.card2), contentAlignment = Alignment.Center) {
        val art = t.art
        if (art != null) {
            val img = remember(art) { art.asImageBitmap() }
            Image(img, contentDescription = "Capa", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            PixelNote(Modifier.fillMaxWidth(0.4f), C.lav)
        }
    }
}

@Composable
fun PixelNote(modifier: Modifier, color: Color) {
    val cols = NOTE_PIXELS.first().length
    Canvas(modifier.aspectRatio(cols.toFloat() / NOTE_PIXELS.size)) {
        val u = size.width / cols
        NOTE_PIXELS.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                if (ch == '#') drawRect(color, Offset(c * u, r * u), Size(u + 0.6f, u + 0.6f))
            }
        }
    }
}

@Composable
private fun AppChip(icon: Bitmap?, label: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(C.card2)
            .clickable(onClick = onClick)
            .padding(start = if (icon != null) 8.dp else 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val img = remember(icon) { icon.asImageBitmap() }
            Image(img, contentDescription = null, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text(action, color = C.clawd, fontSize = 13.sp)
    }
}

@Composable
private fun TrackText(t: Track, compact: Boolean, align: TextAlign) {
    val size = if (compact) 24.sp else 30.sp
    Column(Modifier.fillMaxWidth()) {
        Text(
            t.title.ifBlank { "Sem título" }, color = C.text, fontSize = size, lineHeight = size * 1.1f,
            fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = align, modifier = Modifier.fillMaxWidth(),
        )
        if (t.artist.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                t.artist, color = C.muted, fontSize = if (compact) 15.sp else 18.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = align, modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(if (t.playing) " " else "pausado", color = C.dim, fontSize = 13.sp, textAlign = align, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
    }
}

@Composable
private fun Progress(t: Track, onSeek: (Long) -> Unit) {
    if (t.duration <= 0) return
    val pos = remember(LocalNow.current, t) { t.positionNow() }
    var drag by remember { mutableStateOf<Float?>(null) }
    val f = drag ?: (pos.toFloat() / t.duration).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        DragBar(
            f, t.canSeek, C.clawd, 8.dp, 14.dp,
            onDrag = { drag = it },
            onRelease = {
                onSeek((it * t.duration).toLong())
                drag = null
            },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(fmtTrack(drag?.let { (it * t.duration).toLong() } ?: pos), color = C.muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(fmtTrack(t.duration), color = C.dim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun VolumeBar(music: Music, modifier: Modifier) {
    var version by remember { mutableIntStateOf(0) }
    val v = remember(LocalNow.current, version) { music.volume() } ?: return
    var drag by remember { mutableStateOf<Float?>(null) }
    var sent by remember { mutableIntStateOf(-1) }
    fun send(f: Float) {
        val level = (f * v.max).roundToInt()
        if (level != sent) {
            sent = level
            music.setVolume(level)
        }
    }
    val f = drag ?: (v.level.toFloat() / v.max).coerceIn(0f, 1f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SpeakerIcon(f, C.muted, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        DragBar(
            f, true, C.muted, 6.dp, 12.dp,
            onDrag = {
                drag = it
                send(it)
            },
            onRelease = {
                send(it)
                drag = null
                sent = -1
                version++
            },
            modifier = Modifier.weight(1f).semantics { contentDescription = "Volume" },
        )
    }
}

@Composable
private fun DragBar(
    f: Float,
    enabled: Boolean,
    color: Color,
    barHeight: Dp,
    thumb: Dp,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val drag by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease)
    val gestures = if (!enabled) Modifier else Modifier
        .pointerInput(Unit) {
            detectTapGestures { o -> release((o.x / size.width).coerceIn(0f, 1f)) }
        }
        .pointerInput(Unit) {
            var last = 0f
            detectHorizontalDragGestures(
                onDragStart = { o ->
                    last = (o.x / size.width).coerceIn(0f, 1f)
                    drag(last)
                },
                onDragEnd = { release(last) },
                onDragCancel = { release(last) },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    last = (change.position.x / size.width).coerceIn(0f, 1f)
                    drag(last)
                },
            )
        }
    BoxWithConstraints(modifier.fillMaxWidth().height(thumb + 12.dp).then(gestures), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(50)).background(C.track)) {
            if (f > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(50)).background(color))
        }
        if (enabled) Box(Modifier.offset(x = (maxWidth - thumb) * f).size(thumb).clip(CircleShape).background(C.text))
    }
}

@Composable
private fun SpeakerIcon(f: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cone = Path().apply {
            moveTo(w * 0.08f, h * 0.36f)
            lineTo(w * 0.26f, h * 0.36f)
            lineTo(w * 0.48f, h * 0.16f)
            lineTo(w * 0.48f, h * 0.84f)
            lineTo(w * 0.26f, h * 0.64f)
            lineTo(w * 0.08f, h * 0.64f)
            close()
        }
        drawPath(cone, color)
        val line = w * 0.09f
        if (f <= 0f) {
            drawLine(color, Offset(w * 0.64f, h * 0.38f), Offset(w * 0.88f, h * 0.62f), line, StrokeCap.Round)
            drawLine(color, Offset(w * 0.64f, h * 0.62f), Offset(w * 0.88f, h * 0.38f), line, StrokeCap.Round)
        } else {
            val arc = Stroke(width = line, cap = StrokeCap.Round)
            drawArc(color, -45f, 90f, false, Offset(w * 0.3f, h * 0.5f - w * 0.2f), Size(w * 0.4f, w * 0.4f), style = arc)
            if (f > 0.5f) drawArc(color, -50f, 100f, false, Offset(w * 0.12f, h * 0.5f - w * 0.38f), Size(w * 0.76f, w * 0.76f), style = arc)
        }
    }
}

@Composable
private fun Controls(t: Track, music: Music, compact: Boolean) {
    val small = if (compact) 44.dp else 52.dp
    val big = if (compact) 60.dp else 72.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp)) {
        RoundButton(small, C.card2, "Faixa anterior", t.canPrev, music::previous) { GlyphIcon(Glyph.PREV, C.text, Modifier.size(small * 0.4f)) }
        RoundButton(big, C.clawd, if (t.playing) "Pausar" else "Tocar", true, music::playPause) {
            GlyphIcon(if (t.playing) Glyph.PAUSE else Glyph.PLAY, C.bg, Modifier.size(big * 0.4f))
        }
        RoundButton(small, C.card2, "Próxima faixa", t.canNext, music::next) { GlyphIcon(Glyph.NEXT, C.text, Modifier.size(small * 0.4f)) }
        t.extras.take(if (compact) 2 else 3).forEach { a ->
            RoundButton(small * 0.85f, Color.Transparent, a.name, true, { music.extra(a) }) {
                val icon = a.icon
                if (icon != null) {
                    val img = remember(icon) { icon.asImageBitmap() }
                    Image(img, contentDescription = null, colorFilter = ColorFilter.tint(C.muted), modifier = Modifier.size(22.dp))
                } else {
                    Text(a.name.take(1).uppercase(), color = C.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun RoundButton(size: Dp, bg: Color, desc: String, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun GlyphIcon(g: Glyph, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val soft = Stroke(width = w * 0.1f, join = StrokeJoin.Round)
        fun tri(left: Float, right: Float) = Path().apply {
            moveTo(left, h * 0.14f)
            lineTo(right, h * 0.5f)
            lineTo(left, h * 0.86f)
            close()
        }
        fun fill(p: Path) {
            drawPath(p, color)
            drawPath(p, color, style = soft)
        }
        fun bar(x: Float, bw: Float) = drawRoundRect(color, Offset(x, h * 0.1f), Size(bw, h * 0.8f), CornerRadius(bw * 0.35f))
        when (g) {
            Glyph.PLAY -> fill(tri(w * 0.2f, w * 0.88f))
            Glyph.PAUSE -> {
                bar(w * 0.16f, w * 0.24f)
                bar(w * 0.6f, w * 0.24f)
            }
            Glyph.NEXT -> {
                fill(tri(w * 0.1f, w * 0.68f))
                bar(w * 0.72f, w * 0.16f)
            }
            Glyph.PREV -> scale(-1f, 1f) {
                fill(tri(w * 0.1f, w * 0.68f))
                bar(w * 0.72f, w * 0.16f)
            }
        }
    }
}
