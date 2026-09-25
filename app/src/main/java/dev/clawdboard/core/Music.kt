package dev.clawdboard.core

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.VolumeProvider
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import dev.clawdboard.MediaListener
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TrackAction(val id: String, val name: String, val icon: Bitmap?)

data class Track(
    val pkg: String,
    val app: String,
    val appIcon: Bitmap?,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val position: Long,
    val positionAt: Long,
    val speed: Float,
    val duration: Long,
    val canSeek: Boolean,
    val canPrev: Boolean,
    val canNext: Boolean,
    val extras: List<TrackAction> = emptyList(),
) {
    fun positionNow(): Long = livePosition(position, positionAt, speed, playing, duration, SystemClock.elapsedRealtime())
}

data class MediaApp(val pkg: String, val label: String, val icon: Bitmap?)

data class Volume(val level: Int, val max: Int)

fun livePosition(position: Long, at: Long, speed: Float, playing: Boolean, duration: Long, now: Long): Long {
    val p = if (playing && at > 0 && position >= 0) position + ((now - at) * speed).toLong() else position
    val floor = p.coerceAtLeast(0)
    return if (duration > 0) floor.coerceAtMost(duration) else floor
}

private val PLAYING_STATES = setOf(
    PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING,
    PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
)
private val PREFERRED = listOf("com.spotify.music", "com.google.android.apps.youtube.music", "deezer.android.app", "com.amazon.mp3")
private val KNOWN = mapOf("com.spotify.music" to "Spotify", "com.google.android.apps.youtube.music" to "YouTube Music")

private fun isPlaying(ps: PlaybackState?) = ps != null && ps.state in PLAYING_STATES

