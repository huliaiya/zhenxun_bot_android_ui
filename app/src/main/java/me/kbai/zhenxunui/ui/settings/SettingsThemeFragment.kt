package me.kbai.zhenxunui.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.SeekBar
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentSettingsThemeBinding
import me.kbai.zhenxunui.extends.runWithoutReturn
import me.kbai.zhenxunui.tool.ThemeMode
import me.kbai.zhenxunui.tool.WallpaperStore

/**
 * @author Sean on 2026/10/21
 */
class SettingsThemeFragment : BaseFragment<FragmentSettingsThemeBinding>() {

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSettingsThemeBinding =
        FragmentSettingsThemeBinding.inflate(inflater, container, false)

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
}
