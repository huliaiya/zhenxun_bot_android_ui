package me.kbai.zhenxunui.ui.group

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.databinding.ItemFriendListBinding
import me.kbai.zhenxunui.databinding.ItemFriendListClassifyBinding
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.model.FriendListItem
import me.kbai.zhenxunui.model.GroupListItem
import me.kbai.zhenxunui.tool.glide.GlideApp

/**
 * @author Sean on 2023/6/7
 */
class FriendListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val TYPE_GROUP_CLASSIFY = 0
        private const val TYPE_FRIEND_CLASSIFY = 1
        private const val TYPE_GROUP = 2
        private const val TYPE_FRIEND = 3
    }

    var groupData: List<GroupListItem> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var friendData: List<FriendListItem> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var onGroupClickListener: ((GroupListItem) -> Unit)? = null
    var onFriendClickListener: ((FriendListItem) -> Unit)? = null

    var expandGroup = true
    var expandFriend = true

    class ItemViewHolder(val binding: ItemFriendListBinding) :
        RecyclerView.ViewHolder(binding.root)

    class ClassifyViewHolder(val binding: ItemFriendListClassifyBinding) :
        RecyclerView.ViewHolder(binding.root)

    private fun friendClassifyPosition() = (if (expandGroup) groupData.size else 0) + 1

    override fun getItemCount() = 2 +
            (if (expandGroup) groupData.size else 0) +
            (if (expandFriend) friendData.size else 0)

    override fun getItemViewType(position: Int): Int {
        val friendClassifyPos = friendClassifyPosition()
        return when {
            position == 0 -> TYPE_GROUP_CLASSIFY
            position < friendClassifyPos -> TYPE_GROUP
            position == friendClassifyPos -> TYPE_FRIEND_CLASSIFY
            else -> TYPE_FRIEND
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        TYPE_GROUP_CLASSIFY, TYPE_FRIEND_CLASSIFY -> ClassifyViewHolder(
            ItemFriendListClassifyBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

        else -> ItemViewHolder(
            ItemFriendListBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            TYPE_GROUP_CLASSIFY -> (holder as ClassifyViewHolder).onBindGroup()
            TYPE_FRIEND_CLASSIFY -> (holder as ClassifyViewHolder).onBindFriend()
            TYPE_GROUP -> (holder as ItemViewHolder).onBindGroup(groupData[position - 1])
            TYPE_FRIEND -> (holder as ItemViewHolder).onBindFriend(
                friendData[position - friendClassifyPosition() - 1]
            )
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun ClassifyViewHolder.onBindGroup() = binding.run {
        tvClassify.setText(R.string.groups)
        root.setOnClickListener {
            expandGroup = !expandGroup
            ivArrow.rotation = if (expandGroup) 0f else -90f
            notifyDataSetChanged()
        }
        ivArrow.rotation = if (expandGroup) 0f else -90f
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun ClassifyViewHolder.onBindFriend() = binding.run {
        tvClassify.setText(R.string.friends)
        root.setOnClickListener {
            expandFriend = !expandFriend
            ivArrow.rotation = if (expandFriend) 0f else -90f
            notifyDataSetChanged()
        }
        ivArrow.rotation = if (expandFriend) 0f else -90f
    }

    private fun ItemViewHolder.onBindGroup(item: GroupListItem) = binding.run {
        GlideApp.with(ivAvatar)
            .load(item.avatarUrl)
            .into(ivAvatar)
        tvNickname.text = item.groupName
        tvId.text = item.groupId
        root.setOnDebounceClickListener { onGroupClickListener?.invoke(item) }
    }

    private fun ItemViewHolder.onBindFriend(item: FriendListItem) = binding.run {
        GlideApp.with(ivAvatar)
            .load(item.avatarUrl)
            .into(ivAvatar)
        tvNickname.text = item.nickname
        tvId.text = item.userId
        root.setOnDebounceClickListener { onFriendClickListener?.invoke(item) }
    }
}
