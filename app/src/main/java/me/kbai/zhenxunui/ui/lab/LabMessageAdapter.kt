package me.kbai.zhenxunui.ui.lab

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.databinding.ItemMessageReceiveBinding
import me.kbai.zhenxunui.databinding.ItemMessageTextBinding
import me.kbai.zhenxunui.model.LabMessage
import me.kbai.zhenxunui.tool.LabAvatarResolver
import me.kbai.zhenxunui.tool.glide.GlideApp

class LabMessageAdapter : RecyclerView.Adapter<LabMessageAdapter.ViewHolder>() {

    private val mData = mutableListOf<LabMessage>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val layout = if (viewType == TYPE_SEND) {
            R.layout.item_message_send
        } else {
            R.layout.item_message_receive
        }
        return ViewHolder(inflater.inflate(layout, parent, false))
    }

    override fun getItemCount() = mData.size

    override fun getItemViewType(position: Int) =
        if (mData[position].fromBot) TYPE_SEND else TYPE_RECEIVE

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mData[position]
        holder.binding.run {
            GlideApp.with(ivAvatar)
                .load(LabAvatarResolver.resolve(item.avatar))
                .into(ivAvatar)
            tvNickname.text = item.name
            llMessage.apply {
                removeAllViews()
                ItemMessageTextBinding.inflate(
                    LayoutInflater.from(context), this, true
                ).apply {
                    root.text = item.text
                }
            }
        }
    }

    fun setData(data: List<LabMessage>) {
        mData.clear()
        mData.addAll(data)
        notifyDataSetChanged()
    }

    fun addData(message: LabMessage) {
        mData.add(message)
        notifyItemInserted(mData.size - 1)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val binding: ItemMessageReceiveBinding
            get() = ItemMessageReceiveBinding.bind(itemView)
    }

    companion object {
        private const val TYPE_SEND = 1
        private const val TYPE_RECEIVE = 0
    }
}
