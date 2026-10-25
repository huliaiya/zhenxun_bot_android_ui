package me.kbai.zhenxunui.ui.lab

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.MenuProvider
import androidx.core.widget.addTextChangedListener
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentLabChatBinding
import me.kbai.zhenxunui.extends.launchAndCollectIn
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.extends.viewLifecycleScope
import me.kbai.zhenxunui.model.LabConversation
import me.kbai.zhenxunui.model.LabMessage
import me.kbai.zhenxunui.tool.GlobalToast
import me.kbai.zhenxunui.tool.LabAvatarResolver
import me.kbai.zhenxunui.tool.LabBot
import me.kbai.zhenxunui.tool.LabBotConnection
import me.kbai.zhenxunui.tool.LabStore
import me.kbai.zhenxunui.ui.common.PromptDialog

class LabChatFragment : BaseFragment<FragmentLabChatBinding>() {

    companion object {
        const val ARG_CONVERSATION_ID = "conversationId"
        private const val BOT_USER_ID = "lab_bot"
        private const val LOCAL_REPLY_DELAY_MS = 400L
    }

    private val mAdapter = LabMessageAdapter()

    private var mConversation: LabConversation? = null

    private val mMenuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_lab_chat, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
            R.id.menu_lab_clear_history -> {
                confirmClearHistory()
                true
            }
            else -> false
        }
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentLabChatBinding.inflate(inflater, container, false)

    override fun initView(): Unit = viewBinding.run {
        rvMessage.layoutManager = LinearLayoutManager(requireContext())
        rvMessage.adapter = mAdapter
        etText.addTextChangedListener {
            btnSend.isEnabled = !it.isNullOrEmpty()
        }
        btnSend.setOnDebounceClickListener { send() }
    }

    override fun initData() {
        LabStore.ensureInit(requireContext())
        val conversationId = arguments?.getString(ARG_CONVERSATION_ID)
        val conversation = conversationId?.let { LabStore.findConversation(it) }
        if (conversation == null) {
            GlobalToast.showToast(getString(R.string.lab_conversation_missing))
            findNavController().popBackStack()
            return
        }
        mConversation = conversation
        requireActivity().findViewById<Toolbar>(R.id.toolbar).title = conversation.title
        requireActivity().addMenuProvider(mMenuProvider)
        mAdapter.setData(LabStore.messages(conversation.id))
        mAdapter.onItemLongClick = { message -> confirmDeleteMessage(message) }
        scrollToBottom()

        LabStore.botMessageEvents.launchAndCollectIn(this@LabChatFragment) { message ->
            if (message.conversationId == conversation.id) {
                mAdapter.addData(message)
                scrollToBottom()
            }
        }
    }

    private fun confirmClearHistory() {
        val conversation = mConversation ?: return
        PromptDialog(requireContext())
            .setTitle(getString(R.string.lab_clear_history))
            .setText(getString(R.string.lab_clear_history_confirm))
            .setOnCancelClickListener { it.dismiss() }
            .setOnConfirmClickListener { dialog ->
                dialog.dismiss()
                LabStore.clearMessages(conversation.id)
                mAdapter.setData(emptyList())
                GlobalToast.showToast(getString(R.string.lab_history_cleared))
            }
            .show()
    }

    private fun confirmDeleteMessage(message: LabMessage) {
        PromptDialog(requireContext())
            .setTitle(getString(R.string.lab_delete_message))
            .setText(getString(R.string.lab_delete_message_confirm))
            .setOnCancelClickListener { it.dismiss() }
            .setOnConfirmClickListener { dialog ->
                dialog.dismiss()
                val position = mAdapter.indexOf(message)
                if (position >= 0 && LabStore.deleteMessage(message)) {
                    mAdapter.removeAt(position)
                }
            }
            .show()
    }

    private fun send(): Unit = viewBinding.run {
        val text = etText.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        val conversation = mConversation ?: return
        etText.setText("")

        appendMessage(
            LabMessage(
                conversationId = conversation.id,
                userId = conversation.accountId,
                name = conversation.accountName,
                avatar = conversation.accountAvatar,
                text = text,
                fromBot = false,
                time = System.currentTimeMillis()
            )
        )

        val sentViaBot = LabBotConnection.sendUserMessage(conversation, text)
        if (!sentViaBot) {
            viewLifecycleScope.launch {
                delay(LOCAL_REPLY_DELAY_MS)
                appendMessage(
                    LabMessage(
                        conversationId = conversation.id,
                        userId = BOT_USER_ID,
                        name = LabStore.botNameOf(conversation),
                        avatar = LabStore.botAvatarOf(conversation),
                        text = LabBot.reply(requireContext(), text),
                        fromBot = true,
                        time = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun appendMessage(message: LabMessage) {
        LabStore.appendMessage(message)
        mAdapter.addData(message)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        if (mAdapter.itemCount > 0) {
            viewBinding.rvMessage.scrollToPosition(mAdapter.itemCount - 1)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        runCatching { requireActivity().removeMenuProvider(mMenuProvider) }
    }
}
