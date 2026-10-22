package me.kbai.zhenxunui.tool

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import me.kbai.zhenxunui.extends.logE
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Fetches and holds the daily wallpaper for the whole app.
 *
 * Endpoint: api.huliaiya.icu/API/zhenxun.php (HTTPS preferred, HTTP fallback).
 */
object WallpaperStore {
    private const val HOST = "api.huliaiya.icu/API/zhenxun.php"
    private const val FILE_NAME = "today_wallpaper.jpg"
    private const val MAX_DIM = 2048
    private const val FETCH_TIMEOUT_MS = 20_000L

    private val mClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap

    private var mCacheLoaded = false

    /**
     * Loads the last successfully fetched wallpaper from cache (once).
     */
    fun loadCached(context: Context) {
        if (mCacheLoaded) return
        mCacheLoaded = true
        val file = File(context.cacheDir, FILE_NAME)
        if (!file.exists()) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { decodeFile(file) }
                .onSuccess { if (it != null) _bitmap.value = it }
                .onFailure { logE(it) }
        }
    }

    data class FetchResult(val success: Boolean, val code: Int)

    /**
     * Fetches today's wallpaper. Tries HTTPS first, then HTTP.
     * On success the bitmap and the cache file are replaced.
     */
    suspend fun fetchToday(context: Context): FetchResult {
        val result = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            var lastCode = -1
            for (scheme in listOf("https://", "http://")) {
                val (code, bytes) = callForImage(scheme + HOST)
                if (code > 0) lastCode = code
                if (code in 200..299 && bytes != null) {
                    val bmp = decodeBytes(bytes)
                    if (bmp != null) {
                        saveToCache(context, bytes)
                        _bitmap.value = bmp
                        return@withTimeoutOrNull FetchResult(true, code)
                    }
                }
            }
            FetchResult(false, lastCode)
        }
        return result ?: FetchResult(false, -1)
    }

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

    private fun saveToCache(context: Context, bytes: ByteArray) {
        runCatching {
            File(context.cacheDir, FILE_NAME).writeBytes(bytes)
        }.onFailure { logE(it) }
    }

    private fun decodeBytes(bytes: ByteArray): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sampleOptions(bounds))
    }.getOrNull()

    private fun decodeFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(file.absolutePath, sampleOptions(bounds))
    }

    private fun sampleOptions(bounds: BitmapFactory.Options) = BitmapFactory.Options().apply {
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_DIM || bounds.outHeight / (sample * 2) >= MAX_DIM) {
            sample *= 2
        }
        inSampleSize = sample
    }
}
