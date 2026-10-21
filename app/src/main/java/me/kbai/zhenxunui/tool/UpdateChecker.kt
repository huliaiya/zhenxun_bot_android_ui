package me.kbai.zhenxunui.tool

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kbai.zhenxunui.BuildConfig
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.tool.GlobalToast.showToast
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Checks the rolling "latest" GitHub release, prompts the user to update and,
 * once confirmed, downloads the APK in-app and launches the package installer.
 */
object UpdateChecker {
    private const val RELEASE_API =
        "https://api.github.com/repos/huliaiya/zhenxun_bot_android_ui/releases/tags/latest"

    private class ReleaseInfo(val version: String, val downloadUrl: String, val notes: String)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private var downloadJob: Job? = null

    fun check(activity: FragmentActivity, manual: Boolean) = activity.lifecycleScope.launch {
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

    private fun fetchLatestRelease(): ReleaseInfo {
        val body = client.newCall(
            Request.Builder()
                .url(RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "zhenxun-bot-android-ui")
                .build()
        ).execute().use { resp ->
            require(resp.isSuccessful) { "HTTP ${resp.code}" }
            resp.body?.string().orEmpty()
        }
        val json = JSONObject(body)
        val assets = json.getJSONArray("assets")
        var downloadUrl = ""
        var version = ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name.endsWith(".apk") && name.startsWith("zxbot-release-")) {
                downloadUrl = asset.optString("browser_download_url")
                version = name.removePrefix("zxbot-release-").removeSuffix(".apk")
                break
            }
        }
        require(downloadUrl.isNotBlank()) { "no apk asset" }
        return ReleaseInfo(version, downloadUrl, json.optString("body"))
    }

    private fun showUpdateDialog(activity: FragmentActivity, release: ReleaseInfo) {
        val message = activity.getString(
            R.string.update_found_format, release.version, BuildConfig.VERSION_NAME
        ) + "\n\n" + release.notes.take(500).trim()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_found_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_now) { _, _ ->
                downloadAndInstall(activity, release)
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun downloadAndInstall(activity: FragmentActivity, release: ReleaseInfo) {
        val progress = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.downloading_update)
            .setMessage("0%")
            .setNegativeButton(R.string.cancel, null)
            .show()
        progress.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            downloadJob?.cancel()
            progress.dismiss()
        }
        downloadJob = activity.lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    downloadApk(activity, release.downloadUrl) { percent ->
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
        onProgress: (Int) -> Unit
    ): File =
        withContext(Dispatchers.IO) {
            val dir = activity.getExternalFilesDir("apk") ?: activity.filesDir
            val file = File(dir, "zxbot-update.apk")
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
