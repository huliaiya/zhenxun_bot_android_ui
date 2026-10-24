package me.kbai.zhenxunui.ui.console

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentBackendLogBinding
import me.kbai.zhenxunui.extends.launchAndCollectIn
import me.kbai.zhenxunui.extends.runWithoutReturn
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.tool.GlobalToast
import me.kbai.zhenxunui.viewmodel.ConnectLogViewModel
import me.kbai.zhenxunui.viewmodel.ConsoleViewModel

/**
 * @author Sean on 2026/10/21
 */
class BackendLogFragment : BaseFragment<FragmentBackendLogBinding>() {

    private val mViewModel by viewModels<ConsoleViewModel>()
    private val mConnectLogViewModel by viewModels<ConnectLogViewModel>()

    private val mConnectLogAdapter by lazy { ConnectLogAdapter(mConnectLogViewModel.logs.value) }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentBackendLogBinding =
        FragmentBackendLogBinding.inflate(inflater, container, false)

    override fun initView() = viewBinding.run {
        rvConnectLog.layoutManager = LinearLayoutManager(requireContext())
        rvConnectLog.adapter = mConnectLogAdapter
        rvConnectLog.isNestedScrollingEnabled = false
        tvLoadMore.setOnDebounceClickListener { mConnectLogViewModel.requestNextPage() }
    }

    override fun initData() = viewBinding.runWithoutReturn {
        mConnectLogViewModel.logs.launchAndCollectIn(this@BackendLogFragment) { logs ->
            mConnectLogAdapter.notifyDataSetChanged()
            tvLoadMore.isVisible = mConnectLogViewModel.hasMore.value && logs.isNotEmpty()
        }
        mConnectLogViewModel.hasMore.launchAndCollectIn(this@BackendLogFragment) { hasMore ->
            tvLoadMore.isVisible = hasMore && mConnectLogViewModel.logs.value.isNotEmpty()
        }
        mConnectLogViewModel.requestNextPage { GlobalToast.showToast(it) }

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
