package me.kbai.zhenxunui.ui.zxconsole

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.kbai.zhenxunui.databinding.ItemBlockModuleBinding
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.model.ModuleItem

/**
 * 真寻控制台的全局禁用模块列表, 勾选表示禁用
 */
class BlockModuleAdapter(
    items: List<ModuleItem>,
    blockedModules: Collection<String>,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<BlockModuleAdapter.ViewHolder>() {

    private val mAllItems: List<ModuleItem> = items
    private val mVisibleItems: MutableList<ModuleItem> = items.toMutableList()
    private val mBlocked: MutableSet<String> = blockedModules.toMutableSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemBlockModuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = mVisibleItems.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.binding.run {
        val item = mVisibleItems[position]
        tvName.text = item.name
        tvModule.text = item.module
        cbBlock.setOnCheckedChangeListener(null)
        cbBlock.isChecked = item.module in mBlocked
        cbBlock.setOnCheckedChangeListener { _, checked ->
            if (checked) mBlocked.add(item.module) else mBlocked.remove(item.module)
            onChanged.invoke()
        }
        root.setOnDebounceClickListener { cbBlock.toggle() }
    }

    fun filter(query: String) {
        val keyword = query.trim()
        mVisibleItems.clear()
        mVisibleItems += if (keyword.isEmpty()) {
            mAllItems
        } else {
            mAllItems.filter {
                it.name.contains(keyword, ignoreCase = true) ||
                    it.module.contains(keyword, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    fun getBlockedModules(): Set<String> = mBlocked.toSet()

    class ViewHolder(val binding: ItemBlockModuleBinding) : RecyclerView.ViewHolder(binding.root)
}
