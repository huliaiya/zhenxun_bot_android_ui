package me.kbai.zhenxunui.ui.console

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.databinding.ItemConnectLogBinding
import me.kbai.zhenxunui.model.BotConnectLog

class ConnectLogAdapter :
    ListAdapter<BotConnectLog, ConnectLogAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemConnectLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.binding.run {
        val log = getItem(position)
        tvTime.text = log.connectTime.replace('T', ' ')
        tvBotId.text = log.botId
        val connected = log.type == 1
        tvType.setText(if (connected) R.string.connect_log_on else R.string.connect_log_off)
        tvType.setBackgroundResource(
            if (connected) R.drawable.bg_tag_connect else R.drawable.bg_tag_disconnect
        )
    }

    class ViewHolder(val binding: ItemConnectLogBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BotConnectLog>() {
            override fun areItemsTheSame(oldItem: BotConnectLog, newItem: BotConnectLog) =
                oldItem.connectTime == newItem.connectTime && oldItem.botId == newItem.botId

            override fun areContentsTheSame(oldItem: BotConnectLog, newItem: BotConnectLog) =
                oldItem == newItem
        }
    }
}
