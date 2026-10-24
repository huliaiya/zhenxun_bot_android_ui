package me.kbai.zhenxunui.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentReleaseHistoryBinding
import me.kbai.zhenxunui.databinding.ItemReleaseBinding
import me.kbai.zhenxunui.extends.logE
import me.kbai.zhenxunui.extends.runWithoutReturn
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.tool.GlobalToast.showToast
import me.kbai.zhenxunui.tool.UpdateChecker

/**
 * @author Sean on 2026/10/21
 */
class ReleaseHistoryFragment : BaseFragment<FragmentReleaseHistoryBinding>() {

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentReleaseHistoryBinding =
        FragmentReleaseHistoryBinding.inflate(inflater, container, false)

    override fun initView() = viewBinding.runWithoutReturn {
        runCatching {
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = ReleaseHistoryAdapter { release ->
                activity?.let { UpdateChecker.showInstallDialog(it, release) }
            }
        }.onFailure { logE(it) }
    }

    override fun initData() {
        loadHistory()
    }

    private fun loadHistory() {
        viewBinding.progressBar.visibility = View.VISIBLE
        viewBinding.tvEmpty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { UpdateChecker.fetchReleases() }
                .onSuccess { releases ->
                    if (viewBinding == null) return@onSuccess
                    viewBinding.progressBar.visibility = View.GONE
                    (viewBinding.recyclerView.adapter as? ReleaseHistoryAdapter)?.submitList(releases)
                    viewBinding.tvEmpty.visibility =
                        if (releases.isEmpty()) View.VISIBLE else View.GONE
                }
                .onFailure {
                    if (viewBinding == null) return@onFailure
                    viewBinding.progressBar.visibility = View.GONE
                    viewBinding.tvEmpty.setText(R.string.update_history_load_failed)
                    viewBinding.tvEmpty.visibility = View.VISIBLE
                    showToast(R.string.update_history_load_failed)
                }
        }
    }
}

class ReleaseHistoryAdapter(
    private val onItemClick: (UpdateChecker.ReleaseInfo) -> Unit
) : RecyclerView.Adapter<ReleaseHistoryAdapter.Holder>() {

    private val items = ArrayList<UpdateChecker.ReleaseInfo>()

    fun submitList(list: List<UpdateChecker.ReleaseInfo>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemReleaseBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemReleaseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.binding.tvReleaseName.text = item.tagName
        holder.binding.tvReleaseDate.text = listOf(item.publishedAt, item.version)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        holder.binding.root.setOnDebounceClickListener { onItemClick(item) }
    }
}
