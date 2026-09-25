package dev.clawdboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.clawdboard.core.Repository
import dev.clawdboard.core.Sample
import dev.clawdboard.core.ScreenMode
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay

private enum class Overlay { NONE, PIN, SETTINGS }
private enum class Page { DASH, MASCOTS, CHART, NEWS, CLOCK }

@Composable
fun ClawdboardApp(repo: Repository) {
    val st by repo.state.collectAsStateWithLifecycle()
    val prefs by repo.settings.flow.collectAsStateWithLifecycle()
    val history by repo.history.flow.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            now = System.currentTimeMillis()
        }
    }

    SideEffect { C.apply(prefs.backdrop) }

    var overlay by remember { mutableStateOf(Overlay.NONE) }
    LaunchedEffect(st.unlocked, st.provisioned) {
        if (!st.unlocked || !st.provisioned) overlay = Overlay.NONE
    }

    val look = remember(prefs.skin, prefs.tint, prefs.animations) { Look(prefs.skin, prefs.tint, prefs.animations) }
    CompositionLocalProvider(LocalNow provides now, LocalLook provides look) {
        ClawdTheme {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(C.bg)
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                listOf(C.glow, Color.Transparent),
                                center = Offset(size.width / 2f, size.height * 1.05f),
                                radius = size.maxDimension * 0.55f,
                            )
                        )
                    }
                    .windowInsetsPadding(WindowInsets.displayCutout)
            ) {
                when {
                    !st.provisioned -> SetupScreen(repo, st)
                    !st.unlocked -> LockScreen(repo, st)
                    overlay == Overlay.SETTINGS -> Zoomed(prefs.zoom) {
                        SettingsScreen(repo, st, prefs, onClose = { overlay = Overlay.NONE })
                    }
                    overlay == Overlay.PIN -> {
                        BackHandler { overlay = Overlay.NONE }
                        PinGate(repo, st, onOk = { overlay = Overlay.SETTINGS }, onCancel = { overlay = Overlay.NONE })
                    }
                    else -> Zoomed(prefs.zoom) {
                        Box(Modifier.fillMaxSize()) {
                            Shifted(prefs.pixelShift) {
                                Screens(st, prefs.mode, prefs.dwellSec, history, onSettings = { overlay = Overlay.PIN })
                            }
                            FeedbackCard(repo, Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Zoomed(percent: Int, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val zoomed = remember(base, percent) { Density(base.density * percent / 100f, base.fontScale) }
    CompositionLocalProvider(LocalDensity provides zoomed, content = content)
}

@Composable
private fun Shifted(enabled: Boolean, content: @Composable () -> Unit) {
    val target by produceState(Pair(0, 0), enabled) {
        value = Pair(0, 0)
        while (enabled) {
            delay(60_000)
            value = Pair(Random.nextInt(-6, 7), Random.nextInt(-5, 6))
        }
    }
    val dx by animateDpAsState(target.first.dp, tween(2500), label = "dx")
    val dy by animateDpAsState(target.second.dp, tween(2500), label = "dy")
    Box(Modifier.fillMaxSize().offset(dx, dy)) { content() }
}

@Composable
private fun Screens(
    st: Repository.State,
    mode: ScreenMode,
    dwellSec: Int,
    history: List<Sample>,
    onSettings: () -> Unit,
) {
    val pages = Page.entries
    val home = when (mode) {
        ScreenMode.CLOCK -> Page.CLOCK
        ScreenMode.MASCOTS -> Page.MASCOTS
        else -> Page.DASH
    }
    var page by remember { mutableStateOf(home) }
    var swipeDir by remember { mutableIntStateOf(0) }
    fun go(step: Int) {
        swipeDir = step
        page = pages[(page.ordinal + step + pages.size) % pages.size]
    }
    LaunchedEffect(mode) { swipeDir = 0; page = home }
    LaunchedEffect(mode, dwellSec, page) {
        if (mode == ScreenMode.CAROUSEL) {
            delay(dwellSec * 1000L)
            swipeDir = 0
            page = pages[(page.ordinal + 1) % pages.size]
        } else if (page != home) {
            delay(30_000)
            swipeDir = 0
            page = home
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (abs(dragged) > 48.dp.toPx()) go(if (dragged < 0) 1 else -1) },
                    onHorizontalDrag = { change, dx -> change.consume(); dragged += dx },
                )
            }
    ) {
        val landscape = maxWidth > maxHeight
        val compact = minOf(maxWidth, maxHeight) < 380.dp
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val dir = swipeDir
                if (dir == 0) {
                    fadeIn(tween(700)) togetherWith fadeOut(tween(700))
                } else {
                    (slideInHorizontally(tween(320)) { w -> w * dir } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(320)) { w -> -w * dir } + fadeOut(tween(320)))
                }
            },
            label = "page",
        ) { p ->
            when (p) {
                Page.DASH -> DashboardPage(st, landscape, compact)
                Page.MASCOTS -> MascotsPage(st, landscape, compact)
                Page.CHART -> ChartPage(history, st, landscape)
                Page.NEWS -> NewsPage(st.news, landscape)
                Page.CLOCK -> ClockPage(st, landscape)
            }
        }
        PageDots(pages.size, page.ordinal, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 2.dp)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Configurações", tint = C.dim.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        }
    }
}
