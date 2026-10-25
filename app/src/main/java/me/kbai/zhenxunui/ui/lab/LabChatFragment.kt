package me.kbai.zhenxunui.ui.lab

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.addTextChangedListener
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentLabChatBinding
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.extends.viewLifecycleScope
import me.kbai.zhenxunui.model.LabConversation
import me.kbai.zhenxunui.model.LabMessage
import me.kbai.zhenxunui.tool.GlobalToast
import me.kbai.zhenxunui.tool.LabAvatarResolver
import me.kbai.zhenxunui.tool.LabBot
import me.kbai.zhenxunui.tool.LabStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LabChatFragment : BaseFragment<FragmentLabChatBinding>() {

    companion object {
        const val ARG_CONVERSATION_ID = "conversationId"
        private const val BOT_USER_ID = "lab_bot"
        private const val BOT_REPLY_DELAY_MS = 400L
    }

    private val mAdapter = LabMessageAdapter()

    private var mConversation: LabConversation? = null

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
        val history = LabStore.messages(conversation.id)
        mAdapter.setData(history)
        scrollToBottom()
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

        viewLifecycleScope.launch {
            delay(BOT_REPLY_DELAY_MS)
            appendMessage(
                LabMessage(
                    conversationId = conversation.id,
                    userId = BOT_USER_ID,
                    name = getString(R.string.lab_bot_name),
                    avatar = LabAvatarResolver.BUILTIN_BOT,
                    text = LabBot.reply(requireContext(), text),
                    fromBot = true,
                    time = System.currentTimeMillis()
                )
            )
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
}
