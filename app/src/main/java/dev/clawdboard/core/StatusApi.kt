package dev.clawdboard.core

import org.json.JSONArray
import org.json.JSONObject

object StatusApi {
    const val URL = "https://status.claude.com/api/v2/incidents/unresolved.json"

    fun fetch(): StatusSnapshot? = try {
        val r = httpRequest(URL, headers = mapOf("Accept" to "application/json"))
        if (r.code == 200) parse(r.body, System.currentTimeMillis()) else null
    } catch (e: Exception) {
        null
    }

    fun parse(body: String, now: Long): StatusSnapshot {
        val arr = JSONObject(body).optJSONArray("incidents") ?: JSONArray()
        val down = linkedSetOf<String>()
        val list = mutableListOf<Incident>()
        for (i in 0 until arr.length()) {
            val inc = arr.optJSONObject(i) ?: continue
            val text = inc.toString().lowercase()
            MODELS.forEach { m -> if (text.contains(m.lowercase())) down += m }
            val last = inc.optJSONArray("incident_updates")?.optJSONObject(0)
            list += Incident(
                inc.str("name") ?: "Incidente",
                inc.str("impact") ?: "",
                inc.str("status") ?: "",
                inc.str("shortlink"),
                last?.str("body"),
                parseIsoMillis(last?.str("created_at") ?: inc.str("updated_at")),
            )
        }
        return StatusSnapshot(down, list, now)
    }
}
