package me.kbai.zhenxunui.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import me.kbai.zhenxunui.BuildConfig
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentSettingsBinding
import me.kbai.zhenxunui.extends.runWithoutReturn
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.tool.ThemeMode
import me.kbai.zhenxunui.tool.UpdateChecker

/**
 * @author Sean on 2026/10/21
 */
class SettingsFragment : BaseFragment<FragmentSettingsBinding>() {

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSettingsBinding =
        FragmentSettingsBinding.inflate(inflater, container, false)

    override fun initView() = viewBinding.runWithoutReturn {
        llAppearance.setOnDebounceClickListener {
            findNavController().navigate(R.id.action_settings_to_theme)
        }
        llUpdate.setOnDebounceClickListener {
            activity?.let { UpdateChecker.check(it, manual = true) }
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
