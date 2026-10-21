package me.kbai.zhenxunui.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import me.kbai.zhenxunui.BuildConfig
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentUpdateBinding
import me.kbai.zhenxunui.extends.runWithoutReturn
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.tool.UpdateChecker

/**
 * @author Sean on 2026/10/21
 */
class UpdateFragment : BaseFragment<FragmentUpdateBinding>() {

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentUpdateBinding =
        FragmentUpdateBinding.inflate(inflater, container, false)

    override fun initView() = viewBinding.runWithoutReturn {
        llCheckUpdate.setOnDebounceClickListener {
            activity?.let { UpdateChecker.check(it, manual = true) }
        }
        llHistory.setOnDebounceClickListener {
            findNavController().navigate(R.id.action_update_to_history)
        }
        switchAutoUpdate.isChecked = UpdateChecker.isAutoUpdateEnabled(requireContext())
        switchAutoUpdate.setOnCheckedChangeListener { _, checked ->
            UpdateChecker.setAutoUpdateEnabled(requireContext(), checked)
        }
    }

    override fun onResume() {
        super.onResume()
        viewBinding.tvCurrentVersion.text =
            getString(R.string.current_version_format, BuildConfig.VERSION_NAME)
    }
}
