package me.kbai.zhenxunui.tool

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kbai.zhenxunui.BuildConfig
import me.kbai.zhenxunui.Constants
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.tool.GlobalToast.showToast
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub releases, prompts the user to update and, once confirmed,
 * downloads the APK in-app and launches the package installer. Also exposes
 * release history browsing and stale APK cleanup.
 */
object UpdateChecker {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/huliaiya/zhenxun_bot_android_ui/releases/latest"
    private const val RELEASE_LIST_API =
        "https://api.github.com/repos/huliaiya/zhenxun_bot_android_ui/releases?per_page=30"
    private const val PREF_AUTO_UPDATE = "auto_update"

    data class ReleaseInfo(
        val version: String,
        val downloadUrl: String,
        val notes: String,
        val tagName: String,
        val publishedAt: String,
        val prerelease: Boolean
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private var downloadJob: Job? = null

    fun isAutoUpdateEnabled(context: Context): Boolean =
        context.getSharedPreferences(Constants.SP_NAME_CONFIG, Context.MODE_PRIVATE)
            .getBoolean(PREF_AUTO_UPDATE, true)

    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(Constants.SP_NAME_CONFIG, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_AUTO_UPDATE, enabled).apply()
    }

    /**
     * Deletes leftover downloaded APKs. Called on app start so the package of a
     * finished update is removed automatically, and before each new download so
     * stale or corrupted files can never break the next install.
     */
    fun cleanApkDir(context: Context) {
        if (downloadJob?.isActive == true) return
        runCatching {
            (context.getExternalFilesDir("apk") ?: context.filesDir)
                .listFiles()?.forEach { it.delete() }
        }
    }

    fun check(activity: FragmentActivity, manual: Boolean) = activity.lifecycleScope.launch {
        if (!manual && !isAutoUpdateEnabled(activity)) return@launch
        val result = withContext(Dispatchers.IO) {
            runCatching { fetchLatestRelease() }
        }
        result.onSuccess { release ->
            if (release.version == BuildConfig.VERSION_NAME) {
                if (manual) showToast(R.string.already_latest_version)
            } else {
                showUpdateDialog(activity, release)
            }
        }.onFailure {
            if (manual) showToast(R.string.update_check_failed)
        }
    }

    suspend fun fetchReleases(): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        val body = getJson(RELEASE_LIST_API)
        val array = JSONArray(body)
        val result = ArrayList<ReleaseInfo>(array.length())
        for (i in 0 until array.length()) {
            val release = array.getJSONObject(i)
            parseApkAsset(release)?.let { asset ->
                result.add(
                    ReleaseInfo(
                        version = asset.second,
                        downloadUrl = asset.first,
                        notes = release.optString("body"),
                        tagName = release.optString("tag_name"),
                        publishedAt = release.optString("published_at").take(10),
                        prerelease = release.optBoolean("prerelease")
                    )
                )
            }
        }
        result
    }

    private fun fetchLatestRelease(): ReleaseInfo {
        val json = JSONObject(getJson(LATEST_RELEASE_API))
        val (url, version) = parseApkAsset(json)
            ?: throw IOException("no apk asset")
        return ReleaseInfo(
            version = version,
            downloadUrl = url,
            notes = json.optString("body"),
            tagName = json.optString("tag_name"),
            publishedAt = json.optString("published_at").take(10),
            prerelease = json.optBoolean("prerelease")
        )
    }

    private fun getJson(url: String): String =
        client.newCall(
            Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "zhenxun-bot-android-ui")
                .build()
        ).execute().use { resp ->
            require(resp.isSuccessful) { "HTTP ${resp.code}" }
            resp.body?.string().orEmpty()
        }

    /** Returns (downloadUrl, version) of the first release asset we recognize. */
    private fun parseApkAsset(release: JSONObject): Pair<String, String>? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name.endsWith(".apk") && name.startsWith("zxbot-release-")) {
                return asset.optString("browser_download_url") to
                        name.removePrefix("zxbot-release-").removeSuffix(".apk")
            }
        }
        return null
    }

    private fun showUpdateDialog(activity: FragmentActivity, release: ReleaseInfo) {
        val message = activity.getString(
            R.string.update_found_format, release.version, BuildConfig.VERSION_NAME
        ) + "\n\n" + release.notes.take(500).trim()
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_found_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_now, null)
            .setNegativeButton(R.string.later, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { button ->
            stylePrimaryButton(activity, button)
            button.setOnClickListener { downloadAndInstall(activity, release) }
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { button ->
            button.setTextColor(ContextCompat.getColor(activity, R.color.dn_black))
        }
    }

    fun showInstallDialog(activity: FragmentActivity, release: ReleaseInfo) {
        val message = activity.getString(
            R.string.install_version_format, release.version
        ) + "\n\n" + release.notes.take(300).trim()
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(release.tagName)
            .setMessage(message)
            .setPositiveButton(R.string.install_this_version, null)
            .setNegativeButton(R.string.later, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { button ->
            stylePrimaryButton(activity, button)
            button.setOnClickListener { downloadAndInstall(activity, release) }
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { button ->
            button.setTextColor(ContextCompat.getColor(activity, R.color.dn_black))
        }
    }

    private fun stylePrimaryButton(activity: FragmentActivity, button: View) {
        val density = activity.resources.displayMetrics.density
        (button as MaterialButton).apply {
            cornerRadius = (22 * density).toInt()
            backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.glass_button_tint))
            setTextColor(ContextCompat.getColor(activity, R.color.glass_button_text))
            rippleColor =
                ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.glass_ripple))
            minWidth = 0
            minHeight = (38 * density).toInt()
            setPadding((24 * density).toInt(), 0, (24 * density).toInt(), 0)
        }
    }

    fun downloadAndInstall(activity: FragmentActivity, release: ReleaseInfo) {
        cleanApkDir(activity)
        val progress = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.downloading_update)
            .setMessage("0%")
            .setNegativeButton(R.string.cancel, null)
            .show()
        progress.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            downloadJob?.cancel()
            progress.dismiss()
        }
        downloadJob = activity.lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    downloadApk(activity, release.downloadUrl, release.version) { percent ->
                        activity.runOnUiThread {
                            if (progress.isShowing) progress.setMessage("$percent%")
                        }
                    }
                }
                progress.dismiss()
                installApk(activity, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                progress.dismiss()
                showToast(R.string.update_download_failed)
            }
        }
    }

    private suspend fun downloadApk(
        activity: FragmentActivity,
        url: String,
        version: String,
        onProgress: (Int) -> Unit
    ): File =
        withContext(Dispatchers.IO) {
            val dir = activity.getExternalFilesDir("apk") ?: activity.filesDir
            val file = File(dir, "zxbot-$version.apk")
            if (file.exists()) file.delete()

            client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "zhenxun-bot-android-ui")
                    .build()
            ).execute().use { resp ->
                require(resp.isSuccessful) { "HTTP ${resp.code}" }
                val body = resp.body ?: throw IOException("empty body")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPercent = -1
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) {
                                val percent = (done * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
            }
            file
        }

    private fun installApk(activity: FragmentActivity, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.packageName)
                )
            )
            showToast(R.string.grant_install_permission)
            return
        }
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", file)
        activity.startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}
