package me.kbai.zhenxunui.ui.lab

import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.databinding.ItemMessageReceiveBinding
import me.kbai.zhenxunui.databinding.ItemMessageTextBinding
import me.kbai.zhenxunui.extends.dp
import me.kbai.zhenxunui.model.LabMessage
import me.kbai.zhenxunui.tool.LabAvatarResolver
import me.kbai.zhenxunui.tool.glide.GlideApp

class LabMessageAdapter : RecyclerView.Adapter<LabMessageAdapter.ViewHolder>() {

    private val mData = mutableListOf<LabMessage>()

    var onItemLongClick: ((LabMessage) -> Unit)? = null

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
        if (mData[position].fromBot) TYPE_RECEIVE else TYPE_SEND

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mData[position]
        holder.binding.run {
            GlideApp.with(ivAvatar)
                .load(LabAvatarResolver.resolve(item.avatar))
                .into(ivAvatar)
            tvNickname.text = item.name
            llMessage.apply {
                removeAllViews()
                if (item.reply != null) {
                    addView(quoteView(context, item.reply))
                }
                if (item.text.isNotEmpty()) {
                    ItemMessageTextBinding.inflate(
                        LayoutInflater.from(context), this, true
                    ).apply {
                        root.text = item.text
                    }
                }
                item.images?.forEach { url ->
                    addView(mediaView(context, url, true))
                }
                item.videos?.forEach { url ->
                    addView(mediaView(context, url, false))
                }
            }
            root.setOnLongClickListener {
                onItemLongClick?.invoke(item)
                true
            }
        }
    }

    private fun quoteView(context: android.content.Context, reply: String): TextView {
        val view = TextView(context)
        view.background =
            androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_lab_quote)
        view.setPadding(8.dp.toInt(), 4.dp.toInt(), 8.dp.toInt(), 4.dp.toInt())
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
        view.setTextColor(
            androidx.core.content.ContextCompat.getColor(context, R.color.text_gray)
        )
        view.maxLines = 3
        view.ellipsize = android.text.TextUtils.TruncateAt.END
        view.text = reply
        return view
    }

    private fun mediaView(
        context: android.content.Context,
        url: String,
        isImage: Boolean
    ): View = if (isImage) {
        ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(150.dp.toInt(), 150.dp.toInt()).apply {
                topMargin = 8.dp.toInt()
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            GlideApp.with(this).load(url).into(this)
            setOnClickListener { openExternally(context, url) }
        }
    } else {
        TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp.toInt() }
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            setTextColor(0xFF1E88E5.toInt())
            text = context.getString(R.string.lab_view_video)
            setOnClickListener { openExternally(context, url) }
        }
    }

    private fun openExternally(context: android.content.Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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

    fun removeAt(position: Int) {
        if (position < 0 || position >= mData.size) return
        mData.removeAt(position)
        notifyItemRemoved(position)
    }

    fun indexOf(message: LabMessage): Int {
        if (message.time > 0) {
            val exact = mData.indexOfFirst {
                it.time == message.time &&
                    it.userId == message.userId &&
                    it.text == message.text &&
                    it.fromBot == message.fromBot
            }
            if (exact >= 0) return exact
        }
        return mData.indexOf(message)
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
