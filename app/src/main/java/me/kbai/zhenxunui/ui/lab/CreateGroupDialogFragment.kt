package me.kbai.zhenxunui.ui.lab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseDialogFragment
import me.kbai.zhenxunui.databinding.DialogLabCreateGroupBinding
import me.kbai.zhenxunui.extends.dp
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.model.LabConversation
import me.kbai.zhenxunui.tool.LabAvatarResolver
import me.kbai.zhenxunui.tool.LabStore
import me.kbai.zhenxunui.tool.glide.GlideApp

class CreateGroupDialogFragment : BaseDialogFragment() {

    var onCreated: ((LabConversation) -> Unit)? = null

    private lateinit var mBinding: DialogLabCreateGroupBinding
    private var mBotAvatar: String = LabAvatarResolver.BUILTIN_BOT
    private val mAvatarPicker = LabAvatarPicker(this)

    init {
        maxWidth = 320.dp.toInt()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = DialogLabCreateGroupBinding.inflate(inflater, container, false)
        .also { mBinding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LabStore.ensureInit(requireContext())
        mBinding.run {
            etGroupName.setText(getString(R.string.lab_group_default_name, LabStore.nextGroupIndex()))
            etBotName.setText(LabStore.defaultBotName())
            ivBotAvatar.setOnDebounceClickListener {
                mAvatarPicker.launch { path ->
                    mBotAvatar = path
                    GlideApp.with(ivBotAvatar).load(path).into(ivBotAvatar)
                }
            }
            btnCancel.setOnDebounceClickListener { dismiss() }
            etGroupName.addTextChangedListener { updateSaveState() }
            updateSaveState()
            btnSave.setOnDebounceClickListener { save() }
        }
    }

    private fun updateSaveState(): Unit = mBinding.run {
        btnSave.isEnabled = etGroupName.text?.isNotBlank() == true
    }

    private fun save(): Unit = mBinding.run {
        val title = etGroupName.text?.toString()?.trim().orEmpty()
        val botName = etBotName.text?.toString()?.trim().orEmpty()
            .ifEmpty { LabStore.defaultBotName() }
        if (title.isEmpty()) return
        val conversation = LabStore.addGroup(title, botName, mBotAvatar)
        onCreated?.invoke(conversation)
        dismiss()
    }
}
