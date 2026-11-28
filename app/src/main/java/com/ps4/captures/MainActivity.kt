package com.ps4.captures

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

// ───────────── الألوان ─────────────
private val Bg = Color(0xFF070B18)
private val CardBg = Color(0xFF151D3B)
private val Accent = Color(0xFF3B82F6)
private val Accent2 = Color(0xFF8B5CF6)
private val Good = Color(0xFF34D399)
private val Bad = Color(0xFFF87171)
private val Amber = Color(0xFFFFB020)
private val TextHi = Color(0xFFF1F5FF)
private val TextLo = Color(0xFF93A0C8)

/** رابط مشارك من تطبيق آخر (مشاركة ← PS4 Captures) */
var pendingShared by mutableStateOf<String?>(null)

val CONN_OPTIONS = listOf(4, 6, 8, 16)

data class ProxyView(
    val name: String = "",
    val served: Long = 0,
    val total: Long = 0,
    val speed: Long = 0,
    val nets: String = "",
    val requests: Long = 0,
    val error: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // نسمح بعدد أكبر من الاتصالات المفتوحة لنفس السيرفر (أسرع للسحب المتوازي)
        System.setProperty("http.maxConnections", "64")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        readShared(intent)
        setContent { PS4App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readShared(intent)
    }

    private fun readShared(i: Intent?) {
        if (i != null && i.action == Intent.ACTION_SEND) {
            pendingShared = i.getStringExtra(Intent.EXTRA_TEXT)
        }
    }
}

