package com.ps4.captures

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

// ───────────── طلب التثبيت لأداة الـ PS4 ─────────────
data class InstallResult(val code: Int, val body: String)

/** يرسل قائمة روابط PKG لأداة التثبيت عن بعد على الـ PS4 (Remote Package Installer) */
fun postInstall(ip: String, port: Int, urls: List<String>): InstallResult {
    val body = JSONObject()
        .put("type", "direct")
        .put("packages", JSONArray(urls))
        .toString()
    val conn = URL("http://$ip:$port/api/install").openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 4000
        conn.readTimeout = 120000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return InstallResult(code, text.take(400))
    } finally {
        conn.disconnect()
    }
}

/** IP التلفون على نفس شبكة الـ PS4 */
fun localIpFor(ps4Ip: String): String? {
    val prefix = ps4Ip.substringBeforeLast('.') + "."
    var fallback: String? = null
    val ifs = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
    for (ni in ifs) {
        if (!ni.isUp || ni.isLoopback) continue
        for (a in ni.inetAddresses.toList()) {
            if (a is Inet4Address && a.isSiteLocalAddress) {
                val h = a.hostAddress ?: continue
                if (h.startsWith(prefix)) return h
                if (fallback == null) fallback = h
            }
        }
    }
    return fallback
}

// ───────────── سيرفر النقل المحلي من ذاكرة التلفون ─────────────
data class LocalPkgEntry(val uri: Uri, val name: String, val size: Long)

data class FileProgress(val name: String, val size: Long, val sent: Long)

private class ServedFile(
    val displayName: String,
    val safeName: String,
    val size: Long,
    val pfd: ParcelFileDescriptor,
    val channel: FileChannel
) {
    val sent = AtomicLong(0L)
}

/**
 * سيرفر HTTP محلي فائق السرعة: يقرأ ملفات .pkg المختارة من ذاكرة التلفون مباشرة
 * عبر FileChannel (دعم فوري لـ Range بدون استهلاك إنترنت) ويرسلها للـ PS4.
 */
