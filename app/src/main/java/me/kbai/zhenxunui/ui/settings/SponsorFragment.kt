package me.kbai.zhenxunui.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentSponsorBinding
import me.kbai.zhenxunui.extends.runWithoutReturn
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.ui.WebViewActivity

/**
 * @author Sean on 2026/10/23
 */
class SponsorFragment : BaseFragment<FragmentSponsorBinding>() {

    companion object {
        private const val AFDIAN_URL = "https://afdian.com/a/hulihulihuli"
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentSponsorBinding = FragmentSponsorBinding.inflate(inflater, container, false)

    override fun initView() = viewBinding.runWithoutReturn {
        btnAfdian.setOnDebounceClickListener {
            WebViewActivity.start(requireContext(), AFDIAN_URL, getString(R.string.sponsor_afdian))
        }
    }
}
