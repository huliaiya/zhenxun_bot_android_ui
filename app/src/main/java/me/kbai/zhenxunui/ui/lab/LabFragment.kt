package me.kbai.zhenxunui.ui.lab

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentLabBinding
import me.kbai.zhenxunui.extends.launchAndCollectIn
import me.kbai.zhenxunui.model.LabConversation
import me.kbai.zhenxunui.tool.LabBotConnection
import me.kbai.zhenxunui.tool.LabStore
import me.kbai.zhenxunui.ui.common.PromptDialog

class LabFragment : BaseFragment<FragmentLabBinding>() {

    private var mStatusText: String = ""

    private val mAdapter by lazy {
        LabAdapter(
            onOpenConnection = {
                ConnectionDialogFragment().show(childFragmentManager, "lab_connection")
            },
            onCreateGroup = {
                CreateGroupDialogFragment().apply {
                    onCreated = { openConversation(it) }
                }.show(childFragmentManager, "create_group")
            },
            onCreateFriend = {
                CreateFriendDialogFragment().apply {
                    onCreated = { openConversation(it) }
                }.show(childFragmentManager, "create_friend")
            },
            onOpenConversation = { openConversation(it) },
            onDeleteConversation = { confirmDelete(it) }
        )
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentLabBinding.inflate(inflater, container, false)

    override fun initView(): Unit = viewBinding.run {
        rvLab.layoutManager = LinearLayoutManager(requireContext())
        rvLab.adapter = mAdapter
    }

    override fun initData() {
        LabStore.ensureInit(requireContext())
        LabStore.conversations.launchAndCollectIn(this@LabFragment) { bindList() }
        LabBotConnection.status.launchAndCollectIn(this@LabFragment) { status ->
            mStatusText = when (status) {
                LabBotConnection.Status.Connecting -> getString(R.string.lab_connection_status_connecting)
                LabBotConnection.Status.Connected -> getString(R.string.lab_connection_status_connected)
                is LabBotConnection.Status.Failed -> getString(R.string.lab_connection_status_failed)
                LabBotConnection.Status.Idle -> getString(R.string.lab_connection_status_idle)
            }
            bindList()
        }
    }

    private fun bindList(): Unit = viewBinding.run {
        val list = LabStore.conversations.value
        val items = ArrayList<LabItem>()
        items += LabItem.Connection(mStatusText)
        items += LabItem.Header(
            getString(R.string.lab_groups),
            getString(R.string.lab_create_group),
            true
        )
        items += list.filter { it.isGroup }.map { LabItem.Row(it) }
        items += LabItem.Header(
            getString(R.string.lab_friends),
            getString(R.string.lab_create_friend),
            false
        )
        items += list.filter { !it.isGroup }.map { LabItem.Row(it) }
        mAdapter.submit(items)
        tvLabEmpty.isVisible = list.isEmpty()
    }

    private fun confirmDelete(conversation: LabConversation) {
        PromptDialog(requireContext())
            .setTitle(getString(R.string.delete))
            .setText(getString(R.string.lab_delete_conversation))
            .setOnCancelClickListener { it.dismiss() }
            .setOnConfirmClickListener { dialog ->
                dialog.dismiss()
                LabStore.deleteConversation(conversation.id)
            }
            .show()
    }

    private fun openConversation(conversation: LabConversation) {
        findNavController().navigate(
            R.id.action_lab_to_labChat,
            bundleOf(LabChatFragment.ARG_CONVERSATION_ID to conversation.id)
        )
    }
}
