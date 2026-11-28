package com.ps4.captures

import android.net.Network
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// ───────────── طلب التثبيت لأداة الـ PS4 ─────────────
data class InstallResult(val code: Int, val body: String)

/** يرسل رابط PKG لأداة التثبيت عن بعد على الـ PS4 (Remote Package Installer) */
fun postInstall(ip: String, port: Int, url: String): InstallResult {
    val body = JSONObject()
        .put("type", "direct")
        .put("packages", JSONArray().put(url))
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

// ───────────── السيرفر الوسيط ─────────────
data class OriginInfo(val url: String, val size: Long, val name: String, val ranges: Boolean)

/** شبكة مصدر (واي فاي أو داتا) مع عدّادات للتوزيع الديناميكي */
class NetSlot(val net: Network?, val label: String) {
    @Volatile var url: String = ""
    @Volatile var inflight: Int = 0
    @Volatile var fails: Int = 0
    @Volatile var dead: Boolean = false
    @Volatile var lastRefreshAt: Long = 0L
    val bytes = AtomicLong(0)
}

private class HttpFailException(
    val code: Int,
    val retryAfterMs: Long,
    message: String
) : IOException(message)

/**
 * سيرفر HTTP صغير على التلفون: الـ PS4 يطلب منه الملف، والتلفون يسحبه من الإنترنت
 * بعدة اتصالات متوازية ثابتة (ومن أكثر من شبكة) مع تحكّم تكيّفي بالسرعة وتجديد تلقائي للروابط.
 */
class ProxyServer(
    private val conns: Int,
    private val slots: List<NetSlot>,
    private val allowedIp: String
) {
    private val fetchPool: ExecutorService = Executors.newFixedThreadPool(conns)
    private val handlerPool: ExecutorService = Executors.newCachedThreadPool()

    @Volatile private var stopped = false
    @Volatile private var originalUrl: String = ""
    private var server: ServerSocket? = null

    val served = AtomicLong(0)
    val requests = AtomicLong(0)

    @Volatile var lastError: String = ""
    @Volatile var origin: OriginInfo? = null

    private val effectiveMax = AtomicInteger(conns)
    private val activeFetchers = AtomicInteger(0)
    private val okStreak = AtomicInteger(0)
    private val throttleLock = Object()

    val total: Long get() = origin?.size ?: 0L

    fun liveLabels(): String {
        val nets = slots.filter { !it.dead }.joinToString(" + ") { it.label }
        return "$nets (${effectiveMax.get()})"
    }

    // ───── فحص الرابط ─────
    fun prepare(url: String): OriginInfo {
        originalUrl = url
        var info: OriginInfo? = null
        var lastErr: Exception? = null
        for (slot in slots) {
            try {
                val i = probe(url, slot)
                if (info == null) info = i
            } catch (e: Exception) {
                slot.dead = true
                if (lastErr == null) lastErr = e
            }
        }
        if (info == null) throw IOException(lastErr?.message ?: "تعذّر فحص الرابط")
        origin = info
        return info
    }

    private fun open(url: String, net: Network?, method: String = "GET"): HttpURLConnection {
        val u = URL(url)
        val c = (if (net != null) net.openConnection(u) else u.openConnection()) as HttpURLConnection
        c.instanceFollowRedirects = false
        c.requestMethod = method
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        )
        c.setRequestProperty("Accept-Encoding", "identity")
        return c
    }

    /** طلب مع تتبع التحويلات يدويًا (يشمل http ← https) */
    private fun requestFollowing(
        url: String,
        net: Network?,
        range: String?,
        method: String = "GET"
    ): Pair<HttpURLConnection, String> {
        var cur = url
        for (hop in 0 until 8) {
            val c = open(cur, net, method)
            if (range != null) c.setRequestProperty("Range", range)
            val code = c.responseCode
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                val loc = c.getHeaderField("Location") ?: run {
                    c.disconnect()
                    throw IOException("تحويل بدون عنوان")
                }
                c.disconnect()
                cur = URL(URL(cur), loc).toString()
            } else {
                return Pair(c, cur)
            }
        }
        throw IOException("تحويلات كثيرة")
    }

    private fun probe(url: String, slot: NetSlot): OriginInfo {
        var (c, finalUrl) = requestFollowing(url, slot.net, "bytes=0-0")
        try {
            var code = c.responseCode
            val ctype = (c.contentType ?: "").lowercase(Locale.US)
            if ("text/html" in ctype) {
                throw IOException("الرابط يرجع صفحة ويب (HTML) وليس ملف .pkg مباشر")
            }

            if (code == 400 || code == 405 || code == 416) {
                c.disconnect()
                val headRes = requestFollowing(url, slot.net, null, method = "HEAD")
                c = headRes.first
                finalUrl = headRes.second
                code = c.responseCode
            }

            var size = -1L
            var ranges = false
            if (code == 206) {
                val cr = c.getHeaderField("Content-Range") ?: ""
                size = cr.substringAfterLast('/').trim().toLongOrNull() ?: -1L
                ranges = size > 0
            } else if (code in 200..299) {
                size = c.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                ranges = c.getHeaderField("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
            } else {
                throw IOException("رد السيرفر: HTTP $code (${slot.label})")
            }

            if (size <= 0) throw IOException("حجم الملف غير معروف")
            slot.url = finalUrl
            slot.lastRefreshAt = System.currentTimeMillis()
            val disp = c.getHeaderField("Content-Disposition") ?: ""
            val fn = Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""", RegexOption.IGNORE_CASE)
                .find(disp)?.groupValues?.get(1)
            val raw = fn ?: URL(finalUrl).path.substringAfterLast('/')
            return OriginInfo(finalUrl, size, safeName(raw), ranges)
        } finally {
            c.disconnect()
        }
    }

    private fun refreshSlotUrl(slot: NetSlot) {
        val base = originalUrl
        if (base.isEmpty()) return
        synchronized(slot) {
            val now = System.currentTimeMillis()
            if (now - slot.lastRefreshAt < 5000L) return
            val (c, finalUrl) = requestFollowing(base, slot.net, "bytes=0-0")
            try {
                val code = c.responseCode
                if (code == 206 || code in 200..299) {
                    slot.url = finalUrl
                    slot.lastRefreshAt = System.currentTimeMillis()
                }
            } finally {
                c.disconnect()
            }
        }
    }

    private fun safeName(raw: String): String {
        val dec = try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }
        var n = dec.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(80)
        if (n.isEmpty()) n = "game"
        if (!n.endsWith(".pkg", ignoreCase = true)) n += ".pkg"
        return n
    }

    // ───── تشغيل/إيقاف ─────
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
        synchronized(throttleLock) { throttleLock.notifyAll() }
        try {
            server?.close()
        } catch (_: Exception) {
        }
        fetchPool.shutdownNow()
        handlerPool.shutdownNow()
        blocks.clear()
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
            sock.sendBufferSize = 1 shl 20
            sock.soTimeout = 120000
            val ins = sock.getInputStream().buffered(16 * 1024)
            val out = sock.getOutputStream()
            while (!stopped) {
                val reqLine = readLine(ins) ?: break
                if (reqLine.isEmpty()) continue
                val parts = reqLine.split(" ")
                if (parts.size < 2) break
                val method = parts[0].uppercase(Locale.US)
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
                if (!respond(method, headers, out)) break
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

    private fun respond(method: String, h: Map<String, String>, out: OutputStream): Boolean {
        val o = origin
        if (o == null) {
            writeStatus(out, "503 Service Unavailable")
            return false
        }
        val size = o.size
        var start = 0L
        var end = size - 1
        var partial = false
        val range = h["range"]
        if (range != null && o.ranges) {
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
        sb.append("Accept-Ranges: ").append(if (o.ranges) "bytes" else "none").append("\r\n")
        if (partial) sb.append("Content-Range: bytes $start-$end/$size\r\n")
        sb.append("Content-Length: $len\r\n")
        sb.append("Connection: keep-alive\r\n\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        if (method == "HEAD") {
            out.flush()
            return true
        }
        if (o.ranges) serveRange(start, end, out) else streamSingle(out)
        out.flush()
        return true
    }

    // ───── السحب المتوازي مع تحكّم تكيّفي ─────
    private fun acquireThrottlePermit() {
        synchronized(throttleLock) {
            while (!stopped && activeFetchers.get() >= effectiveMax.get()) {
                throttleLock.wait(200)
            }
            if (stopped) throw IOException("تم إيقاف السيرفر")
            activeFetchers.incrementAndGet()
        }
    }

    private fun releaseThrottlePermit() {
        activeFetchers.decrementAndGet()
        synchronized(throttleLock) { throttleLock.notifyAll() }
    }

    private fun onSuccess() {
        val streak = okStreak.incrementAndGet()
        val cur = effectiveMax.get()
        if (cur < conns && streak >= 16) {
            effectiveMax.compareAndSet(cur, minOf(conns, cur + 2))
            okStreak.set(0)
        }
    }

    private fun onThrottle() {
        okStreak.set(0)
        val cur = effectiveMax.get()
        if (cur > 4) {
            effectiveMax.compareAndSet(cur, maxOf(4, (cur * 0.7).toInt()))
        }
    }

    private fun pickSlot(exclude: NetSlot?): NetSlot? {
        synchronized(slots) {
            val alive = slots.filter { !it.dead }
            var best: NetSlot? = null
            for (sl in alive) {
                if (sl === exclude && alive.size > 1) continue
                if (best == null || sl.inflight < best.inflight) best = sl
            }
            if (best == null) best = alive.firstOrNull()
            if (best != null) best.inflight = best.inflight + 1
            return best
        }
    }

    private fun fetchChunk(s: Long, e: Long): ByteArray {
        val want = (e - s + 1).toInt()
        var attempt = 0
        var last: NetSlot? = null
        var refreshedOnce = false
        while (true) {
            if (stopped) throw IOException("تم إيقاف السيرفر")
            acquireThrottlePermit()
            val slot = try {
                pickSlot(last) ?: throw IOException("لا توجد شبكة متاحة")
            } catch (ex: Throwable) {
                releaseThrottlePermit()
                throw ex
            }
            var conn: HttpURLConnection? = null
            try {
                val (c, _) = requestFollowing(slot.url, slot.net, "bytes=$s-$e")
                conn = c
                val code = c.responseCode
                if (code == 429 || code == 503) {
                    val ra = c.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
                    val waitMs = if (ra != null && ra > 0) minOf(ra * 1000L, 8000L) else 1200L
                    throw HttpFailException(code, waitMs, "HTTP $code")
                }
                if ((code == 401 || code == 403 || code == 410) && !refreshedOnce) {
                    refreshedOnce = true
                    c.disconnect()
                    conn = null
                    refreshSlotUrl(slot)
                    continue
                }
                if (code != 206) throw HttpFailException(code, 0L, "HTTP $code")

                val buf = ByteArray(want)
                var off = 0
                c.inputStream.use { ins ->
                    while (off < want && !stopped) {
                        val n = ins.read(buf, off, want - off)
                        if (n < 0) break
                        off += n
                    }
                }
                if (stopped) throw IOException("تم إيقاف السيرفر")
                if (off < want) throw IOException("انقطاع أثناء السحب")
                slot.bytes.addAndGet(want.toLong())
                slot.fails = 0
                onSuccess()
                lastError = ""
                return buf
            } catch (ex: IOException) {
                try {
                    conn?.disconnect()
                } catch (_: Exception) {
                }
                slot.fails = slot.fails + 1
                if (slot.fails >= 4 && slots.count { !it.dead } > 1) slot.dead = true
                if (ex is HttpFailException && (ex.code == 429 || ex.code == 503)) {
                    onThrottle()
                } else if (attempt >= 1) {
                    onThrottle()
                }
                attempt++
                last = slot
                if (attempt >= 8 || stopped) throw ex
                val sleepMs = if (ex is HttpFailException && ex.retryAfterMs > 0) {
                    ex.retryAfterMs
                } else {
                    250L * minOf(attempt, 6)
                }
                Thread.sleep(sleepMs)
            } finally {
                synchronized(slots) { slot.inflight = maxOf(0, slot.inflight - 1) }
                releaseThrottlePermit()
            }
        }
    }

    // ───── كاش الكتل + سحب مسبق ذكي (read-ahead) ─────
    private val blockSize = 2 * 1024 * 1024
    private val maxBlocks = 48
    private val ahead = minOf(conns, 16)

    private class Block(val idx: Long) {
        val fut = CompletableFuture<ByteArray>()

        @Volatile
        var touched: Long = System.nanoTime()
    }

    private val blocks = ConcurrentHashMap<Long, Block>()

    private fun getBlock(idx: Long): Block {
        var b = blocks[idx]
        if (b == null) {
            val nb = Block(idx)
            val prev = blocks.putIfAbsent(idx, nb)
            if (prev == null) {
                startFetch(nb)
                b = nb
            } else {
                b = prev
            }
        }
        b.touched = System.nanoTime()
        return b
    }

    private fun startFetch(b: Block) {
        val size = origin?.size ?: 0L
        val s = b.idx * blockSize
        val e = minOf(s + blockSize - 1, size - 1)
        try {
            fetchPool.execute {
                try {
                    b.fut.complete(fetchChunk(s, e))
                } catch (t: Throwable) {
                    if (!stopped) lastError = t.message ?: t.javaClass.simpleName
                    blocks.remove(b.idx, b)
                    b.fut.completeExceptionally(t)
                }
            }
        } catch (ex: Exception) {
            blocks.remove(b.idx, b)
            b.fut.completeExceptionally(ex)
        }
    }

    private fun trim() {
        if (blocks.size <= maxBlocks) return
        synchronized(blocks) {
            var excess = blocks.size - maxBlocks
            val done = blocks.values.filter { it.fut.isDone }.sortedBy { it.touched }
            for (d in done) {
                if (excess <= 0) break
                blocks.remove(d.idx, d)
                excess--
            }
        }
    }

    private fun serveRange(start: Long, end: Long, out: OutputStream) {
        val size = origin?.size ?: throw IOException("لا يوجد ملف")
        val lastBlock = (size - 1) / blockSize
        var pos = start
        while (pos <= end && !stopped) {
            val idx = pos / blockSize
            val blk = getBlock(idx)
            if (end - pos + 1 > 256L * 1024) {
                val lookAhead = minOf(ahead, effectiveMax.get())
                var k = 1L
                while (k <= lookAhead && idx + k <= lastBlock) {
                    getBlock(idx + k)
                    k++
                }
            }
            val data = try {
                blk.fut.get()
            } catch (ex: Exception) {
                throw IOException(ex.cause?.message ?: ex.message ?: "فشل السحب")
            }
            val blockStart = idx * blockSize
            val from = (pos - blockStart).toInt()
            val lastByte = minOf(end, blockStart + data.size - 1)
            val to = (lastByte - blockStart).toInt()
            out.write(data, from, to - from + 1)
            served.addAndGet((to - from + 1).toLong())
            pos = lastByte + 1
            if (lastByte == blockStart + data.size - 1) blocks.remove(idx, blk)
            trim()
        }
    }

    /** احتياطي: السيرفر الأصلي ما يدعم Range، نمرر الملف باتصال واحد */
    private fun streamSingle(out: OutputStream) {
        val slot = slots.firstOrNull { !it.dead } ?: throw IOException("لا توجد شبكة متاحة")
        val (c, _) = requestFollowing(slot.url, slot.net, null)
        try {
            c.inputStream.use { ins ->
                val buf = ByteArray(256 * 1024)
                while (!stopped) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    served.addAndGet(n.toLong())
                }
            }
        } finally {
            c.disconnect()
        }
    }
}