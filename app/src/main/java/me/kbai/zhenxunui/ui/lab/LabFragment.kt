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
import me.kbai.zhenxunui.tool.LabStore

class LabFragment : BaseFragment<FragmentLabBinding>() {

    private val mAdapter by lazy {
        LabAdapter(
            onCreateGroup = { openConversation(LabStore.addGroup()) },
            onCreateFriend = {
                CreateFriendDialogFragment().apply {
                    onCreated = { openConversation(it) }
                }.show(childFragmentManager, "create_friend")
            },
            onOpenConversation = { openConversation(it) }
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
        LabStore.conversations.launchAndCollectIn(this@LabFragment) { list ->
            bindConversations(list)
        }
    }

    private fun bindConversations(list: List<LabConversation>): Unit = viewBinding.run {
        val items = ArrayList<LabItem>()
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

    private fun openConversation(conversation: LabConversation) {
        findNavController().navigate(
            R.id.action_lab_to_labChat,
            bundleOf(LabChatFragment.ARG_CONVERSATION_ID to conversation.id)
        )
    }
}
