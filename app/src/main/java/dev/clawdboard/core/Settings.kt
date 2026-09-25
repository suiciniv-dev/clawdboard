package dev.clawdboard.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class DataSource(val label: String) { AUTO("Automático"), USAGE("Endpoint de uso"), PROBE("Sondagem") }
enum class ScreenMode(val label: String) { STATIC("Estático"), MASCOTS("Mascotes"), CAROUSEL("Carrossel"), CLOCK("Relógio") }
enum class Backdrop(val label: String) { STICK("Tom do stick"), BLACK("Preto AMOLED") }
enum class Brightness(val label: String, val level: Float) { SYSTEM("Sistema", -1f), LOW("Baixo", 0.05f), MEDIUM("Médio", 0.35f), HIGH("Alto", 1f) }
enum class Orientation(val label: String) { LANDSCAPE("Paisagem"), PORTRAIT("Retrato"), AUTO("Automática") }
enum class Skin(val label: String) { CLASSIC("Clássico"), MODELS("Por modelo"), CROWNS("Coroas"), XMAS("Natal") }
enum class Tint(val label: String) { CORAL("Coral"), RAINBOW("Arco-íris"), LAVENDER("Lavanda"), MINT("Menta"), BUBBLEGUM("Chiclete") }

data class Prefs(
    val refreshSec: Int = 60,
    val source: DataSource = DataSource.AUTO,
    val mode: ScreenMode = ScreenMode.CAROUSEL,
    val dwellSec: Int = 15,
    val brightness: Brightness = Brightness.SYSTEM,
    val orientation: Orientation = Orientation.LANDSCAPE,
    val pixelShift: Boolean = true,
    val autostart: Boolean = true,
    val panelEnabled: Boolean = true,
    val backdrop: Backdrop = Backdrop.STICK,
    val zoom: Int = 100,
    val skin: Skin = Skin.MODELS,
    val tint: Tint = Tint.CORAL,
    val animations: Boolean = true,
    val music: Boolean = false,
) {
    fun sanitized() = copy(
        refreshSec = refreshSec.coerceIn(REFRESH_OPTIONS.first(), REFRESH_OPTIONS.last()),
        dwellSec = dwellSec.coerceIn(5, 120),
        zoom = zoom.coerceIn(ZOOM_OPTIONS.first(), ZOOM_OPTIONS.last()),
    )

    fun toJson(): JSONObject = JSONObject()
        .put("refreshSec", refreshSec).put("source", source.name).put("mode", mode.name)
        .put("dwellSec", dwellSec).put("brightness", brightness.name).put("orientation", orientation.name)
        .put("pixelShift", pixelShift).put("autostart", autostart).put("panelEnabled", panelEnabled)
        .put("backdrop", backdrop.name).put("zoom", zoom)
        .put("skin", skin.name).put("tint", tint.name).put("animations", animations)
        .put("music", music)

    fun merge(o: JSONObject): Prefs = copy(
        refreshSec = if (o.has("refreshSec")) o.optInt("refreshSec", refreshSec) else refreshSec,
        source = enumOr(o.str("source"), source),
        mode = enumOr(o.str("mode"), mode),
        dwellSec = if (o.has("dwellSec")) o.optInt("dwellSec", dwellSec) else dwellSec,
        brightness = enumOr(o.str("brightness"), brightness),
        orientation = enumOr(o.str("orientation"), orientation),
        pixelShift = if (o.has("pixelShift")) o.optBoolean("pixelShift", pixelShift) else pixelShift,
        autostart = if (o.has("autostart")) o.optBoolean("autostart", autostart) else autostart,
        panelEnabled = if (o.has("panelEnabled")) o.optBoolean("panelEnabled", panelEnabled) else panelEnabled,
        backdrop = enumOr(o.str("backdrop"), backdrop),
        zoom = if (o.has("zoom")) o.optInt("zoom", zoom) else zoom,
        skin = enumOr(o.str("skin"), skin),
        tint = enumOr(o.str("tint"), tint),
        animations = if (o.has("animations")) o.optBoolean("animations", animations) else animations,
        music = if (o.has("music")) o.optBoolean("music", music) else music,
    ).sanitized()

    companion object {
        val REFRESH_OPTIONS = listOf(30, 60, 120, 300, 600)
        val DWELL_OPTIONS = listOf(8, 15, 30, 60)
        val ZOOM_OPTIONS = listOf(90, 100, 115, 130, 150)

        fun optionsJson(): JSONObject {
            fun <T : Enum<T>> e(values: List<T>, label: (T) -> String) =
                JSONArray().apply { values.forEach { put(JSONObject().put("id", it.name).put("label", label(it))) } }
            return JSONObject()
                .put("refreshSec", JSONArray(REFRESH_OPTIONS))
                .put("dwellSec", JSONArray(DWELL_OPTIONS))
                .put("zoom", JSONArray(ZOOM_OPTIONS))
                .put("source", e(DataSource.entries) { it.label })
                .put("mode", e(ScreenMode.entries) { it.label })
                .put("brightness", e(Brightness.entries) { it.label })
                .put("orientation", e(Orientation.entries) { it.label })
                .put("backdrop", e(Backdrop.entries) { it.label })
                .put("skin", e(Skin.entries) { it.label })
                .put("tint", e(Tint.entries) { it.label })
        }
    }
}

inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
    if (name == null) fallback else enumValues<T>().firstOrNull { it.name == name } ?: fallback

class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<Prefs> = _flow.asStateFlow()
    val value: Prefs get() = _flow.value

    private fun read(): Prefs {
        val raw = sp.getString("prefs", null) ?: return Prefs()
        return runCatching { Prefs().merge(JSONObject(raw)) }.getOrDefault(Prefs())
    }

    @Synchronized
    fun update(f: (Prefs) -> Prefs): Prefs {
        val p = f(_flow.value).sanitized()
        sp.edit().putString("prefs", p.toJson().toString()).apply()
        _flow.value = p
        return p
    }

    @Synchronized
    fun clear() {
        sp.edit().clear().apply()
        _flow.value = Prefs()
    }
}