// ───────────── الحالة والمنطق ─────────────
@Stable
class AppState(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = ctx.getSharedPreferences("ps4", Context.MODE_PRIVATE)

    var session by mutableStateOf<Ps4Session?>(null)
    var items by mutableStateOf<List<Capture>>(emptyList())
    var connecting by mutableStateOf(false)
    var scanning by mutableStateOf(false)
    var status by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var scanError by mutableStateOf<String?>(null)
    var viewer by mutableStateOf<Capture?>(null)
    var fullImage by mutableStateOf<ImageBitmap?>(null)

    /** معاينات الشبكة (null = جرّبنا ولا توجد صورة) */
    val thumbs = mutableStateMapOf<String, ImageBitmap?>()

    /** مسار الملف ← تقدم التحميل: 0..<1 جاري، 1 تم، -1 فشل */
    val downloads = mutableStateMapOf<String, Float>()

    /** مسار الملف ← عنوان الملف المحفوظ بمعرض الهاتف (للتشغيل) */
    val savedUris = mutableStateMapOf<String, String>()

    private val thumbOrder = ArrayDeque<String>()
    private var thumbBytes = 0L
    private val thumbLimit = 96L * 1024 * 1024

    val savedIp: String
        get() = prefs.getString("ip", "192.168.") ?: "192.168."

    // ───── التثبيت المباشر ─────
    var page by mutableIntStateOf(0)
    var link by mutableStateOf("")
    var mode by mutableIntStateOf(prefs.getInt("mode", 1))
    var netMode by mutableIntStateOf(prefs.getInt("netmode", 0))
    var connIdx by mutableIntStateOf(prefs.getInt("connidx", 1))
    var installPort by mutableStateOf(prefs.getString("iport", "12800") ?: "12800")
    var installBusy by mutableStateOf(false)
    var installOk by mutableStateOf<Boolean?>(null)
    var installMsg by mutableStateOf<String?>(null)
    var installWarn by mutableStateOf(false)
    var proxyRunning by mutableStateOf(false)
    var proxyView by mutableStateOf(ProxyView())

    private var proxy: ProxyServer? = null
    private var pollJob: Job? = null
    private var cellCb: ConnectivityManager.NetworkCallback? = null
    private var proxyWake: PowerManager.WakeLock? = null
    private var proxyWifi: WifiManager.WifiLock? = null

    fun updateMode(v: Int) {
        mode = v
        prefs.edit().putInt("mode", v).apply()
    }

    fun updateNetMode(v: Int) {
        netMode = v
        prefs.edit().putInt("netmode", v).apply()
    }

    fun updateConnIdx(v: Int) {
        connIdx = v
        prefs.edit().putInt("connidx", v).apply()
    }

    fun setPort(p: String) {
        installPort = p.filter { it.isDigit() }.take(5)
        prefs.edit().putString("iport", installPort).apply()
    }

    fun handleShared(text: String?) {
        val m = Regex("""https?://\S+""").find(text ?: "") ?: return
        link = m.value
        page = 1
        installOk = null
        installMsg = null
    }

    @Suppress("DEPRECATION")
    private fun wifiNetwork(): Network? {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.allNetworks.firstOrNull {
                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } catch (e: Exception) {
            null
        }
    }

    /** يطلب شبكة داتا الموبايل بالتوازي مع الواي فاي */
    private suspend fun getCellular(): Network? = suspendCancellableCoroutine<Network?> { cont ->
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (cont.isActive) cont.resume(network)
            }

            override fun onUnavailable() {
                if (cont.isActive) cont.resume(null)
            }
        }
        cellCb = cb
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.requestNetwork(req, cb, 6000)
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun acquireProxyLocks() {
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ps4captures:proxy")
            wl.acquire(6 * 60 * 60 * 1000L)
            proxyWake = wl
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wf = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "ps4captures:proxywifi")
            wf.acquire()
            proxyWifi = wf
        } catch (_: Exception) {
        }
    }

    private fun releaseProxyLocks() {
        try {
            proxyWake?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        try {
            proxyWifi?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        proxyWake = null
        proxyWifi = null
    }

    private suspend fun startProxy(ps4Ip: String, url: String): String {
        stopProxy()
        val wifi = wifiNetwork()
        val slots = ArrayList<NetSlot>()
        if (netMode == 0 || netMode == 2) slots.add(NetSlot(wifi, "Wi-Fi"))
        if (netMode == 1 || netMode == 2) {
            val cell = getCellular()
            if (cell != null) {
                slots.add(NetSlot(cell, "الداتا"))
            } else if (netMode == 1) {
                throw IOException("ما لكيت اتصال داتا الموبايل. فعّله وأعد المحاولة.")
            }
        }
        if (slots.isEmpty()) slots.add(NetSlot(wifi, "Wi-Fi"))

        val conns = CONN_OPTIONS[connIdx.coerceIn(0, CONN_OPTIONS.size - 1)]
        val server = ProxyServer(conns, slots, ps4Ip)
        val info = withContext(Dispatchers.IO) { server.prepare(url) }
        val phoneIp = localIpFor(ps4Ip) ?: throw IOException("ما كدرت أعرف IP التلفون على الشبكة")
        val port = server.start(8765)
        proxy = server
        proxyRunning = true
        proxyView = ProxyView(info.name, 0, info.size, 0, server.liveLabels())
        acquireProxyLocks()
        try {
            ctx.startForegroundService(Intent(ctx, KeepAliveService::class.java))
        } catch (_: Exception) {
        }
        startPoll()
        return "http://$phoneIp:$port/${info.name}"
    }

    fun stopProxy() {
        proxy?.stop()
        proxy = null
        proxyRunning = false
        pollJob?.cancel()
        pollJob = null
        releaseProxyLocks()
        try {
            ctx.stopService(Intent(ctx, KeepAliveService::class.java))
        } catch (_: Exception) {
        }
        val cb = cellCb
        if (cb != null) {
            try {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
            }
        }
        cellCb = null
    }

    private fun startPoll() {
        pollJob?.cancel()
        pollJob = scope.launch {
            val history = ArrayDeque<Pair<Long, Long>>()
            while (proxyRunning) {
                delay(500)
                val srv = proxy ?: break
                val served = srv.served.get()
                val now = SystemClock.elapsedRealtime()
                history.addLast(now to served)
                while (history.size > 2 && now - history.first().first > 4000L) {
                    history.removeFirst()
                }
                val first = history.first()
                val dt = (now - first.first).coerceAtLeast(1)
                val speed = if (history.size >= 2) {
                    ((served - first.second) * 1000.0 / dt).toLong().coerceAtLeast(0L)
                } else {
                    0L
                }
                proxyView = ProxyView(
                    srv.origin?.name ?: "", served, srv.total, speed, srv.liveLabels(),
                    srv.requests.get(), srv.lastError
                )
            }
        }
    }

    fun startInstall() {
        val s = session ?: return
        val url = link.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            installOk = false
            installMsg = "الصق رابطًا يبدأ بـ http أو https"
            return
        }
        if (installBusy) return
        val port = installPort.toIntOrNull() ?: 12800
        installBusy = true
        installOk = null
        installWarn = false
        installMsg = null
        scope.launch {
            try {
                bindWifi()
                val target = if (mode == 0) url else startProxy(s.ip, url)
                var timedOut = false
                val r = try {
                    withContext(Dispatchers.IO) { postInstall(s.ip, port, target) }
                } catch (e: java.net.ConnectException) {
                    null
                } catch (e: java.net.SocketTimeoutException) {
                    timedOut = true
                    null
                }
                if (r == null && timedOut) {
                    // الـ PS4 ما رد لحد الآن (غالبًا يحضّر الملف). نخلي السيرفر شغّال ولا نقتله.
                    installOk = true
                    installWarn = true
                    installMsg = if (mode == 1)
                        "الـ PS4 ما رد على الأمر لحد الآن، وقد يكون مشغولًا بتحضير اللعبة. السيرفر باقي شغّال: راقب إشعارات الجهاز وعدّاد الطلبات بالأسفل."
                    else
                        "الـ PS4 ما رد على الأمر لحد الآن. راقب إشعارات الجهاز."
                } else if (r == null) {
                    stopProxy()
                    installOk = false
                    installMsg = "أداة التثبيت مو شغّالة على المنفذ $port. افتحها على الـ PS4 وأعد المحاولة."
                } else if (r.code in 200..299) {
                    installOk = true
                    installMsg = if (mode == 0)
                        "تم إرسال الرابط. الـ PS4 ينزّل من الإنترنت مباشرة، تابع شاشته."
                    else
                        "تم إرسال الأمر. الـ PS4 هسه يسحب من تلفونك، لا تغلق التطبيق."
                } else {
                    stopProxy()
                    installOk = false
                    installMsg = "رد الجهاز (${r.code}): ${r.body}"
                }
            } catch (e: Exception) {
                stopProxy()
                installOk = false
                installMsg = "تعذّر الإرسال: ${e.message ?: "خطأ غير معروف"}"
            } finally {
                installBusy = false
            }
        }
    }

    private fun toast(msg: String, long: Boolean = false) =
        Toast.makeText(ctx, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    /** نربط التطبيق بشبكة الواي فاي حتى لو ما بيها إنترنت */
    private fun bindWifi() {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION")
            val wifi = cm.allNetworks.firstOrNull {
                cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            if (wifi != null) cm.bindProcessToNetwork(wifi)
        } catch (_: Exception) {
        }
    }

    fun connect(rawIp: String) {
        val ip = rawIp.trim()
        if (connecting) return
        if (!Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(ip)) {
            error = "اكتب عنوان IP صحيح مثل 192.168.1.20"
            return
        }
        prefs.edit().putString("ip", ip).apply()
        error = null
        connecting = true
        status = "جاري البحث عن الجهاز…"
        scope.launch {
            try {
                bindWifi()
                val s = autoConnect(ip) { p -> status = "جاري تجربة المنفذ $p …" }
                session = s
                loadDone()
                scan()
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                connecting = false
            }
        }
    }

    fun scan() {
        val s = session ?: return
        scanning = true
        scanError = null
        scope.launch {
            try {
                items = s.scan()
            } catch (e: Exception) {
                scanError = friendly(e)
            }
            scanning = false
        }
    }

    fun disconnect() {
        session?.close()
        session = null
        items = emptyList()
        thumbs.clear()
        thumbOrder.clear()
        thumbBytes = 0L
        downloads.clear()
        savedUris.clear()
        viewer = null
        fullImage = null
        stopProxy()
        page = 0
    }

    private fun putThumb(path: String, bmp: ImageBitmap) {
        thumbs[path] = bmp
        thumbOrder.addLast(path)
        thumbBytes += bmp.width.toLong() * bmp.height * 4
        while (thumbBytes > thumbLimit && thumbOrder.size > 1) {
            val old = thumbOrder.removeFirst()
            val b = thumbs.remove(old)
            if (b != null) thumbBytes -= b.width.toLong() * b.height * 4
        }
    }

    /** الصور: نحمّل الصورة الأصلية ونصغّرها بجودة عالية. المقاطع: المصغرة التي ينشئها الـ PS4 إن وجدت */
    fun loadThumb(item: Capture) {
        val s = session ?: return
        if (thumbs.containsKey(item.path)) return
        thumbs[item.path] = null
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    val b = if (item.isVideo) s.bytes(item.thumbPath)
                    else (s.bytes(item.path) ?: s.bytes(item.thumbPath))
                    if (b != null) decode(b, 720) else null
                } catch (e: Exception) {
                    null
                }
            }
            if (session === s && bmp != null) putThumb(item.path, bmp)
        }
    }

    fun openViewer(item: Capture) {
        val s = session ?: return
        viewer = item
        fullImage = null
        if (item.isVideo) return
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    val b = s.bytes(item.path)
                    if (b != null) decode(b, 4096) else null
                } catch (e: Exception) {
                    null
                }
            }
            if (viewer?.path == item.path) fullImage = bmp
        }
    }

    fun closeViewer() {
        viewer = null
        fullImage = null
    }

    fun download(item: Capture) {
        val s = session ?: return
        scope.launch { downloadNow(s, item) }
    }

    fun openSaved(item: Capture) {
        val u = savedUris[item.path] ?: return
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(u), if (item.isVideo) "video/*" else "image/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            toast("ما لكيت تطبيق يفتح الملف")
        }
    }

    private suspend fun downloadNow(s: Ps4Session, item: Capture) {
        val cur = downloads[item.path]
        if (cur != null && cur >= 0f) return
        downloads[item.path] = 0f
        val err = withContext(Dispatchers.IO) { saveToGallery(s, item) }
        if (err == null) {
            downloads[item.path] = 1f
            markDone(item.path)
            if (item.isVideo) videoThumb(item)
        } else {
            downloads[item.path] = -1f
            toast("فشل تحميل ${item.name}\n$err", long = true)
        }
    }

    private suspend fun videoThumb(item: Capture) {
        val u = savedUris[item.path] ?: return
        val bmp = withContext(Dispatchers.IO) {
            try {
                ctx.contentResolver.loadThumbnail(Uri.parse(u), Size(720, 720), null).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
        if (bmp != null) putThumb(item.path, bmp)
    }

    /** يرجع null عند النجاح، أو نص الخطأ */
    private suspend fun saveToGallery(s: Ps4Session, item: Capture): String? {
        val resolver = ctx.contentResolver
        val collection = if (item.isVideo)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(item.name))
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (item.isVideo) "Movies/PS4Captures" else "Pictures/PS4Captures"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return "تعذّر إنشاء الملف في المعرض"

        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ps4captures:dl")
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wf = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "ps4captures:wifi")
        return try {
            wl.acquire(60 * 60 * 1000L)
            wf.acquire()
            resolver.openOutputStream(uri)!!.use { out ->
                s.download(item.path, item.size, out) { p ->
                    downloads[item.path] = p.coerceAtMost(0.99f)
                }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            savedUris[item.path] = uri.toString()
            null
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            e.message ?: e.javaClass.simpleName
        } finally {
            try {
                if (wl.isHeld) wl.release()
            } catch (_: Exception) {
            }
            try {
                if (wf.isHeld) wf.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun markDone(path: String) {
        val set = (prefs.getStringSet("done", emptySet()) ?: emptySet()).toMutableSet()
        set.add(path)
        prefs.edit().putStringSet("done", set).apply()
    }

    private fun loadDone() {
        prefs.getStringSet("done", emptySet())?.forEach { downloads[it] = 1f }
    }
}

fun mimeOf(name: String): String = when (name.substringAfterLast('.').lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "mp4" -> "video/mp4"
    "webm" -> "video/webm"
    else -> "application/octet-stream"
}

fun decode(b: ByteArray, maxDim: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(b, 0, b.size, bounds)
    var sample = 1
    val m = maxOf(bounds.outWidth, bounds.outHeight)
    while (m / sample > maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(b, 0, b.size, opts)?.asImageBitmap()
}

fun friendly(e: Throwable): String {
    val m = (e.message ?: "").lowercase()
    return when {
        "no route" in m || "unreachable" in m || "timed out" in m || "timeout" in m || "failed to connect" in m ->
            "تعذّر الوصول للجهاز. تأكد من صحة الـ IP وأن الهاتف والـ PS4 على نفس شبكة الواي فاي وأن سيرفر FTP شغّال."
        "refused" in m ->
            "الجهاز رفض الاتصال. فعّل سيرفر FTP من إعدادات GoldHEN."
        else -> "تعذّر الاتصال: ${e.message ?: "خطأ غير معروف"}"
    }
}

fun fmtSize(b: Long): String {
    if (b <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = b.toDouble()
    var i = 0
    while (v >= 1024 && i < 3) {
        v /= 1024
        i++
    }
    return if (i <= 1) "${v.toInt()} ${units[i]}" else String.format(Locale.US, "%.1f %s", v, units[i])
}

fun captureTitle(c: Capture): String {
    if (c.time > 0) {
        return SimpleDateFormat("d MMM · h:mm a", Locale("ar")).format(Date(c.time))
    }
    return c.name.substringBeforeLast('.')
}

// ───────────── الواجهة ─────────────
@Composable
fun PS4App() {
    val ctx = LocalContext.current
    val state = remember { AppState(ctx.applicationContext) }
    val view = LocalView.current
    val shared = pendingShared
    LaunchedEffect(shared) {
        if (shared != null) {
            state.handleShared(shared)
            pendingShared = null
        }
    }

    // نبقي الشاشة شغّالة أثناء تحميل ملف كبير حتى لا ينقطع
    val busy = state.proxyRunning || state.downloads.values.any { it >= 0f && it < 1f }
    DisposableEffect(busy) {
        view.keepScreenOn = busy
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = state.viewer != null || state.session != null) {
        when {
            state.viewer != null -> state.closeViewer()
            state.session != null && state.page == 1 -> state.page = 0
            else -> state.disconnect()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Accent,
                background = Bg,
                surface = CardBg,
                onSurface = TextHi
            )
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0B1235), Bg, Color(0xFF04060E))
                        )
                    )
            ) {
                Crossfade(
                    targetState = state.session != null,
                    animationSpec = tween(350),
                    label = "screen"
                ) { connected ->
                    if (connected) {
                        if (state.page == 1) InstallScreen(state) else GalleryScreen(state)
                    } else {
                        ConnectScreen(state)
                    }
                }
                val v = state.viewer
                if (v != null) Viewer(state, v)
            }
        }
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
fun StepRow(n: Int, title: String, sub: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Accent, Accent2))),
            contentAlignment = Alignment.Center
        ) {
            Text("$n", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(sub, color = TextLo, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
fun ConnectScreen(state: AppState) {
    var ip by rememberSaveable { mutableStateOf(state.savedIp) }
    val focus = LocalFocusManager.current
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(34.dp))

        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        scaleX = glow
                        scaleY = glow
                    }
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Accent.copy(alpha = 0.38f), Color.Transparent)))
            )
            Box(
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Accent, Accent2))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.SportsEsports, null, tint = Color.White, modifier = Modifier.size(46.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("PS4 Captures", color = TextHi, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "استعرض لقطات ومقاطع جهازك مباشرةً من هاتفك،\nواحفظها بضغطة زر.",
            color = TextLo,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StepRow(1, "فعّل خادم FTP", "من إعدادات GoldHEN على جهاز PS4")
                StepRow(2, "أدخل عنوان IP", "نجرّب كل المنافذ المعروفة تلقائيًا")
                StepRow(3, "تصفّح واحفظ", "كأنك تتصفح معرض هاتفك، والتحميل من داخل كل صورة أو مقطع")
            }
        }

        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = ip,
            onValueChange = { v -> ip = v.filter { it.isDigit() || it == '.' } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("عنوان IP الـ PS4") },
            placeholder = { Text("192.168.1.20") },
            leadingIcon = { Icon(Icons.Rounded.Wifi, null) },
            shape = RoundedCornerShape(18.dp),
            textStyle = TextStyle(
                color = TextHi,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textDirection = TextDirection.Ltr
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                focus.clearFocus()
                state.connect(ip)
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextHi,
                unfocusedTextColor = TextHi,
                focusedContainerColor = Color.White.copy(alpha = 0.04f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                cursorColor = Accent,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                focusedLabelColor = Accent,
                unfocusedLabelColor = TextLo,
                focusedLeadingIconColor = Accent,
                unfocusedLeadingIconColor = TextLo,
                focusedPlaceholderColor = TextLo.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = TextLo.copy(alpha = 0.6f)
            )
        )

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Amber.copy(alpha = 0.12f))
                .border(1.dp, Amber.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Info, null, tint = Amber, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "مهم: يجب أن يكون الـ PS4 والهاتف متصلين بنفس شبكة الواي فاي.",
                color = Amber,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium
            )
        }

        val err = state.error
        if (err != null) {
            Spacer(Modifier.height(12.dp))
            Text(err, color = Bad, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(Accent, Accent2)))
                .clickable(enabled = !state.connecting) {
                    focus.clearFocus()
                    state.connect(ip)
                },
            contentAlignment = Alignment.Center
        ) {
            if (state.connecting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(state.status, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text("اتصال", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "يعمل عبر FTP (GoldHEN) — بدون حقن أو صلاحيات خاصة.",
            color = TextLo.copy(alpha = 0.7f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.imePadding())
    }
}

@Composable
fun GalleryScreen(state: AppState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val all = state.items
    val photos = remember(all) { all.filter { !it.isVideo } }
    val videos = remember(all) { all.filter { it.isVideo } }
    val shown = when (tab) {
        1 -> photos
        2 -> videos
        else -> all
    }
    val s = state.session

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { state.disconnect() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = TextHi)
            }
            Column(Modifier.weight(1f)) {
                Text("معرض الـ PS4", color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Good))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "متصل · ${s?.ip ?: ""}:${s?.port ?: ""}",
                        color = TextLo,
                        fontSize = 12.sp
                    )
                }
            }
            IconButton(onClick = { state.scan() }) {
                Icon(Icons.Rounded.Refresh, null, tint = TextHi)
            }
        }

        PageSwitch(state)

        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("الكل" to all.size, "الصور" to photos.size, "المقاطع" to videos.size)
                .forEachIndexed { i, pair ->
                    val sel = tab == i
                    val bg: Brush = if (sel) Brush.horizontalGradient(listOf(Accent, Accent2))
                    else SolidColor(Color.White.copy(alpha = 0.07f))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(bg)
                            .clickable { tab = i }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "${pair.first}  ${pair.second}",
                            color = if (sel) Color.White else TextLo,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val scanErr = state.scanError
            when {
                state.scanning -> CenterMessage(loading = true, text = "جاري فحص الجهاز…")
                scanErr != null -> CenterMessage(
                    icon = Icons.Rounded.ErrorOutline,
                    text = scanErr,
                    actionLabel = "إعادة المحاولة"
                ) { state.scan() }
                shown.isEmpty() -> CenterMessage(
                    icon = Icons.Rounded.PhotoLibrary,
                    text = "لا توجد ملفات هنا بعد.\nالتقط لقطة أو سجّل مقطعًا في اللعبة ثم حدّث."
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(shown, key = { it.path }) { item -> CaptureTile(item, state) }
                }
            }
        }
    }
}

