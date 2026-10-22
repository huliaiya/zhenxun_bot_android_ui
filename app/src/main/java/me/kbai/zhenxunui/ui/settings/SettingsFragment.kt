package me.kbai.zhenxunui.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.kbai.zhenxunui.BuildConfig
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentSettingsBinding
import me.kbai.zhenxunui.extends.logE
import me.kbai.zhenxunui.extends.runWithoutReturn
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.tool.GlobalToast.showToast
import me.kbai.zhenxunui.tool.ThemeMode
import me.kbai.zhenxunui.tool.UpdateChecker
import me.kbai.zhenxunui.tool.WallpaperStore
import java.io.File

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
        llCrashLog.setOnDebounceClickListener {
            runCatching { showCrashLog() }.onFailure { logE(it) }
        }
        initScrimSeekBar()
    }

    private fun initScrimSeekBar() = viewBinding.runWithoutReturn {
        val appContext = requireContext().applicationContext
        val savedAlpha = WallpaperStore.scrimAlpha.value
        val savedPercent = savedAlpha * 100 / 255
        sbWallpaperScrim.progress = savedPercent
        tvScrimValue.text = getString(R.string.scrim_percent_format, savedPercent)
        sbWallpaperScrim.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                tvScrimValue.text = getString(R.string.scrim_percent_format, progress)
                if (fromUser) {
                    WallpaperStore.setScrimAlpha(appContext, progress * 255 / 100)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
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
