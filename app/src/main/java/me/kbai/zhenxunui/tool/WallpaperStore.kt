package me.kbai.zhenxunui.tool

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import me.kbai.zhenxunui.Constants
import me.kbai.zhenxunui.extends.logE
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Fetches and holds the daily wallpaper for the whole app.
 *
 * Endpoint: api.huliaiya.icu/API/zhenxun.php (HTTPS preferred, HTTP fallback).
 */
object WallpaperStore {
    private const val HOST = "api.huliaiya.icu/API/zhenxun.php"
    private const val MAX_DIM = 2048
    //5秒内拿不到壁纸就直接放弃, 页面退回纯色底
    private const val FETCH_TIMEOUT_MS = 5_000L
    private const val SP_KEY_SCRIM_ALPHA = "wallpaper_scrim_alpha"

    const val DEFAULT_SCRIM_ALPHA = 192

    private val mClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap

    private val _scrimAlpha = MutableStateFlow(DEFAULT_SCRIM_ALPHA)
    val scrimAlpha: StateFlow<Int> = _scrimAlpha

    private var mPrefsLoaded = false

    /**
     * Appends a timestamp query param so neither the endpoint nor any
     * intermediary cache can serve a stale (deleted) image.
     */
    private fun cacheBust(url: String) =
        url + (if (url.contains('?')) '&' else '?') + "t=" + System.currentTimeMillis()

    /**
     * Loads the persisted wallpaper scrim alpha (once).
     */
    fun loadPrefs(context: Context) {
        if (mPrefsLoaded) return
        mPrefsLoaded = true
        val sp = context.applicationContext
            .getSharedPreferences(Constants.SP_NAME_CONFIG, Context.MODE_PRIVATE)
        _scrimAlpha.value = sp.getInt(SP_KEY_SCRIM_ALPHA, DEFAULT_SCRIM_ALPHA)
    }

    /**
     * Updates the wallpaper scrim alpha (0-255) and persists it.
     */
    fun setScrimAlpha(context: Context, alpha: Int) {
        val clamped = alpha.coerceIn(0, 255)
        _scrimAlpha.value = clamped
        context.applicationContext
            .getSharedPreferences(Constants.SP_NAME_CONFIG, Context.MODE_PRIVATE)
            .edit()
            .putInt(SP_KEY_SCRIM_ALPHA, clamped)
            .apply()
    }

    data class FetchResult(val success: Boolean, val code: Int)

    /**
     * Fetches today's wallpaper. Tries HTTPS first, then HTTP.
     * Always hits the network - nothing is persisted locally.
     */
    suspend fun fetchToday(context: Context): FetchResult {
        val result = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            var lastCode = -1
            for (scheme in listOf("https://", "http://")) {
                val (code, bytes) = callForImage(cacheBust(scheme + HOST))
                if (code > 0) lastCode = code
                if (code in 200..299 && bytes != null) {
                    // case 1: endpoint returns the image directly
                    val bmp = decodeBytes(bytes)
                    if (bmp != null) {
                        _bitmap.value = bmp
                        return@withTimeoutOrNull FetchResult(true, code)
                    }
                    // case 2: endpoint returns JSON carrying the real image url
                    val imageUrl = parseImageUrl(bytes)
                    if (imageUrl != null) {
                        val (imgCode, imgBytes) = fetchImage(cacheBust(imageUrl), scheme)
                        if (imgCode > 0) lastCode = imgCode
                        if (imgCode in 200..299 && imgBytes != null) {
                            val imgBmp = decodeBytes(imgBytes)
                            if (imgBmp != null) {
                                _bitmap.value = imgBmp
                                return@withTimeoutOrNull FetchResult(true, imgCode)
                            }
                        }
                    }
                }
            }
            FetchResult(false, lastCode)
        }
        return result ?: FetchResult(false, -1)
    }

    /**
     * Downloads the image url from the JSON payload.
     * If the direct download fails, retries once with the endpoint's scheme
     * (https <-> http) swapped in.
     */
    private suspend fun fetchImage(url: String, scheme: String): Pair<Int, ByteArray?> {
        val (code, bytes) = callForImage(url)
        if (code in 200..299 && bytes != null) return code to bytes
        val swapped = url.replaceFirst(Regex("^https?://"), scheme)
        if (swapped != url) return callForImage(swapped)
        return code to bytes
    }

    private fun parseImageUrl(bytes: ByteArray): String? = runCatching {
        JSONObject(String(bytes, Charset.forName("UTF-8")))
            .optString("image_url")
            .ifBlank { null }
    }.getOrNull()

    private suspend fun callForImage(url: String): Pair<Int, ByteArray?> =
        suspendCancellableCoroutine { cont ->
            val call = mClient.newCall(Request.Builder().url(url).build())
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(-1 to null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val code = response.code
                    val bytes = if (response.isSuccessful) {
                        runCatching { response.body?.bytes() }
                            .onFailure { logE(it) }
                            .getOrNull()
                    } else {
                        null
                    }
                    response.close()
                    if (cont.isActive) cont.resume(code to bytes)
                }
            })
        }

    /**
     * Removes any legacy on-disk wallpaper cache and the scrim pref stays untouched.
     */
    fun clearLegacyCache(context: Context) {
        runCatching {
            File(context.cacheDir, "today_wallpaper.jpg").delete()
        }
    }

    private fun decodeBytes(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sampleOptions(bounds))
    }.getOrNull()

    private fun sampleOptions(bounds: BitmapFactory.Options) = BitmapFactory.Options().apply {
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_DIM || bounds.outHeight / (sample * 2) >= MAX_DIM) {
            sample *= 2
        }
        inSampleSize = sample
    }
}
