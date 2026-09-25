package dev.clawdboard.core

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

val MODELS = listOf("Haiku", "Sonnet", "Opus", "Fable")

data class UsageWindow(val percent: Double, val resetsAt: Long?)
data class ScopedLimit(val label: String, val percent: Double, val resetsAt: Long?)

data class UsageSnapshot(
    val fiveHour: UsageWindow?,
    val sevenDay: UsageWindow?,
    val scoped: List<ScopedLimit>,
    val source: DataSource,
    val fetchedAt: Long,
) {
    fun toJson(): JSONObject {
        fun w(x: UsageWindow?): Any = x?.let {
            JSONObject().put("percent", it.percent).put("resetsAt", it.resetsAt ?: JSONObject.NULL)
        } ?: JSONObject.NULL
        val sc = JSONArray()
        scoped.forEach {
            sc.put(JSONObject().put("label", it.label).put("percent", it.percent).put("resetsAt", it.resetsAt ?: JSONObject.NULL))
        }
        return JSONObject().put("fiveHour", w(fiveHour)).put("sevenDay", w(sevenDay)).put("scoped", sc)
            .put("source", source.name).put("fetchedAt", fetchedAt)
    }
}

sealed interface FetchResult {
    data class Ok(val snap: UsageSnapshot) : FetchResult
    data class Auth(val code: Int, val message: String) : FetchResult
    data class Limited(val retryAfterSec: Long?, val message: String) : FetchResult
    data class Failed(val message: String) : FetchResult
}

data class Incident(
    val name: String,
    val impact: String,
    val status: String,
    val url: String?,
    val update: String? = null,
    val updatedAt: Long? = null,
)

data class StatusSnapshot(val down: Set<String>, val incidents: List<Incident>, val fetchedAt: Long) {
    fun toJson(): JSONObject {
        val arr = JSONArray()
        incidents.forEach {
            arr.put(
                JSONObject().put("name", it.name).put("impact", it.impact).put("status", it.status).put("url", it.url ?: JSONObject.NULL)
                    .put("update", it.update ?: JSONObject.NULL).put("updatedAt", it.updatedAt ?: JSONObject.NULL)
            )
        }
        return JSONObject().put("down", JSONArray(down.toList())).put("incidents", arr).put("fetchedAt", fetchedAt)
    }
}

data class NewsItem(val title: String, val link: String, val date: Long?, val category: String?)

data class Sample(val t: Long, val p5: Double?, val p7: Double?)

internal fun JSONObject.str(name: String): String? =
    if (isNull(name)) null else optString(name, "").takeIf { it.isNotEmpty() }

internal fun JSONObject.num(name: String): Double? =
    if (isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

internal fun parseIsoMillis(s: String?): Long? =
    s?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
