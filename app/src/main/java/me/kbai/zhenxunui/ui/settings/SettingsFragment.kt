package me.kbai.zhenxunui.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
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
    }

    private fun showCrashLog() {
        val logs = requireContext().getExternalFilesDir(null)
            ?.listFiles { file -> file.name.endsWith(".log") }
            ?.sortedByDescending { it.name }
            .orEmpty()
        if (logs.isEmpty()) {
            showToast(R.string.crash_log_empty)
            return
        }
        val latest = logs.first()
        val text = runCatching { latest.readText() }.getOrDefault("")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(latest.name)
            .setMessage(
                if (text.isBlank()) getString(R.string.crash_log_empty)
                else text.takeLast(4000)
            )
            .setPositiveButton(R.string.cancel, null)
            .show()
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