class Music(private val app: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val sessions = app.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(app, MediaListener::class.java)
    private val audio = app.getSystemService(AudioManager::class.java)
    private var demoVolume = 9

    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track.asStateFlow()
    private val _access = MutableStateFlow(hasAccess())
    val access: StateFlow<Boolean> = _access.asStateFlow()

    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var controllers: List<MediaController> = emptyList()
    private var current: MediaController? = null
    private var attached = false
    private var demo = false
    private val appCache = HashMap<String, Pair<String, Bitmap?>>()
    private val iconCache = HashMap<Pair<String, Int>, Bitmap?>()
    private val artCache = HashMap<String, Bitmap?>()

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = choose()
        override fun onMetadataChanged(metadata: MediaMetadata?) = choose()
        override fun onSessionDestroyed() = choose()
    }
    private val changed = MediaSessionManager.OnActiveSessionsChangedListener { use(it.orEmpty()) }

    fun hasAccess(): Boolean = NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)

    fun poke() {
        signal.trySend(Unit)
    }

    suspend fun watch() {
        try {
            while (true) {
                attach()
                signal.receive()
            }
        } finally {
            detach()
        }
    }

    private fun attach() {
        detach()
        if (demo) {
            _access.value = true
            return
        }
        val ok = hasAccess()
        _access.value = ok
        if (!ok) {
            _track.value = null
            return
        }
        try {
            sessions.addOnActiveSessionsChangedListener(changed, listener, main)
            attached = true
            use(sessions.getActiveSessions(listener))
        } catch (e: SecurityException) {
            _access.value = false
            _track.value = null
        }
    }

    private fun detach() {
        if (attached) runCatching { sessions.removeOnActiveSessionsChangedListener(changed) }
        attached = false
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = emptyList()
        current = null
    }

    private fun use(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list
        list.forEach { it.registerCallback(callback, main) }
        choose()
    }

    private fun choose() {
        if (demo) return
        val live = controllers.filter { it.metadata != null || it.playbackState != null }
        val pick = live.firstOrNull { isPlaying(it.playbackState) }
            ?: current?.let { c -> live.firstOrNull { it.sessionToken == c.sessionToken } }
            ?: live.firstOrNull()
        current = pick
        _track.value = pick?.let(::read)
    }

    private fun read(c: MediaController): Track {
        val md = c.metadata
        val ps = c.playbackState
        val actions = ps?.actions ?: 0L
        fun can(a: Long) = actions == 0L || actions and a != 0L
        fun text(vararg keys: String) = keys.firstNotNullOfOrNull { k -> md?.getText(k)?.toString()?.takeIf { it.isNotBlank() } } ?: ""
        val (label, icon) = appInfo(c.packageName)
        return Track(
            pkg = c.packageName,
            app = label,
            appIcon = icon,
            title = text(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = text(MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            art = md?.let {
                it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                    ?: text(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                        .takeIf { u -> u.isNotEmpty() }?.let(::artFrom)
            },
            playing = isPlaying(ps),
            position = ps?.position ?: 0L,
            positionAt = ps?.lastPositionUpdateTime ?: 0L,
            speed = ps?.playbackSpeed ?: 1f,
            duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
            canPrev = can(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
            canNext = can(PlaybackState.ACTION_SKIP_TO_NEXT),
            extras = ps?.customActions.orEmpty().take(3).map { TrackAction(it.action, it.name.toString(), actionIcon(c.packageName, it.icon)) },
        )
    }

    private fun appInfo(pkg: String): Pair<String, Bitmap?> = appCache.getOrPut(pkg) {
        val pm = app.packageManager
        runCatching {
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString() to runCatching { pm.getApplicationIcon(ai).toBitmap(96, 96) }.getOrNull()
        }.getOrDefault((KNOWN[pkg] ?: pkg) to null)
    }

    private fun artFrom(uri: String): Bitmap? {
        if (artCache.containsKey(uri)) return artCache[uri]
        artCache[uri] = null
        Thread {
            val bmp = runCatching { decodeArt(uri) }.getOrNull()
            main.post {
                if (artCache.size > 12) artCache.keys.filter { it != uri }.take(6).forEach(artCache::remove)
                artCache[uri] = bmp
                if (bmp != null) choose()
            }
        }.start()
        return null
    }

    private fun decodeArt(uri: String): Bitmap? {
        val bytes = if (uri.startsWith("http://") || uri.startsWith("https://")) {
            val c = URL(uri).openConnection() as HttpURLConnection
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            try {
                c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
        } else {
            app.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() } ?: return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun actionIcon(pkg: String, id: Int): Bitmap? = if (id == 0) null else iconCache.getOrPut(pkg to id) {
        runCatching { Icon.createWithResource(pkg, id).loadDrawable(app)?.toBitmap(72, 72) }.getOrNull()
    }

    fun playPause() {
        if (demo) {
            _track.update { t -> t?.let { val now = SystemClock.elapsedRealtime(); it.copy(playing = !it.playing, position = it.positionNow(), positionAt = now) } }
            return
        }
        val c = current ?: return
        if (isPlaying(c.playbackState)) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        if (demo) return seek(0)
        current?.transportControls?.skipToNext()
    }

    fun previous() {
        if (demo) return seek(0)
        current?.transportControls?.skipToPrevious()
    }

    fun seek(ms: Long) {
        if (demo) {
            _track.update { it?.copy(position = ms, positionAt = SystemClock.elapsedRealtime()) }
            return
        }
        current?.transportControls?.seekTo(ms)
    }

    private fun remoteInfo(): MediaController.PlaybackInfo? =
        current?.playbackInfo?.takeIf { it.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE }

    fun volume(): Volume? {
        if (demo) return Volume(demoVolume, 15)
        val remote = remoteInfo()
        if (remote != null) {
            if (remote.volumeControl == VolumeProvider.VOLUME_CONTROL_FIXED || remote.maxVolume <= 0) return null
            return Volume(remote.currentVolume, remote.maxVolume)
        }
        if (audio.isVolumeFixed) return null
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max <= 0) null else Volume(audio.getStreamVolume(AudioManager.STREAM_MUSIC), max)
    }

    fun setVolume(level: Int) {
        if (demo) {
            demoVolume = level.coerceIn(0, 15)
            return
        }
        val c = current
        if (c != null && remoteInfo() != null) {
            c.setVolumeTo(level, 0)
        } else {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0) }
        }
    }

    fun extra(a: TrackAction) {
        current?.transportControls?.sendCustomAction(a.id, null)
    }

    fun open(from: Context): Boolean {
        val c = current
        val pi = c?.sessionActivity
        if (pi != null) {
            val opts = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else {
                null
            }
            val sent = runCatching { pi.send(from, 0, null, null, null, null, opts); true }.getOrDefault(false)
            if (sent) return true
        }
        return launch(from, c?.packageName ?: _track.value?.pkg ?: return false)
    }

    fun launch(from: Context, pkg: String): Boolean {
        val i = app.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { from.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }

    fun apps(): List<MediaApp> {
        val pm = app.packageManager
        val found = listOf("android.media.browse.MediaBrowserService", "androidx.media3.session.MediaSessionService")
            .flatMap { a -> runCatching { pm.queryIntentServices(Intent(a), 0) }.getOrDefault(emptyList()).map { it.serviceInfo.packageName } }
            .distinct()
            .filter { it != app.packageName && pm.getLaunchIntentForPackage(it) != null }
        return found
            .sortedBy { PREFERRED.indexOf(it).let { i -> if (i < 0) PREFERRED.size else i } }
            .take(4)
            .map { pkg -> appInfo(pkg).let { (label, icon) -> MediaApp(pkg, label, icon) } }
    }

    fun openAccess(from: Context) {
        val detail = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString())
        } else {
            null
        }
        for (i in listOfNotNull(detail, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
            if (runCatching { from.startActivity(i); true }.getOrDefault(false)) return
        }
    }

    fun demo(playing: Boolean) {
        demo = true
        detach()
        _access.value = true
        val spotify = appInfo("com.spotify.music")
        _track.value = Track(
            pkg = "com.spotify.music",
            app = spotify.first,
            appIcon = spotify.second,
            title = "Pixel Groove",
            artist = "Guaxinim e os Modelos",
            art = demoCover(),
            playing = playing,
            position = 83_000L,
            positionAt = SystemClock.elapsedRealtime(),
            speed = 1f,
            duration = 214_000L,
            canSeek = true,
            canPrev = true,
            canNext = true,
        )
    }

    private fun demoCover(): Bitmap {
        val u = 64
        val size = u * 11
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), 0xFFB9A6F2.toInt(), 0xFFD77757.toInt(), Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bg)
        val ink = Paint().apply { color = 0xFF16130F.toInt(); isAntiAlias = false }
        val top = u * 5 / 2
        NOTE_PIXELS.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                if (ch == '#') canvas.drawRect(((c + 2) * u).toFloat(), (top + r * u).toFloat(), ((c + 3) * u).toFloat(), (top + (r + 1) * u).toFloat(), ink)
            }
        }
        return bmp
    }
}

val NOTE_PIXELS = listOf(
    "..#####",
    "..#...#",
    "..#...#",
    "..#...#",
    "###.###",
    "###.###",
)
