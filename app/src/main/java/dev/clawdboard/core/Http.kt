package dev.clawdboard.core

import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

data class HttpResponse(val code: Int, val body: String, val headers: Map<String, String>)

fun httpRequest(
    url: String,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    body: String? = null,
    timeoutMs: Int = 15_000,
): HttpResponse {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.useCaches = false
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code >= 400) conn.errorStream else conn.inputStream
        val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        val hdrs = conn.headerFields.entries
            .filter { it.key != null }
            .associate { it.key.lowercase() to it.value.joinToString(",") }
        return HttpResponse(code, text, hdrs)
    } finally {
        conn.disconnect()
    }
}

fun netMessage(e: Throwable): String = when (e) {
    is UnknownHostException -> "Sem internet (DNS falhou)"
    is SocketTimeoutException -> "Tempo esgotado na conexão"
    is SSLException -> "Falha TLS: ${e.message ?: ""}".trim()
    else -> "${e.javaClass.simpleName}: ${e.message ?: ""}".trim()
}
