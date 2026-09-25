package dev.clawdboard.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class History(context: Context) {
    private val file = File(context.filesDir, "history.json")
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<List<Sample>> = _flow.asStateFlow()

    @Synchronized
    fun record(snap: UsageSnapshot) {
        val slot = (snap.fetchedAt / SLOT_MS) * SLOT_MS
        val cutoff = snap.fetchedAt - WINDOW_MS
        val list = _flow.value.filter { it.t >= cutoff && it.t != slot }.toMutableList()
        list += Sample(slot, snap.fiveHour?.percent, snap.sevenDay?.percent)
        list.sortBy { it.t }
        _flow.value = list
        runCatching { save(list) }
    }

    @Synchronized
    fun clear() {
        file.delete()
        _flow.value = emptyList()
    }

    private fun load(): List<Sample> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        val cutoff = System.currentTimeMillis() - WINDOW_MS
        (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONArray(i) ?: return@mapNotNull null
            Sample(a.getLong(0), a.optDouble(1).takeIf { !it.isNaN() }, a.optDouble(2).takeIf { !it.isNaN() })
        }.filter { it.t >= cutoff }.sortedBy { it.t }
    }.getOrDefault(emptyList())

    private fun save(list: List<Sample>) {
        val tmp = File(file.parentFile, "history.json.tmp")
        tmp.writeText(encode(list).toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    fun preview(list: List<Sample>) {
        _flow.value = list
    }

    fun toJson(): JSONArray = encode(_flow.value)

    private fun encode(list: List<Sample>): JSONArray {
        val arr = JSONArray()
        list.forEach { s -> arr.put(JSONArray().put(s.t).put(s.p5 ?: JSONObject.NULL).put(s.p7 ?: JSONObject.NULL)) }
        return arr
    }

    companion object {
        const val SLOT_MS = 30 * 60_000L
        const val WINDOW_MS = 7 * 86_400_000L
    }
}
