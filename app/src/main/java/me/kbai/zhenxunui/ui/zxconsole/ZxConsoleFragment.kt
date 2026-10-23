package me.kbai.zhenxunui.ui.zxconsole

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentZxConsoleBinding
import me.kbai.zhenxunui.extends.apiCollect
import me.kbai.zhenxunui.extends.launchAndCollectIn
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.extends.viewLifecycleScope
import me.kbai.zhenxunui.model.BotBaseInfo
import me.kbai.zhenxunui.model.BotManageUpdate
import me.kbai.zhenxunui.model.BotBlockModule
import me.kbai.zhenxunui.repository.Resource
import me.kbai.zhenxunui.tool.GlobalToast
import me.kbai.zhenxunui.tool.glide.GlideApp
import me.kbai.zhenxunui.viewmodel.MainViewModel
import me.kbai.zhenxunui.viewmodel.ZxConsoleViewModel

/**
 * 真寻控制台, 对应webui的/#/command页面: 全局开关+全局禁用被动/插件
 */
class ZxConsoleFragment : BaseFragment<FragmentZxConsoleBinding>() {

    private val mMainViewModel by viewModels<MainViewModel>({ requireActivity() })
    private val mViewModel by viewModels<ZxConsoleViewModel>()

    private var mTaskAdapter: BlockModuleAdapter? = null
    private var mPluginAdapter: BlockModuleAdapter? = null
    private var mLoadedBotId: String? = null
    private var mSwitchChecked = false
    private var mStatusChanging = false

    override fun getViewBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ): FragmentZxConsoleBinding = FragmentZxConsoleBinding.inflate(inflater, container, false)

    override fun initView() = viewBinding.run {
        rvTasks.layoutManager = LinearLayoutManager(requireContext())
        rvTasks.isNestedScrollingEnabled = false
        rvPlugins.layoutManager = LinearLayoutManager(requireContext())
        rvPlugins.isNestedScrollingEnabled = false

        etSearch.doAfterTextChanged { text ->
            mPluginAdapter?.filter(text?.toString().orEmpty())
        }

        swStatus.setOnCheckedChangeListener { _, checked ->
            if (checked != mSwitchChecked) onSwitchToggled(checked)
        }

        btnApply.setOnDebounceClickListener { applySettings() }
    }

    override fun initData() {
        mMainViewModel.currentBot.observe(viewLifecycleOwner) { info ->
            bindBotInfo(info)
            if (info.selfId.isNotBlank() && mLoadedBotId != info.selfId) {
                mLoadedBotId = info.selfId
                mViewModel.requestBlockModule(info.selfId)
            }
        }

        mViewModel.blockModule.launchAndCollectIn(this@ZxConsoleFragment) { res ->
            if (res?.data != null) bindBlockModule(res.data)
        }
    }

    private fun bindBotInfo(info: BotBaseInfo) = viewBinding.run {
        GlideApp.with(ivAvatar)
            .load(info.avatarUrl)
            .into(ivAvatar)
        tvNickname.text = info.nickname
        tvId.text = info.selfId
        mSwitchChecked = info.status
        swStatus.isEnabled = true
        if (swStatus.isChecked != info.status) swStatus.isChecked = info.status
        updateStatusHint(info.status)
    }

    private fun bindBlockModule(data: BotBlockModule) = viewBinding.run {
        if (mTaskAdapter == null) {
            mTaskAdapter = BlockModuleAdapter(data.allTasks, data.blockTasks) {
                updateApplyButtonState()
            }
            rvTasks.adapter = mTaskAdapter
        }
        if (mPluginAdapter == null) {
            mPluginAdapter = BlockModuleAdapter(data.allPlugins, data.blockPlugins) {
                updateApplyButtonState()
            }
            rvPlugins.adapter = mPluginAdapter
            mPluginAdapter?.filter(etSearch.text?.toString().orEmpty())
        }
        updateApplyButtonState()
    }

    private fun updateApplyButtonState() {
        viewBinding.btnApply.isEnabled =
            mTaskAdapter != null || mPluginAdapter != null
    }

    private fun updateStatusHint(enabled: Boolean) {
        viewBinding.tvStatusHint.setText(
            if (enabled) R.string.zx_console_hint_on else R.string.zx_console_hint_off
        )
    }

    private fun onSwitchToggled(target: Boolean) {
        val botId = mLoadedBotId
        if (botId == null || mStatusChanging) {
            viewBinding.swStatus.isChecked = mSwitchChecked
            return
        }
        mStatusChanging = true
        viewLifecycleScope.launch {
            val res = mViewModel.changeBotStatus(botId, target)
                .apiCollect(viewBinding.swStatus)
            mStatusChanging = false
            if (res.status == Resource.Status.SUCCESS) {
                mSwitchChecked = target
                updateStatusHint(target)
            } else {
                viewBinding.swStatus.isChecked = mSwitchChecked
            }
            if (res.message.isNotBlank()) GlobalToast.showToast(res.message)
        }
    }

    private fun applySettings() {
        val botId = mLoadedBotId ?: return
        val taskAdapter = mTaskAdapter ?: return
        val pluginAdapter = mPluginAdapter ?: return
        val update = BotManageUpdate(
            botId,
            pluginAdapter.getBlockedModules().toList(),
            taskAdapter.getBlockedModules().toList()
        )
        viewLifecycleScope.launch {
            val res = mViewModel.updateBotManage(update)
                .apiCollect(viewBinding.btnApply)
            if (res.status == Resource.Status.SUCCESS) {
                GlobalToast.showToast(
                    res.message.ifBlank { getString(R.string.zx_console_apply_success) }
                )
            } else {
                GlobalToast.showToast(res.message)
            }
        }
    }
}