class LocalPkgServer(
    private val ctx: Context,
    private val entries: List<LocalPkgEntry>,
    private val allowedIp: String
) {
    private val handlerPool: ExecutorService = Executors.newCachedThreadPool()
    private val files = ArrayList<ServedFile>(entries.size)

    @Volatile private var stopped = false
    @Volatile var lastError: String = ""
    private var server: ServerSocket? = null

    val served = AtomicLong(0L)
    val requests = AtomicLong(0L)

    fun prepare() {
        closeFiles()
        try {
            for (entry in entries) {
                val pfd = ctx.contentResolver.openFileDescriptor(entry.uri, "r")
                    ?: throw IOException("تعذّر فتح الملف: ${entry.name}")
                val ch = FileInputStream(pfd.fileDescriptor).channel
                val realSize = when {
                    ch.size() > 0L -> ch.size()
                    pfd.statSize > 0L -> pfd.statSize
                    else -> entry.size
                }
                if (realSize <= 0L) {
                    try { ch.close() } catch (_: Exception) {}
                    try { pfd.close() } catch (_: Exception) {}
                    throw IOException("حجم الملف غير صالح: ${entry.name}")
                }
                // نتأكد أن القراءة العشوائية (Range) شغالة على هذا الملف
                ch.read(ByteBuffer.allocate(1), 0L)
                files.add(
                    ServedFile(
                        displayName = entry.name,
                        safeName = safeName(entry.name),
                        size = realSize,
                        pfd = pfd,
                        channel = ch
                    )
                )
            }
        } catch (t: Throwable) {
            closeFiles()
            throw t
        }
    }

    fun totalSize(): Long = files.sumOf { it.size }

    fun fileProgress(): List<FileProgress> =
        files.map { FileProgress(it.displayName, it.size, it.sent.get()) }

    fun urls(phoneIp: String, port: Int): List<String> =
        files.mapIndexed { idx, f -> "http://$phoneIp:$port/$idx/${f.safeName}" }

    private fun safeName(raw: String): String {
        var n = raw.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(80)
        if (n.isEmpty()) n = "game"
        if (!n.endsWith(".pkg", ignoreCase = true)) n += ".pkg"
        return n
    }

    fun start(preferredPort: Int): Int {
        val ss = try {
            ServerSocket(preferredPort, 64)
        } catch (_: IOException) {
            ServerSocket(0, 64)
        }
        server = ss
        handlerPool.execute { acceptLoop(ss) }
        return ss.localPort
    }

    fun stop() {
        stopped = true
        try {
            server?.close()
        } catch (_: Exception) {
        }
        handlerPool.shutdownNow()
        closeFiles()
    }

    private fun closeFiles() {
        for (f in files) {
            try { f.channel.close() } catch (_: Exception) {}
            try { f.pfd.close() } catch (_: Exception) {}
        }
        files.clear()
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!stopped) {
            val s = try {
                ss.accept()
            } catch (_: IOException) {
                break
            }
            if (s.inetAddress?.hostAddress != allowedIp) {
                try {
                    s.close()
                } catch (_: Exception) {
                }
                continue
            }
            handlerPool.execute { handle(s) }
        }
    }

    private fun readLine(ins: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = ins.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 8192) throw IOException("سطر طويل")
        }
    }

    private fun handle(sock: Socket) {
        try {
            sock.tcpNoDelay = true
            sock.sendBufferSize = 2 shl 20
            sock.soTimeout = 120000
            val ins = sock.getInputStream().buffered(16 * 1024)
            val out = BufferedOutputStream(sock.getOutputStream(), 512 * 1024)
            while (!stopped) {
                val reqLine = readLine(ins) ?: break
                if (reqLine.isEmpty()) continue
                val parts = reqLine.split(" ")
                if (parts.size < 2) break
                val method = parts[0].uppercase(Locale.US)
                val rawPath = parts[1]
                val headers = HashMap<String, String>()
                while (true) {
                    val l = readLine(ins) ?: return
                    if (l.isEmpty()) break
                    val idx = l.indexOf(':')
                    if (idx > 0) {
                        headers[l.substring(0, idx).trim().lowercase(Locale.US)] = l.substring(idx + 1).trim()
                    }
                }
                requests.incrementAndGet()
                if (!respond(method, rawPath, headers, out)) break
            }
        } catch (e: Exception) {
            if (!stopped) lastError = e.message ?: e.javaClass.simpleName
        } finally {
            try {
                sock.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeStatus(out: OutputStream, line: String, extra: String = "") {
        val s = "HTTP/1.1 $line\r\n$extra" + "Content-Length: 0\r\nConnection: close\r\n\r\n"
        out.write(s.toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private fun respond(
        method: String,
        rawPath: String,
        h: Map<String, String>,
        out: OutputStream
    ): Boolean {
        val cleanPath = rawPath.substringBefore('?').trimStart('/')
        val idx = cleanPath.substringBefore('/').toIntOrNull()
        val f = if (idx != null && idx in files.indices) files[idx] else null
        if (f == null) {
            writeStatus(out, "404 Not Found")
            return false
        }

        val size = f.size
        var start = 0L
        var end = size - 1
        var partial = false
        val range = h["range"]
        if (range != null) {
            val m = Regex("""bytes=(\d*)-(\d*)""").find(range)
            if (m != null) {
                val a = m.groupValues[1]
                val b = m.groupValues[2]
                if (a.isEmpty() && b.isNotEmpty()) {
                    start = maxOf(0L, size - b.toLong())
                } else if (a.isNotEmpty()) {
                    start = a.toLong()
                    if (b.isNotEmpty()) end = minOf(b.toLong(), size - 1)
                }
                partial = true
            }
        }
        if (start >= size || start > end) {
            writeStatus(out, "416 Range Not Satisfiable", "Content-Range: bytes */$size\r\n")
            return false
        }

        val len = end - start + 1
        val sb = StringBuilder()
        sb.append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
        sb.append("Content-Type: application/octet-stream\r\n")
        sb.append("Accept-Ranges: bytes\r\n")
        if (partial) sb.append("Content-Range: bytes $start-$end/$size\r\n")
        sb.append("Content-Length: $len\r\n")
        sb.append("Connection: keep-alive\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))

        if (method == "HEAD") {
            out.flush()
            return true
        }

        var pos = start
        var remaining = len
        val buf = ByteArray(256 * 1024)
        while (remaining > 0 && !stopped) {
            val want = minOf(buf.size.toLong(), remaining).toInt()
            val bb = ByteBuffer.wrap(buf, 0, want)
            var read = 0
            while (bb.hasRemaining()) {
                val n = f.channel.read(bb, pos + read)
                if (n <= 0) break
                read += n
            }
            if (read <= 0) throw IOException("تعذّرت قراءة الملف أثناء النقل")
            out.write(buf, 0, read)
            pos += read
            remaining -= read
            f.sent.addAndGet(read.toLong())
            served.addAndGet(read.toLong())
        }
        out.flush()
        return true
    }
}