package dev.clawdboard.core

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object NewsApi {
    const val URL = "https://raw.githubusercontent.com/Olshansk/rss-feeds/main/feeds/feed_anthropic_news.xml"

    fun fetch(): List<NewsItem>? = try {
        val r = httpRequest(URL, timeoutMs = 20_000)
        if (r.code == 200) parse(r.body) else null
    } catch (e: Exception) {
        null
    }

    fun parse(xml: String): List<NewsItem> {
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        val items = mutableListOf<NewsItem>()
        var inItem = false
        var tag: String? = null
        var title = StringBuilder()
        var link = StringBuilder()
        var date = StringBuilder()
        var cat = StringBuilder()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "item") {
                        inItem = true
                        title = StringBuilder(); link = StringBuilder(); date = StringBuilder(); cat = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> if (inItem) when (tag) {
                    "title" -> title.append(p.text)
                    "link" -> link.append(p.text)
                    "pubDate" -> date.append(p.text)
                    "category" -> if (cat.isEmpty()) cat.append(p.text)
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "item" && inItem) {
                        inItem = false
                        val t = title.toString().trim()
                        if (t.isNotEmpty()) {
                            items += NewsItem(t, link.toString().trim(), parseRfc(date.toString().trim()), cat.toString().trim().ifEmpty { null })
                        }
                    }
                    tag = null
                }
            }
            ev = p.next()
        }
        return items.sortedByDescending { it.date ?: 0L }.take(12)
    }

    private fun parseRfc(s: String): Long? = runCatching {
        ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrNull()
}
