package me.kbai.zhenxunui.ui.console

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.databinding.ItemConnectLogBinding
import me.kbai.zhenxunui.model.BotConnectLog

class ConnectLogAdapter(
    private val items: List<BotConnectLog>
) : RecyclerView.Adapter<ConnectLogAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemConnectLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.binding.run {
        val log = items[position]
        tvTime.text = log.connectTime.replace('T', ' ')
        tvBotId.text = log.botId
        val connected = log.type == 1
        tvType.setText(if (connected) R.string.connect_log_on else R.string.connect_log_off)
        tvType.setBackgroundResource(
            if (connected) R.drawable.bg_tag_connect else R.drawable.bg_tag_disconnect
        )
    }

    class ViewHolder(val binding: ItemConnectLogBinding) : RecyclerView.ViewHolder(binding.root)
}
