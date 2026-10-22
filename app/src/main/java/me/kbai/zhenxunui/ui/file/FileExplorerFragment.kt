package me.kbai.zhenxunui.ui.file

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentFileExplorerBinding
import me.kbai.zhenxunui.databinding.LayoutErrorPageBinding
import me.kbai.zhenxunui.extends.launchAndApiCollectIn
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.model.RemoteFile
import me.kbai.zhenxunui.repository.Resource
import me.kbai.zhenxunui.tool.GlobalToast
import me.kbai.zhenxunui.ui.common.EditTextDialogFragment
import me.kbai.zhenxunui.ui.common.PromptDialog
import me.kbai.zhenxunui.viewmodel.FileExplorerViewModel

/**
 * @author Sean on 2023/5/30
 */
class FileExplorerFragment : BaseFragment<FragmentFileExplorerBinding>() {

    companion object {
        const val ARGS_FILE = "FILE"

        private var mRenamingFile: RemoteFile? = null
        private var mEditingFile: RemoteFile? = null
    }

    private val mViewModel by viewModels<FileExplorerViewModel>()

    private var mMenuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.file_explorer_menu_items, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
            R.id.menu_new_file -> {
                EditTextDialogFragment.newInstance(
                    R.string.new_file, "", EditTextDialogFragment.NEW_FILE
                ).show(childFragmentManager)
                true
            }

            R.id.menu_new_folder -> {
                EditTextDialogFragment.newInstance(
                    R.string.new_folder, "", EditTextDialogFragment.NEW_FOLDER
                ).show(childFragmentManager)
                true
            }

