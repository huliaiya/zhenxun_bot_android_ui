package me.kbai.zhenxunui.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kbai.zhenxunui.BuildConfig
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentSettingsBinding
import me.kbai.zhenxunui.extends.logE
import me.kbai.zhenxunui.extends.runWithoutReturn
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.extends.viewLifecycleScope
import me.kbai.zhenxunui.tool.GlobalToast.showToast
import me.kbai.zhenxunui.tool.ThemeMode
import me.kbai.zhenxunui.tool.UpdateChecker
import me.kbai.zhenxunui.tool.WallpaperStore
import me.kbai.zhenxunui.tool.glide.GlideApp
import java.io.File
import java.util.Locale

/**
 * @author Sean on 2026/10/21
 */
class SettingsFragment : BaseFragment<FragmentSettingsBinding>() {

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSettingsBinding =
        FragmentSettingsBinding.inflate(inflater, container, false)

    override fun initView() = viewBinding.runWithoutReturn {
        llAppearance.setOnDebounceClickListener {
            runCatching {
                findNavController().navigate(R.id.action_settings_to_theme)
            }.onFailure { logE(it) }
        }
        llUpdate.setOnDebounceClickListener {
            runCatching {
                findNavController().navigate(R.id.action_settings_to_update)
            }.onFailure { e ->
                logE(e)
                showToast(R.string.action_failed)
            }
        }
        llAbout.setOnDebounceClickListener {
            runCatching {
                findNavController().navigate(R.id.action_settings_to_about)
            }.onFailure { e ->
                logE(e)
                showToast(R.string.action_failed)
            }
        }
        llCrashLog.setOnDebounceClickListener {
            runCatching { showCrashLog() }.onFailure { logE(it) }
        }
        initClearCache()
    }


    private fun initClearCache() {
        viewBinding.llClearCache.setOnDebounceClickListener {
            runCatching { showClearCacheDialog() }.onFailure { logE(it) }
        }
    }

    private fun showClearCacheDialog() {
        val context = requireContext().applicationContext
        viewLifecycleScope.launch {
            val imgSize = withContext(Dispatchers.IO) {
                cacheDirSize(context.cacheDir) + cacheDirSize(File(context.cacheDir, "image_manager_disk_cache"))
            }
            val apkSize = withContext(Dispatchers.IO) {
                cacheDirSize(context.getExternalFilesDir("apk"))
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_clear_cache)
                .setMessage(
                    getString(
                        R.string.clear_cache_summary_format,
                        formatSize(imgSize),
                        formatSize(apkSize)
                    )
                )
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear) { _, _ ->
                    viewLifecycleScope.launch {
                        val cleared = withContext(Dispatchers.IO) {
                            runCatching { GlideApp.get(requireContext()).clearDiskCache() }
                                .onFailure { logE(it) }
                            var total = 0L
                            total += cacheDirSize(context.cacheDir)
                            deleteDirContents(File(context.cacheDir, "image_manager_disk_cache"))
                            WallpaperStore.clearLegacyCache(context)
                            total += cacheDirSize(context.getExternalFilesDir("apk"))
                            deleteDirContents(context.getExternalFilesDir("apk"))
                            total
                        }
                        withContext(Dispatchers.Main) {
                            showToast(getString(R.string.clear_cache_done_format, formatSize(cleared)))
                        }
                    }
                }
                .show()
        }
    }

    private fun cacheDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun deleteDirContents(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1024.0 / 1024 / 1024)
        bytes >= 1024L * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024)
        bytes >= 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun showCrashLog() {
        val context = requireContext()
        val logs = context.getExternalFilesDir(null)
            ?.listFiles { file -> file.name.endsWith(".log") }
            ?.sortedByDescending { it.name }
            .orEmpty()
        if (logs.isEmpty()) {
            showToast(R.string.crash_log_empty)
            return
        }
        val latest = logs.first()
        val text = runCatching { latest.readText() }.getOrDefault("")
        if (text.isBlank()) {
            showToast(R.string.crash_log_empty)
            return
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(latest.name)
            .setMessage(text.takeLast(4000))
            .setPositiveButton(R.string.copy_all, null)
            .setNegativeButton(R.string.export_log, null)
            .setNeutralButton(R.string.close, null)
            .show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.let { button ->
            UpdateChecker.styleSecondaryButton(context, button)
            button.setOnClickListener {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("crash_log", text)
                )
                showToast(R.string.copied)
            }
        }
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.let { button ->
            UpdateChecker.styleSecondaryButton(context, button)
            button.setOnClickListener {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_SUBJECT, latest.name)
                    .putExtra(android.content.Intent.EXTRA_TEXT, text)
                runCatching {
                    startActivity(android.content.Intent.createChooser(send, latest.name))
                }.onFailure { logE(it) }
            }
        }
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)?.let {
            UpdateChecker.styleSecondaryButton(context, it)
        }
    }

    override fun onResume() {
        super.onResume()
        viewBinding.tvAppearanceValue.setText(
            when (ThemeMode.current()) {
                ThemeMode.LIGHT -> R.string.theme_light
                ThemeMode.DARK -> R.string.theme_dark
                else -> R.string.theme_auto
            }
        )
        viewBinding.tvUpdateValue.text =
            getString(R.string.current_version_format, BuildConfig.VERSION_NAME)
    }
}
