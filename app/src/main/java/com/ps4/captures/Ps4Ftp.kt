package com.ps4.captures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/** المنافذ المعروفة لسيرفر FTP على PS4 المهكر */
val KNOWN_PORTS = listOf(2121, 1337, 21, 2122, 1338, 2020, 9090)

private val ROOTS = listOf("/user/av_contents", "/user/home")
private val PHOTO_EXT = listOf(".jpg", ".jpeg", ".png", ".webp")
private val VIDEO_EXT = listOf(".mp4", ".webm")
private val NAME_DATE = Regex("""^(\d{8}_\d{6})""")

data class Capture(
    val path: String,
    val name: String,
    val size: Long,
    val time: Long,
    val isVideo: Boolean,
    val root: String
) {
    /** مسار الصورة المصغرة التي ينشئها الـ PS4 نفسه */
    val thumbPath: String
        get() = root + "/thumbnails" + path.removePrefix(root) + ".jpg"
}

private fun parseNameTime(name: String): Long? {
    val m = NAME_DATE.find(name) ?: return null
    return try {
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(m.groupValues[1])?.time
    } catch (e: Exception) {
        null
    }
}

fun openClient(ip: String, port: Int, timeoutMs: Int = 4000): FTPClient {
    val c = FTPClient()
    try {
        c.setConnectTimeout(timeoutMs)
        c.connect(ip, port)
        c.setSoTimeout(20000)
        c.setDataTimeout(30000)
        if (!FTPReply.isPositiveCompletion(c.replyCode)) throw IOException("bad reply")
        if (!c.login("anonymous", "anonymous")) throw IOException("login failed")
        c.enterLocalPassiveMode()
        c.setFileType(FTP.BINARY_FILE_TYPE)
        c.setBufferSize(256 * 1024)
        return c
    } catch (e: Exception) {
        try {
            c.disconnect()
        } catch (_: Exception) {
        }
        throw e
    }
}

/** يجرب كل المنافذ المعروفة ويرجع جلسة على أول منفذ ينجح */
suspend fun autoConnect(ip: String, onTry: (Int) -> Unit): Ps4Session =
    withContext(Dispatchers.IO) {
        var firstError: Exception? = null
        for (p in KNOWN_PORTS) {
            onTry(p)
            try {
                return@withContext Ps4Session(ip, p, openClient(ip, p, 3500))
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }
        throw IOException(firstError?.message ?: "no port")
    }

/** الجهاز رفض فتح الملف (مثلاً 550) */
class FtpDenied(message: String) : IOException(message)

class Ps4Session(val ip: String, val port: Int, initial: FTPClient) {
    private val mutex = Mutex()
    private var shared: FTPClient? = initial

    private fun ensure(): FTPClient {
        val c = shared
        if (c != null && c.isConnected) {
            try {
                c.sendNoOp()
                return c
            } catch (_: Exception) {
                try {
                    c.disconnect()
                } catch (_: Exception) {
                }
            }
        }
        val n = openClient(ip, port, 5000)
        shared = n
        return n
    }

    private suspend fun <T> useShared(block: (FTPClient) -> T): T =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    block(ensure())
                } catch (e: Exception) {
                    try {
                        shared?.disconnect()
                    } catch (_: Exception) {
                    }
                    shared = null
                    throw e
                }
            }
        }

    suspend fun scan(): List<Capture> = useShared { c ->
        var result: List<Capture> = emptyList()
        for (root in ROOTS) {
            val out = ArrayList<Capture>()
            walk(c, root, root, 0, out)
            if (out.isNotEmpty()) {
                result = out.sortedWith(
                    compareByDescending<Capture> { it.time }.thenByDescending { it.name }
                )
                break
            }
        }
        result
    }

    private fun listDir(c: FTPClient, dir: String): Array<FTPFile> {
        val m: Array<FTPFile>? = try {
            c.mlistDir(dir)
        } catch (e: Exception) {
            null
        }
        if (m != null && m.isNotEmpty()) return m
        return try {
            c.listFiles(dir)
        } catch (e: Exception) {
            emptyArray()
        }
    }

    private fun walk(c: FTPClient, root: String, dir: String, depth: Int, out: MutableList<Capture>) {
        if (depth > 8) return
        for (f in listDir(c, dir)) {
            val name = (f.name ?: continue).substringAfterLast('/')
            if (name.isEmpty() || name == "." || name == "..") continue
            val full = dir.trimEnd('/') + "/" + name
            if (full == dir) continue
            if (f.isDirectory) {
                if (name.equals("thumbnails", ignoreCase = true)) continue
                walk(c, root, full, depth + 1, out)
            } else {
                val lower = name.lowercase()
                val isVideo = VIDEO_EXT.any { lower.endsWith(it) }
                val isPhoto = PHOTO_EXT.any { lower.endsWith(it) }
                if (isVideo || isPhoto) {
                    val t = parseNameTime(name) ?: f.timestamp?.timeInMillis ?: 0L
                    out.add(Capture(full, name, f.size, t, isVideo, root))
                }
            }
        }
    }

    /** تحميل ملف صغير إلى الذاكرة (صور/مصغرات) */
    suspend fun bytes(path: String): ByteArray? = useShared { c ->
        val bo = ByteArrayOutputStream()
        if (c.retrieveFile(path, bo)) bo.toByteArray() else null
    }

    /** تحميل ملف كبير: نفس اتصال FTP، مع إعادة محاولة واستئناف عند الانقطاع */
    suspend fun download(
        path: String,
        size: Long,
        out: OutputStream,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            var total = 0L
            var attempt = 0
            while (true) {
                try {
                    val c = ensure()
                    if (total > 0) c.setRestartOffset(total)
                    val input = c.retrieveFileStream(path)
                        ?: throw FtpDenied("رد الجهاز: " + c.replyString.trim())
                    input.use { ins ->
                        val buf = ByteArray(128 * 1024)
                        var lastReport = total
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            total += n
                            if (size > 0 && total - lastReport >= 256 * 1024) {
                                lastReport = total
                                onProgress((total.toFloat() / size).coerceIn(0f, 1f))
                            }
                        }
                    }
                    c.completePendingCommand()
                    if (size > 0 && total < size) {
                        throw IOException("انقطع التحميل عند $total من $size بايت")
                    }
                    out.flush()
                    onProgress(1f)
                    break
                } catch (e: FtpDenied) {
                    throw e
                } catch (e: IOException) {
                    try {
                        shared?.disconnect()
                    } catch (_: Exception) {
                    }
                    shared = null
                    attempt++
                    if (attempt >= 4) throw e
                    Thread.sleep(1500)
                }
            }
        }
    }

    fun close() {
        try {
            shared?.disconnect()
        } catch (_: Exception) {
        }
        shared = null
    }
}