@Composable
fun CenterMessage(
    loading: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    text: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) {
            CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
        } else if (icon != null) {
            Icon(icon, null, tint = TextLo.copy(alpha = 0.7f), modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(text, color = TextLo, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Accent, Accent2)))
                    .clickable { onAction() }
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(actionLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun CaptureTile(item: Capture, state: AppState) {
    LaunchedEffect(item.path) { state.loadThumb(item) }
    val bmp = state.thumbs[item.path]
    val saved = (state.downloads[item.path] ?: -1f) >= 1f

    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .clickable { state.openViewer(item) }
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val colors = if (item.isVideo) listOf(Color(0xFF3B1E6E), Color(0xFF1B2A6B))
            else listOf(Color(0xFF16305E), Color(0xFF1B1F45))
            Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(colors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (item.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Image,
                    null,
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        if (item.isVideo) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                if (item.isVideo) "فيديو" else "صورة",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (saved) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Good),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                .padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 10.dp)
        ) {
            Text(
                captureTitle(item),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(fmtSize(item.size), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

@Composable
fun Viewer(state: AppState, item: Capture) {
    var scale by remember(item.path) { mutableFloatStateOf(1f) }
    var offset by remember(item.path) { mutableStateOf(Offset.Zero) }
    val full = state.fullImage
    val shown = if (item.isVideo) state.thumbs[item.path] else (full ?: state.thumbs[item.path])
    val prog = state.downloads[item.path]
    val running = prog != null && prog >= 0f && prog < 1f
    val finished = prog != null && prog >= 1f
    val canPlay = state.savedUris.containsKey(item.path)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.97f))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        if (item.isVideo) {
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF3B1E6E), Color(0xFF1B2A6B)))),
                    contentAlignment = Alignment.Center
                ) {
                    if (shown != null) {
                        Image(
                            bitmap = shown,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .clickable(enabled = canPlay) { state.openSaved(item) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (canPlay) "تم الحفظ. اضغط تشغيل لفتحه."
                    else "المقطع كبير ولا ينعرض مباشرة. حمّله ثم شغّله من هاتفك.",
                    color = TextLo,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            if (shown != null) {
                Image(
                    bitmap = shown,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(item.path) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset = if (scale <= 1f) Offset.Zero else offset + pan
                            }
                        }
                )
            }
            if (full == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(34.dp),
                    color = Accent,
                    strokeWidth = 3.dp
                )
            }
        }

        // الشريط العلوي
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { state.closeViewer() }) {
                Icon(Icons.Rounded.Close, null, tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(captureTitle(item), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(fmtSize(item.size), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }

        // أزرار الأسفل
        val label = when {
            prog == null -> "حفظ في المعرض"
            prog < 0f -> "إعادة المحاولة"
            prog >= 1f -> "تم الحفظ ✓"
            else -> "جاري التحميل ${(prog * 100).toInt()}% · ${fmtSize((prog * item.size).toLong())} / ${fmtSize(item.size)}"
        }
        val pillBg: Brush = if (finished) Brush.horizontalGradient(listOf(Good, Color(0xFF10B981)))
        else Brush.horizontalGradient(listOf(Accent, Accent2))

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (running) {
                LinearProgressIndicator(
                    progress = { prog ?: 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    color = Accent,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(pillBg)
                    .clickable(enabled = !running && !finished) { state.download(item) }
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (finished) Icons.Rounded.Check else Icons.Rounded.Download,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            if (canPlay) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .clickable { state.openSaved(item) }
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (item.isVideo) "تشغيل" else "فتح",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

fun fmtEta(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val x = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, x)
    else String.format(Locale.US, "%d:%02d", m, x)
}

@Composable
fun SegPills(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val bg: Brush = if (sel) Brush.horizontalGradient(listOf(Accent, Accent2))
            else SolidColor(Color.Transparent)
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (sel) Color.White else TextLo,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PageSwitch(state: AppState) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        SegPills(listOf("المعرض", "تثبيت مباشر"), state.page) { state.page = it }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextHi,
    unfocusedTextColor = TextHi,
    focusedContainerColor = Color.White.copy(alpha = 0.04f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
    cursorColor = Accent,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
    focusedPlaceholderColor = TextLo.copy(alpha = 0.6f),
    unfocusedPlaceholderColor = TextLo.copy(alpha = 0.6f)
)

@Composable
fun InstallScreen(state: AppState) {
    val clip = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val s = state.session

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { state.page = 0 }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = TextHi)
            }
            Column(Modifier.weight(1f)) {
                Text("تثبيت مباشر", color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Good))
                    Spacer(Modifier.width(6.dp))
                    Text("متصل · ${s?.ip ?: ""}", color = TextLo, fontSize = 12.sp)
                }
            }
        }

        PageSwitch(state)

        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard {
                Text("رابط اللعبة", color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.link,
                    onValueChange = { state.link = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    placeholder = { Text("https://…/game.pkg") },
                    trailingIcon = {
                        IconButton(onClick = {
                            val t = clip.getText()?.text
                            if (!t.isNullOrBlank()) state.link = t.trim()
                        }) {
                            Icon(Icons.Rounded.ContentPaste, null, tint = Accent)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    textStyle = TextStyle(color = TextHi, fontSize = 13.sp, textDirection = TextDirection.Ltr),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "رابط مباشر لملف .pkg. تكدر تشارك الرابط من المتصفح لهذا التطبيق وينلصق هنا تلقائيًا.",
                    color = TextLo,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }

            GlassCard {
                Text("طريقة النقل", color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                SegPills(listOf("مباشر", "عبر التلفون (أسرع)"), state.mode) { state.updateMode(it) }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.mode == 0)
                        "الـ PS4 ينزّل الرابط من الإنترنت بنفسه. أبسط طريقة، والتلفون ما يشتغل."
                    else
                        "التلفون يسحب الملف بعدة اتصالات متوازية (وحتى من شبكتين) ويمرره للـ PS4. مفيد لما السيرفر يبطّئ الاتصال الواحد.",
                    color = TextLo,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                if (state.mode == 1) {
                    Spacer(Modifier.height(16.dp))
                    Text("مصدر الإنترنت", color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    SegPills(listOf("Wi-Fi", "داتا الموبايل", "الاثنين معًا"), state.netMode) { state.updateNetMode(it) }
                    if (state.netMode != 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "تنبيه: الداتا تُحسب من باقتك. وبعض الروابط المؤقتة مربوطة بالـ IP وتفشل على الداتا، والتطبيق يتجاوزها تلقائيًا.",
                            color = Amber,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("عدد الاتصالات المتوازية", color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    SegPills(CONN_OPTIONS.map { "$it" }, state.connIdx) { state.updateConnIdx(it) }
                }
            }

            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("منفذ أداة التثبيت", color = TextHi, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("الافتراضي 12800", color = TextLo, fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = state.installPort,
                        onValueChange = { state.setPort(it) },
                        modifier = Modifier.width(110.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        textStyle = TextStyle(
                            color = TextHi,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            textDirection = TextDirection.Ltr
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors()
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(Accent, Accent2)))
                    .clickable(enabled = !state.installBusy) {
                        focus.clearFocus()
                        state.startInstall()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (state.installBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudDownload, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("إرسال للـ PS4", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            val ok = state.installOk
            val msg = state.installMsg
            if (ok != null && msg != null) {
                val c = if (state.installWarn) Amber else if (ok) Good else Bad
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.copy(alpha = 0.12f))
                        .border(1.dp, c.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        if (state.installWarn) Icons.Rounded.Info else if (ok) Icons.Rounded.Check else Icons.Rounded.ErrorOutline,
                        null,
                        tint = c,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(msg, color = c, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }

            ProxyStatusCard(state)
            Spacer(Modifier.height(28.dp))
            Spacer(Modifier.imePadding())
        }
    }
}

@Composable
private fun ProxyStatusCard(state: AppState) {
    if (!state.proxyRunning) return
    val v = state.proxyView
    val frac = if (v.total > 0) (v.served.toFloat() / v.total).coerceIn(0f, 1f) else 0f
    val eta = if (v.speed >= 100 * 1024 && v.total > v.served) (v.total - v.served) / v.speed else 0L
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Good))
            Spacer(Modifier.width(6.dp))
            Text(
                "السيرفر الوسيط شغّال",
                color = Good,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, Bad.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .clickable { state.stopProxy() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("إيقاف", color = Bad, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            v.name,
            color = TextHi,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
            style = TextStyle(textDirection = TextDirection.Ltr)
        )
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = Accent,
            trackColor = Color.White.copy(alpha = 0.12f)
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (v.speed >= 1024) fmtSize(v.speed) + "/s" else "—",
                color = TextHi,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(textDirection = TextDirection.Content)
            )
            Text(
                if (eta > 0) "المتبقي " + fmtEta(eta) else "",
                color = TextLo,
                fontSize = 13.sp,
                style = TextStyle(textDirection = TextDirection.Content)
            )
        }
        Text(
            "${fmtSize(v.served)} / ${fmtSize(v.total)} · ${v.nets}",
            color = TextLo,
            fontSize = 12.sp,
            style = TextStyle(textDirection = TextDirection.Content)
        )
        Text(
            "طلبات الـ PS4 للسيرفر: ${v.requests}",
            color = TextLo,
            fontSize = 12.sp
        )
        if (v.error.isNotEmpty()) {
            Text(
                "آخر خطأ: ${v.error}",
                color = Bad,
                fontSize = 11.sp,
                style = TextStyle(textDirection = TextDirection.Content)
            )
        }
        if (v.served < 5L * 1024 * 1024 && v.speed < 1024) {
            Spacer(Modifier.height(6.dp))
            Text(
                "بانتظار الـ PS4 يبدأ السحب… شوف إشعارات الجهاز (التنزيلات). المرحلة الأولى قد تأخذ شوية.",
                color = Amber,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
        if (v.total > 0 && v.served >= v.total) {
            Spacer(Modifier.height(6.dp))
            Text("اكتمل النقل ✓ تكدر توقف السيرفر.", color = Good, fontSize = 12.sp)
        }
    }
}