            else -> false
        }
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentFileExplorerBinding = FragmentFileExplorerBinding.inflate(inflater, container, false)

    override fun initView(): Unit = viewBinding.run {
        root.setOnRefreshListener {
            mViewModel.readDir()
        }
    }

    override fun initData() {
        mViewModel.file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARGS_FILE, RemoteFile::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable(ARGS_FILE)
        }

        mViewModel.dir.observe(viewLifecycleOwner) {
            when (it.status) {
                Resource.Status.LOADING -> viewBinding.icError.loading()
                Resource.Status.FAIL -> {
                    viewBinding.root.isRefreshing = false
                    viewBinding.icError.error(it.message)
                }

                Resource.Status.SUCCESS -> {
                    viewBinding.root.isRefreshing = false
                    if (it.data != null) {
                        viewBinding.icError.success()
                        viewBinding.rvDirs.adapter = initFileListAdapter(it.data)
                    }
                }
            }
        }

        EditTextDialogFragment.result.observe(viewLifecycleOwner) {
            if (it.isConsumed) return@observe
            val text = it.get() ?: return@observe
            when (it.id) {
                EditTextDialogFragment.NEW_FILE -> newFile(text)
                EditTextDialogFragment.NEW_FOLDER -> newFolder(text)
                EditTextDialogFragment.RENAME -> rename(text)
                EditTextDialogFragment.EDIT_FILE -> editFile(text)
            }
        }

        if (mViewModel.dir.value == null) {
            mViewModel.readDir()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(mMenuProvider)
    }

    private fun newFile(name: String) {
        val file = mViewModel.file ?: RemoteFile(false, "", null)
        val dialog = EditTextDialogFragment.dialogMap[EditTextDialogFragment.NEW_FILE]?.get()

        mViewModel.createNewFile(name, file.getPath())
            .launchAndApiCollectIn(viewLifecycleOwner, dialog?.binding?.btnConfirm) {
                if (it.status == Resource.Status.LOADING) return@launchAndApiCollectIn
                GlobalToast.showToast(it.data ?: it.message)
                dialog?.dismiss()
                viewBinding.root.isRefreshing = true
                mViewModel.readDir()
            }
    }

    private fun newFolder(name: String) {
        val file = mViewModel.file ?: RemoteFile(false, "", null)
        val dialog = EditTextDialogFragment.dialogMap[EditTextDialogFragment.NEW_FOLDER]?.get()

        mViewModel.createNewFolder(name, file.getPath())
            .launchAndApiCollectIn(viewLifecycleOwner, dialog?.binding?.btnConfirm) {
                if (it.status == Resource.Status.LOADING) return@launchAndApiCollectIn
                GlobalToast.showToast(it.data ?: it.message)
                dialog?.dismiss()
                viewBinding.root.isRefreshing = true
                mViewModel.readDir()
            }
    }

    private fun rename(name: String) {
        val file = mRenamingFile ?: return
        val dialog = EditTextDialogFragment.dialogMap[EditTextDialogFragment.RENAME]?.get()

        mViewModel.renameFile(file, name)
            .launchAndApiCollectIn(viewLifecycleOwner, dialog?.binding?.btnConfirm) {
                if (it.status == Resource.Status.LOADING) return@launchAndApiCollectIn
                GlobalToast.showToast(it.data ?: it.message)
                dialog?.dismiss()
                viewBinding.root.isRefreshing = true
                mViewModel.readDir()
            }
    }

    private fun editFile(text: String) {
        val file = mEditingFile ?: return
        val dialog = EditTextDialogFragment.dialogMap[EditTextDialogFragment.EDIT_FILE]?.get()

        mViewModel.editFile(file, text)
            .launchAndApiCollectIn(viewLifecycleOwner, dialog?.binding?.btnConfirm) {
                if (it.status == Resource.Status.LOADING) return@launchAndApiCollectIn
                GlobalToast.showToast(it.data ?: it.message)
                dialog?.dismiss()
            }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFileListAdapter(data: List<RemoteFile>) = FileListLineAdapter(
        mViewModel.file != null, data
    ).apply {
        onItemClickListener = { file: RemoteFile ->
            if (file.isFile) {
                viewBinding.root.setOnTouchListener { _, _ -> true }
                viewBinding.icLoading.root.isVisible = true

                mViewModel.readFile(file).launchAndApiCollectIn(viewLifecycleOwner) {
                    if (it.status == Resource.Status.LOADING) return@launchAndApiCollectIn

                    viewBinding.root.setOnTouchListener(null)
                    viewBinding.icLoading.root.isVisible = false

                    //这里服务端返回数据有问题, 非文本内容读取失败后状态仍为success但数据为空
                    if (it.success() && it.data != null) {
                        //EditText内容过多会导致IME崩溃(Binder传输限制), 限制到约500K以内
                        if (it.data.length > 250_000) {
                            GlobalToast.showToast(R.string.text_too_long)
                            PromptDialog(requireContext())
                                .setText(it.data)
                                .show()
                            return@launchAndApiCollectIn
                        }
                        mEditingFile = file
                        EditTextDialogFragment.newInstance(
                            R.string.edit_file,
                            it.data,
                            EditTextDialogFragment.EDIT_FILE,
                            Int.MAX_VALUE
                        ).show(childFragmentManager)
                    } else {
                        GlobalToast.showToast(it.message)
                    }
                }
            } else {
                findNavController().navigate(
                    R.id.action_fileExplorerFragment_self,
                    Bundle().apply { putParcelable(ARGS_FILE, file) }
                )
            }
        }

        onBackPressClickListener = { findNavController().popBackStack() }

        onItemLongClickListener = { file ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(file.name)
                .setItems(
                    arrayOf(getString(R.string.rename), getString(R.string.delete))
                ) { _, which ->
                    when (which) {
                        0 -> showRenameDialog(file)
                        1 -> showDeleteDialog(file)
                    }
                }
                .show()
        }
    }

    private fun showRenameDialog(file: RemoteFile) {
        mRenamingFile = file
        EditTextDialogFragment
            .newInstance(R.string.rename, file.name, EditTextDialogFragment.RENAME)
            .show(childFragmentManager)
    }

    private fun showDeleteDialog(file: RemoteFile) {
        PromptDialog(requireContext())
            .setText(R.string.prompt_delete_file)
            .setOnConfirmClickListener { button, dialog ->
                mViewModel.deleteFile(file).launchAndApiCollectIn(viewLifecycleOwner, button) {
                    if (it.status == Resource.Status.LOADING) return@launchAndApiCollectIn
                    if (it.status == Resource.Status.SUCCESS) {
                        GlobalToast.showToast(getString(R.string.delete_success_format, file.name))
                    } else {
                        GlobalToast.showToast(it.data ?: it.message)
                    }
                    dialog.dismiss()
                    viewBinding.root.isRefreshing = true
                    mViewModel.readDir()
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().removeMenuProvider(mMenuProvider)
    }

    private fun LayoutErrorPageBinding.loading() {
        root.isVisible = false
//        btnRetry.isVisible = false
//        tvText.setText(R.string.loading)
//        ivImage.setImageResource(R.drawable.ic_loading)
//        root.setBackgroundResource(R.color.dn_white)
    }

    private fun LayoutErrorPageBinding.error(msg: String) {
        root.isVisible = true
        btnRetry.isVisible = true
        btnRetry.setOnDebounceClickListener {
            mViewModel.readDir()
            loading()
        }
        tvText.text = msg
        ivImage.setImageResource(R.drawable.ic_error)
    }

    private fun LayoutErrorPageBinding.success() {
        root.isVisible = false
    }
}