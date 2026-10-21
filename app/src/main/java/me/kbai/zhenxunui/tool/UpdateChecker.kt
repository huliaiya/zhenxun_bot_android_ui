package me.kbai.zhenxunui.tool

import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kbai.zhenxunui.BuildConfig
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.tool.GlobalToast.showToast
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Checks the rolling "latest" GitHub release and prompts the user to update
 * when its version differs from the installed one.
 */
object UpdateChecker {
    private const val RELEASE_API =
        "https://api.github.com/repos/huliaiya/zhenxun_bot_android_ui/releases/tags/latest"

    private class ReleaseInfo(val version: String, val downloadUrl: String, val notes: String)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

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
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_found_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_now) { _, _ ->
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl)))
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }
}
