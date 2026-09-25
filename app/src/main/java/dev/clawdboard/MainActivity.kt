package dev.clawdboard

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.clawdboard.core.Brightness
import dev.clawdboard.core.Orientation
import dev.clawdboard.core.SelfTest
import dev.clawdboard.core.enumOr
import dev.clawdboard.ui.ClawdboardApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val repo = repo
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { repo.runLoops() }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.settings.flow.collect { p ->
                    applyBrightness(p.brightness)
                    requestedOrientation = when (p.orientation) {
                        Orientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        Orientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        Orientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                    }
                }
            }
        }

        intent?.let { handleDevIntent(it) }
        if (intent?.getBooleanExtra("selftest", false) == true) {
            lifecycleScope.launch(Dispatchers.Default) { SelfTest.run(applicationContext) }
        }
        setContent { ClawdboardApp(repo) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDevIntent(intent)
    }

    private fun handleDevIntent(i: Intent) {
        if (!i.getBooleanExtra("demo", false)) return
        val p5 = if (i.hasExtra("p5")) i.getIntExtra("p5", 37).toDouble() else 37.0
        val p7 = if (i.hasExtra("p7")) i.getIntExtra("p7", 64).toDouble() else 64.0
        val pf = if (i.hasExtra("pf")) i.getIntExtra("pf", 22).toDouble() else 22.0
        if (!repo.enterDemo(p5, p7, pf)) return
        if (i.getBooleanExtra("nudge", false)) repo.nudge.force()
        repo.updateSettings { p ->
            p.copy(
                mode = enumOr(i.getStringExtra("mode"), p.mode),
                orientation = enumOr(i.getStringExtra("orient"), p.orientation),
                backdrop = enumOr(i.getStringExtra("backdrop"), p.backdrop),
                zoom = i.getIntExtra("zoom", p.zoom),
                skin = enumOr(i.getStringExtra("skin"), p.skin),
                tint = enumOr(i.getStringExtra("tint"), p.tint),
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun applyBrightness(b: Brightness) {
        val attrs = window.attributes
        attrs.screenBrightness = if (b.level < 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else b.level
        window.attributes = attrs
    }
}
