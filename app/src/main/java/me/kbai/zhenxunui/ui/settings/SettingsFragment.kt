package me.kbai.zhenxunui.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentSettingsBinding
import me.kbai.zhenxunui.extends.runWithoutReturn
import me.kbai.zhenxunui.tool.ThemeMode

/**
 * @author Sean on 2026/10/21
 */
class SettingsFragment : BaseFragment<FragmentSettingsBinding>() {

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSettingsBinding =
        FragmentSettingsBinding.inflate(inflater, container, false)

    override fun initView() = viewBinding.runWithoutReturn {
        val current = ThemeMode.current()
        (when (current) {
            ThemeMode.LIGHT -> rbLight
            ThemeMode.DARK -> rbDark
            else -> rbAuto
        }).isChecked = true

        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rb_light -> ThemeMode.LIGHT
                R.id.rb_dark -> ThemeMode.DARK
                else -> ThemeMode.DEFAULT
            }
            if (mode != ThemeMode.current()) ThemeMode.apply(mode)
        }
    }
}
