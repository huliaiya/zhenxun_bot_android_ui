package me.kbai.zhenxunui.ui.console

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentBackendLogBinding
import me.kbai.zhenxunui.extends.launchAndCollectIn
import me.kbai.zhenxunui.extends.runWithoutReturn
import me.kbai.zhenxunui.viewmodel.ConsoleViewModel

/**
 * @author Sean on 2026/10/21
 */
class BackendLogFragment : BaseFragment<FragmentBackendLogBinding>() {

    private val mViewModel by viewModels<ConsoleViewModel>()

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentBackendLogBinding =
        FragmentBackendLogBinding.inflate(inflater, container, false)

    override fun initView() {
    }

    override fun initData() = viewBinding.runWithoutReturn {
        mViewModel.botLogs.launchAndCollectIn(this@BackendLogFragment) { data ->
            tvLogs.text = data.obj
            nsvLog.post { nsvLog.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onStart() {
        super.onStart()
        mViewModel.openWebSocket()
    }

    override fun onStop() {
        super.onStop()
        mViewModel.closeWebSocket()
    }
}
