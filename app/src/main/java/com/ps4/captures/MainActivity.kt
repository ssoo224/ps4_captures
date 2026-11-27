package com.ps4.captures

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.MediaStore
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { PS4App() }
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

    /** null = جرّبنا ولا توجد صورة مصغرة */
    val thumbs = mutableStateMapOf<String, ImageBitmap?>()

    /** مسار الملف ← تقدم التحميل: 0..<1 جاري، 1 تم، -1 فشل */
    val downloads = mutableStateMapOf<String, Float>()

    val savedIp: String
        get() = prefs.getString("ip", "192.168.") ?: "192.168."

    private fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

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
        downloads.clear()
        viewer = null
        fullImage = null
    }

    fun loadThumb(item: Capture) {
        val s = session ?: return
        if (thumbs.containsKey(item.path)) return
        thumbs[item.path] = null
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    val b = s.bytes(item.thumbPath) ?: if (!item.isVideo) s.bytes(item.path) else null
                    if (b != null) decode(b, 640) else null
                } catch (e: Exception) {
                    null
                }
            }
            if (session === s) thumbs[item.path] = bmp
        }
    }

    fun openViewer(item: Capture) {
        val s = session ?: return
        viewer = item
        fullImage = null
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    val b = s.bytes(item.path)
                    if (b != null) decode(b, 2600) else null
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

    fun downloadAll(list: List<Capture>) {
        val s = session ?: return
        val todo = list.filter { (downloads[it.path] ?: -1f) < 0f }
        if (todo.isEmpty()) {
            toast("كل الملفات محمّلة مسبقًا")
            return
        }
        toast("بدأ تحميل ${todo.size} ملف")
        scope.launch { for (c in todo) downloadNow(s, c) }
    }

    private suspend fun downloadNow(s: Ps4Session, item: Capture) {
        val cur = downloads[item.path]
        if (cur != null && cur >= 0f) return
        downloads[item.path] = 0f
        val ok = withContext(Dispatchers.IO) { saveToGallery(s, item) }
        if (ok) {
            downloads[item.path] = 1f
            markDone(item.path)
        } else {
            downloads[item.path] = -1f
            toast("فشل تحميل ${item.name}")
        }
    }

    private suspend fun saveToGallery(s: Ps4Session, item: Capture): Boolean {
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
        val uri = resolver.insert(collection, values) ?: return false
        return try {
            resolver.openOutputStream(uri)!!.use { out ->
                s.download(item.path, item.size, out) { p ->
                    downloads[item.path] = p.coerceAtMost(0.99f)
                }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            false
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
        return SimpleDateFormat("d MMM  ·  h:mm a", Locale("ar")).format(Date(c.time))
    }
    return c.name.substringBeforeLast('.')
}

// ───────────── الواجهة ─────────────
@Composable
fun PS4App() {
    val ctx = LocalContext.current
    val state = remember { AppState(ctx.applicationContext) }

    BackHandler(enabled = state.viewer != null || state.session != null) {
        if (state.viewer != null) state.closeViewer() else state.disconnect()
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
                    if (connected) GalleryScreen(state) else ConnectScreen(state)
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
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(34.dp))

        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(140.dp)
                    .scale(glow)
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
                StepRow(3, "تصفّح واحفظ", "كأنك تتصفح معرض هاتفك، مع زر تحميل لكل ملف")
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
        // الرأس
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
            IconButton(onClick = { state.downloadAll(shown) }) {
                Icon(Icons.Rounded.DownloadForOffline, null, tint = Accent, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { state.scan() }) {
                Icon(Icons.Rounded.Refresh, null, tint = TextHi)
            }
        }

        // التبويبات
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
                scanErr != null -> CenterMessage(icon = Icons.Rounded.ErrorOutline, text = scanErr, actionLabel = "إعادة المحاولة") {
                    state.scan()
                }
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
    val ctx = LocalContext.current
    LaunchedEffect(item.path) { state.loadThumb(item) }
    val bmp = state.thumbs[item.path]
    val prog = state.downloads[item.path]

    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .clickable {
                if (item.isVideo) {
                    Toast.makeText(ctx, "حمّل المقطع ثم شغّله من معرض هاتفك", Toast.LENGTH_SHORT).show()
                } else {
                    state.openViewer(item)
                }
            }
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
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

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                .padding(start = 12.dp, end = 10.dp, top = 28.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
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
            Spacer(Modifier.width(8.dp))
            DownloadButton(prog, 36.dp) { state.download(item) }
        }
    }
}

@Composable
fun DownloadButton(progress: Float?, size: Dp, onClick: () -> Unit) {
    val done = progress != null && progress >= 1f
    val running = progress != null && progress >= 0f && progress < 1f
    val failed = progress != null && progress < 0f
    val bg: Brush = when {
        done -> Brush.linearGradient(listOf(Good, Color(0xFF10B981)))
        failed -> SolidColor(Bad)
        running -> SolidColor(Color.Black.copy(alpha = 0.55f))
        else -> Brush.linearGradient(listOf(Accent, Accent2))
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = !running && !done, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            running -> CircularProgressIndicator(
                progress = { progress ?: 0f },
                modifier = Modifier.size(size * 0.55f),
                strokeWidth = 2.5.dp,
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
            done -> Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
            failed -> Icon(Icons.Rounded.Refresh, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
            else -> Icon(Icons.Rounded.Download, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
        }
    }
}

@Composable
fun Viewer(state: AppState, item: Capture) {
    var scale by remember(item.path) { mutableFloatStateOf(1f) }
    var offset by remember(item.path) { mutableStateOf(Offset.Zero) }
    val full = state.fullImage
    val shown = full ?: state.thumbs[item.path]

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        if (shown != null) {
            Image(
                bitmap = shown,
                contentDescription = null,
                contentScale = ContentScale.Fit,
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
                            scale = (scale * zoom).coerceIn(1f, 5f)
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

        // زر الحفظ
        val prog = state.downloads[item.path]
        val label = when {
            prog == null || prog < 0f -> "حفظ في المعرض"
            prog >= 1f -> "تم الحفظ ✓"
            else -> "جاري التحميل ${(prog * 100).toInt()}%"
        }
        val finished = prog != null && prog >= 1f
        val pillBg: Brush = if (finished) Brush.horizontalGradient(listOf(Good, Color(0xFF10B981)))
        else Brush.horizontalGradient(listOf(Accent, Accent2))
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 26.dp)
                .clip(RoundedCornerShape(50))
                .background(pillBg)
                .clickable(enabled = prog == null || prog < 0f) { state.download(item) }
                .padding(horizontal = 34.dp, vertical = 14.dp),
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
                Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
