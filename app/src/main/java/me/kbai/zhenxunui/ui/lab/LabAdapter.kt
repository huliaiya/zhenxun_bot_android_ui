package me.kbai.zhenxunui.ui.lab

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.kbai.zhenxunui.databinding.ItemLabConnectionBinding
import me.kbai.zhenxunui.databinding.ItemLabConversationBinding
import me.kbai.zhenxunui.databinding.ItemLabHeaderBinding
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.model.LabConversation
import me.kbai.zhenxunui.tool.LabAvatarResolver
import me.kbai.zhenxunui.tool.glide.GlideApp

sealed class LabItem {
    data class Connection(val statusText: String) : LabItem()
    data class Header(val title: String, val action: String, val isGroup: Boolean) : LabItem()
    data class Row(val conversation: LabConversation) : LabItem()
}

class LabAdapter(
    private val onOpenConnection: () -> Unit,
    private val onCreateGroup: () -> Unit,
    private val onCreateFriend: () -> Unit,
    private val onOpenConversation: (LabConversation) -> Unit,
    private val onDeleteConversation: (LabConversation) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<LabItem>()

    fun submit(list: List<LabItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is LabItem.Connection -> TYPE_CONNECTION
        is LabItem.Header -> TYPE_HEADER
        is LabItem.Row -> TYPE_ROW
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_CONNECTION -> ConnectionHolder(
                ItemLabConnectionBinding.inflate(inflater, parent, false)
            )
            TYPE_HEADER -> HeaderHolder(ItemLabHeaderBinding.inflate(inflater, parent, false))
            else -> RowHolder(ItemLabConversationBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is LabItem.Connection -> (holder as ConnectionHolder).binding.run {
                root.setOnDebounceClickListener { onOpenConnection() }
                tvStatus.text = item.statusText
            }
            is LabItem.Header -> (holder as HeaderHolder).binding.run {
                tvSection.text = item.title
                btnCreate.text = item.action
                btnCreate.setOnDebounceClickListener {
                    if (item.isGroup) onCreateGroup() else onCreateFriend()
                }
            }
            is LabItem.Row -> (holder as RowHolder).binding.run {
                val conversation = item.conversation
                GlideApp.with(ivAvatar)
                    .load(LabAvatarResolver.resolve(conversation.accountAvatar))
                    .into(ivAvatar)
                tvTitle.text = conversation.title
                tvSubtitle.text = conversation.accountId
                root.setOnDebounceClickListener { onOpenConversation(conversation) }
                root.setOnLongClickListener {
                    onDeleteConversation(conversation)
                    true
                }
            }
        }
    }

    class ConnectionHolder(val binding: ItemLabConnectionBinding) :
        RecyclerView.ViewHolder(binding.root)

    class HeaderHolder(val binding: ItemLabHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    class RowHolder(val binding: ItemLabConversationBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val TYPE_CONNECTION = 0
        private const val TYPE_HEADER = 1
        private const val TYPE_ROW = 2
    }
}
