package dev.clawdboard.core

import org.json.JSONArray
import org.json.JSONObject

object ClaudeApi {
    const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"
    const val MESSAGES_URL = "https://api.anthropic.com/v1/messages"
    const val PROBE_MODEL = "claude-haiku-4-5-20251001"
    private const val UA = "claude-code/2.1.5"

    private fun baseHeaders(token: String) = mapOf(
        "Authorization" to "Bearer $token",
        "anthropic-beta" to "oauth-2025-04-20",
        "User-Agent" to UA,
        "Accept" to "application/json",
    )

    fun fetchUsage(token: String): FetchResult = try {
        val r = httpRequest(USAGE_URL, headers = baseHeaders(token))
        when (r.code) {
            200 -> FetchResult.Ok(parseUsageJson(r.body, System.currentTimeMillis()))
            401, 403 -> FetchResult.Auth(r.code, errorMessage(r.body) ?: "HTTP ${r.code}")
            429 -> FetchResult.Limited(r.headers["retry-after"]?.toLongOrNull(), "Endpoint de uso limitou as consultas")
            else -> FetchResult.Failed("HTTP ${r.code}: ${errorMessage(r.body) ?: r.body.take(120)}")
        }
    } catch (e: Exception) {
        FetchResult.Failed(netMessage(e))
    }

    fun fetchProbe(token: String): FetchResult = try {
        val body = JSONObject()
            .put("model", PROBE_MODEL)
            .put("max_tokens", 1)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", ".")))
            .toString()
        val headers = baseHeaders(token) + mapOf(
            "anthropic-version" to "2023-06-01",
            "content-type" to "application/json",
        )
        val r = httpRequest(MESSAGES_URL, "POST", headers, body, 20_000)
        lastRateHeaders = r.headers.filterKeys { it.startsWith("anthropic-ratelimit-") }.toSortedMap()
        val snap = parseProbeHeaders(r.headers, System.currentTimeMillis())
        when {
            r.code == 401 || r.code == 403 -> FetchResult.Auth(r.code, errorMessage(r.body) ?: "HTTP ${r.code}")
            snap != null -> FetchResult.Ok(snap)
            r.code == 429 -> FetchResult.Limited(r.headers["retry-after"]?.toLongOrNull(), "API limitou a sondagem")
            else -> FetchResult.Failed("HTTP ${r.code}: ${errorMessage(r.body) ?: "sem headers de uso"}")
        }
    } catch (e: Exception) {
        FetchResult.Failed(netMessage(e))
    }

    fun parseUsageJson(body: String, now: Long): UsageSnapshot {
        val o = JSONObject(body)
        fun win(name: String): UsageWindow? {
            val w = o.optJSONObject(name) ?: return null
            val p = w.num("utilization") ?: return null
            return UsageWindow(p, parseIsoMillis(w.str("resets_at")))
        }
        val scoped = mutableListOf<ScopedLimit>()
        o.optJSONArray("limits")?.let { arr ->
            for (i in 0 until arr.length()) {
                val l = arr.optJSONObject(i) ?: continue
                if (l.str("kind") != "weekly_scoped") continue
                val label = l.optJSONObject("scope")?.optJSONObject("model")?.str("display_name") ?: continue
                val p = l.num("percent") ?: continue
                scoped += ScopedLimit(label, p, parseIsoMillis(l.str("resets_at")))
            }
        }
        if (scoped.isEmpty()) {
            listOf("seven_day_opus" to "Opus", "seven_day_sonnet" to "Sonnet").forEach { (k, label) ->
                win(k)?.let { scoped += ScopedLimit(label, it.percent, it.resetsAt) }
            }
        }
        return UsageSnapshot(win("five_hour"), win("seven_day"), scoped, DataSource.USAGE, now)
    }

    @Volatile var lastRateHeaders: Map<String, String> = emptyMap()
        private set

    private val SCOPED_HEADER = Regex("^anthropic-ratelimit-unified-7d[_-]([a-z]+)-utilization$")

    fun parseProbeHeaders(h: Map<String, String>, now: Long): UsageSnapshot? {
        val u5 = h["anthropic-ratelimit-unified-5h-utilization"]?.toDoubleOrNull()
        val u7 = h["anthropic-ratelimit-unified-7d-utilization"]?.toDoubleOrNull()
        if (u5 == null && u7 == null) return null
        val r5 = h["anthropic-ratelimit-unified-5h-reset"]?.toLongOrNull()?.times(1000)
        val r7 = h["anthropic-ratelimit-unified-7d-reset"]?.toLongOrNull()?.times(1000)
        val scoped = h.keys.sorted().mapNotNull { k ->
            val name = SCOPED_HEADER.find(k)?.groupValues?.get(1) ?: return@mapNotNull null
            val u = h[k]?.toDoubleOrNull() ?: return@mapNotNull null
            val reset = h[k.removeSuffix("-utilization") + "-reset"]?.toLongOrNull()?.times(1000)
            ScopedLimit(name.replaceFirstChar { it.uppercase() }, u * 100.0, reset)
        }
        return UsageSnapshot(
            u5?.let { UsageWindow(it * 100.0, r5) },
            u7?.let { UsageWindow(it * 100.0, r7) },
            scoped, DataSource.PROBE, now,
        )
    }

    fun errorMessage(body: String): String? = runCatching {
        val o = JSONObject(body)
        o.optJSONObject("error")?.str("message") ?: o.str("message")
    }.getOrNull()

    fun tokenFormatProblem(token: String): String? = when {
        token.isBlank() -> "Cole o token"
        token.any { it.isWhitespace() } -> "O token não pode ter espaços ou quebras de linha"
        token.startsWith("sk-ant-api") -> "Isso é uma API key. Use o token do comando claude setup-token (sk-ant-oat...)"
        !token.startsWith("sk-ant-") -> "O token deve começar com sk-ant-oat..."
        token.length < 40 -> "Token curto demais, confira se copiou inteiro"
        else -> null
    }
}
