package me.kbai.zhenxunui.ui.settings

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import me.kbai.zhenxunui.BuildConfig
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentAboutBinding
import me.kbai.zhenxunui.extends.logE
import me.kbai.zhenxunui.extends.runWithoutReturn
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.tool.GlobalToast.showToast
import me.kbai.zhenxunui.ui.WebViewActivity

/**
 * @author Sean on 2026/10/23
 */
class AboutFragment : BaseFragment<FragmentAboutBinding>() {

    companion object {
        private const val BUG_GROUP_URL =
            "https://qun.qq.com/universal-share/share?ac=1&authKey=3QrO7Nz38wwywBGbsVvUgb5u%2Fm98uqdbTD2wnH6bwyi%2FGQisrwz3grfekRMKTm5H&busi_data=eyJncm91cENvZGUiOiI1NTk0MDUyNDAiLCJ0b2tlbiI6InBDTUIySCtYdFRZbnY1NU5iZmpmalVEekRDanYwVkcwS2pYSTZmNEVFZzZQUW9mRkxMRGdZV0FVd1VhK0ZCWHEiLCJ1aW4iOiIzODEyODA4NTI1In0%3D&data=T6MDnXKud_dmMoLA9nUiKounsVeXmliuwVw2ZjR6NYFebRp-CQPt7-C2QVM_p2pRKL7AfoLcYO6fcfMmaERF8Q&svctype=4&tempid=h5_group_info"
        private const val GITHUB_ISSUES_URL =
            "https://github.com/huliaiya/zhenxun_bot_android_ui/issues"
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentAboutBinding = FragmentAboutBinding.inflate(inflater, container, false)

    override fun initView() = viewBinding.runWithoutReturn {
        tvVersion.text = getString(R.string.about_version_format, BuildConfig.VERSION_NAME)
        llBugGroup.setOnDebounceClickListener {
            WebViewActivity.start(requireContext(), BUG_GROUP_URL, getString(R.string.about_bug_group))
        }
        llGithubIssue.setOnDebounceClickListener { openUrl(GITHUB_ISSUES_URL) }
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            logE(it)
            showToast(R.string.action_failed)
        }
    }
}
