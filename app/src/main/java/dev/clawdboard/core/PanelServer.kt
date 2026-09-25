package dev.clawdboard.core

import android.content.Context
import dev.clawdboard.R
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class PanelServer(private val app: Context, private val repo: Repository) {
    @Volatile private var server: ServerSocket? = null
    @Volatile var port: Int = 0
        private set
    val isRunning: Boolean get() = server?.isClosed == false

    private val sessions = ConcurrentHashMap<String, Long>()
    private val pool = Executors.newFixedThreadPool(4)
    private val rng = SecureRandom()
    private val page: ByteArray by lazy { app.assets.open("panel.html").use { it.readBytes() } }
    private val font: ByteArray by lazy { app.resources.openRawResource(R.font.fredoka).use { it.readBytes() } }

    private data class Resp(val code: Int, val type: String, val body: ByteArray, val headers: List<Pair<String, String>> = emptyList())

    @Synchronized
    fun start() {
        if (isRunning) return
        for (p in 8080..8089) {
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress(p))
                server = s
                port = p
                break
            } catch (_: IOException) {
            }
        }
        val s = server ?: return
        thread(name = "clawdboard-panel", isDaemon = true) {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (_: IOException) { break }
                pool.execute { runCatching { handle(c) } }
            }
        }
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        port = 0
        sessions.clear()
    }

    fun invalidateSessions() = sessions.clear()

    private fun handle(sock: Socket) = sock.use { s ->
        s.soTimeout = 15_000
        val input = BufferedInputStream(s.getInputStream())
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0].uppercase()
        val path = parts[1].substringBefore('?')
        val query = parts[1].substringAfter('?', "")
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val len = headers["content-length"]?.toIntOrNull() ?: 0
        if (len > 64 * 1024) {
            write(s, json(413, err("Corpo grande demais")))
            return
        }
        val body = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = input.read(body, read, len - read)
            if (n < 0) break
            read += n
        }
        val resp = try {
            route(method, path, query, headers, String(body, 0, read, Charsets.UTF_8))
        } catch (e: Exception) {
            json(500, err(e.message ?: e.javaClass.simpleName))
        }
        write(s, resp)
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 8192) return null
        }
        return buf.toString("UTF-8")
    }

    private fun write(s: Socket, r: Resp) {
        val out = BufferedOutputStream(s.getOutputStream())
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(r.code).append(' ').append(reason(r.code)).append("\r\n")
        sb.append("Content-Type: ").append(r.type).append("\r\n")
        sb.append("Content-Length: ").append(r.body.size).append("\r\n")
        sb.append("Cache-Control: no-store\r\n")
        sb.append("X-Content-Type-Options: nosniff\r\n")
        sb.append("X-Frame-Options: DENY\r\n")
        sb.append("Referrer-Policy: no-referrer\r\n")
        sb.append("Connection: close\r\n")
        r.headers.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.write(r.body)
        out.flush()
    }

    private fun reason(code: Int) = when (code) {
        200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"
        404 -> "Not Found"; 409 -> "Conflict"; 410 -> "Gone"; 413 -> "Payload Too Large"; else -> "Error"
    }

    private fun json(code: Int, o: JSONObject, headers: List<Pair<String, String>> = emptyList()) =
        Resp(code, "application/json; charset=utf-8", o.toString().toByteArray(Charsets.UTF_8), headers)

    private fun err(msg: String) = JSONObject().put("ok", false).put("error", msg)
    private fun ok() = JSONObject().put("ok", true)

    private fun hostAllowed(host: String?): Boolean {
        val h = host?.substringBeforeLast(':')?.trim('[', ']')?.lowercase() ?: return false
        return h == "localhost" || h.endsWith(".local") || Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(h)
    }

    private fun cookie(headers: Map<String, String>, name: String): String? =
        headers["cookie"]?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')

    private fun sessionValid(id: String?): Boolean {
        if (id == null) return false
        val exp = sessions[id] ?: return false
        if (exp < System.currentTimeMillis()) {
            sessions.remove(id)
            return false
        }
        return true
    }

    private fun newSessionCookie(): Pair<String, String> {
        val bytes = ByteArray(24).also { rng.nextBytes(it) }
        val id = bytes.joinToString("") { "%02x".format(it) }
        sessions[id] = System.currentTimeMillis() + SESSION_MS
        return "Set-Cookie" to "cb_session=$id; Path=/; HttpOnly; SameSite=Strict; Max-Age=${SESSION_MS / 1000}"
    }

    private val clearCookie = "Set-Cookie" to "cb_session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0"

    private fun param(query: String, name: String): String? =
        query.split('&').firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.takeIf { it.isNotEmpty() }

    private fun text(code: Int, body: String) = Resp(code, "text/plain; charset=utf-8", body.toByteArray(Charsets.UTF_8))

    private fun route(method: String, path: String, query: String, headers: Map<String, String>, body: String): Resp {
        if (!hostAllowed(headers["host"])) return json(403, err("Host não permitido"))
        val host = headers["host"].orEmpty()
        if (method == "GET" && (path == "/" || path == "/index.html")) {
            return Resp(
                200, "text/html; charset=utf-8", page,
                listOf("Content-Security-Policy" to "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; img-src 'self' data:"),
            )
        }
        if (method == "GET" && path == "/favicon.ico") return Resp(204, "text/plain", ByteArray(0))
        if (method == "GET" && path == "/fredoka.ttf") return Resp(200, "font/ttf", font)
        if (method == "GET" && path == "/pc/install.ps1") {
            val script = repo.installerFor(host, param(query, "k"))
                ?: return text(403, "Write-Host 'Chave do Clawdboard inválida. Copie o comando de novo no painel.' -ForegroundColor Red")
            return text(200, script)
        }
        if (!path.startsWith("/api/")) return json(404, err("Não encontrado"))
        if (method == "POST" && headers["x-clawdboard"] != "1") return json(403, err("Requisição sem cabeçalho do painel"))

        val sid = cookie(headers, "cb_session")
        val authed = sessionValid(sid)
        val o = if (method == "POST" && body.isNotBlank()) runCatching { JSONObject(body) }.getOrElse { return json(400, err("JSON inválido")) } else JSONObject()

        when ("$method $path") {
            "GET /api/info" -> return json(200, repo.infoJson().put("authenticated", authed))

            "POST /api/push" -> {
                if (!repo.pushKeyValid(headers["x-clawdboard-key"])) return json(401, err("Chave de pareamento inválida"))
                return if (repo.receivePush(o)) json(200, ok()) else json(400, err("Sem rate_limits"))
            }

            "POST /api/setup" -> {
                val r = runBlocking { repo.provision(o.optString("pin")) }
                return when (r) {
                    Repository.Outcome.Ok -> json(200, ok(), listOf(newSessionCookie()))
                    is Repository.Outcome.Error -> json(400, err(r.message))
                    else -> json(400, err("Falha na configuração"))
                }
            }

            "POST /api/login" -> {
                val r = runBlocking { repo.unlock(o.optString("pin")) }
                return when (r) {
                    Repository.Outcome.Ok -> json(200, ok(), listOf(newSessionCookie()))
                    is Repository.Outcome.WrongPin -> json(401, err("PIN incorreto").put("remaining", r.remaining))
                    Repository.Outcome.Wiped -> json(410, err("10 PINs errados: o aparelho foi apagado"))
                    is Repository.Outcome.Error -> json(400, err(r.message))
                }
            }

            "POST /api/logout" -> {
                sid?.let { sessions.remove(it) }
                return json(200, ok(), listOf(clearCookie))
            }
        }

        if (!authed) return json(401, err("Faça login com o PIN").put("needLogin", true))

        return when ("$method $path") {
            "GET /api/state" -> json(200, repo.stateJson(host))

            "POST /api/settings" -> {
                val p = repo.updateSettings { it.merge(o) }
                json(200, ok().put("settings", p.toJson()))
            }

            "POST /api/pair" -> when (val r = runBlocking { repo.newPairKey() }) {
                Repository.Outcome.Ok -> json(200, ok().put("pairCommand", repo.pairCommand(host)))
                is Repository.Outcome.Error -> json(400, err(r.message))
                else -> json(400, err("Falha ao gerar a chave"))
            }

            "POST /api/pin" -> when (val r = runBlocking { repo.changePin(o.optString("pin"), o.optString("newPin")) }) {
                Repository.Outcome.Ok -> json(200, ok())
                is Repository.Outcome.WrongPin -> json(401, err("PIN atual incorreto").put("remaining", r.remaining))
                Repository.Outcome.Wiped -> json(410, err("10 PINs errados: o aparelho foi apagado"))
                is Repository.Outcome.Error -> json(400, err(r.message))
            }

            "POST /api/lock" -> {
                repo.lock()
                json(200, ok(), listOf(clearCookie))
            }

            "POST /api/reset" -> {
                if (o.optString("confirm") != "APAGAR") return json(400, err("Digite APAGAR para confirmar"))
                repo.factoryReset()
                json(200, ok(), listOf(clearCookie))
            }

            else -> json(404, err("Rota desconhecida"))
        }
    }

    companion object {
        private const val SESSION_MS = 12 * 60 * 60_000L
    }
}
