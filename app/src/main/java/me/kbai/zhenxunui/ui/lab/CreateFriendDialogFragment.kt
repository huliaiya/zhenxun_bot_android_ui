package me.kbai.zhenxunui.ui.lab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseDialogFragment
import me.kbai.zhenxunui.databinding.DialogLabCreateFriendBinding
import me.kbai.zhenxunui.extends.dp
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.model.LabConversation
import me.kbai.zhenxunui.tool.GlobalToast
import me.kbai.zhenxunui.tool.LabAvatarResolver
import me.kbai.zhenxunui.tool.LabStore
import me.kbai.zhenxunui.tool.glide.GlideApp

class CreateFriendDialogFragment : BaseDialogFragment() {

    var onCreated: ((LabConversation) -> Unit)? = null

    private lateinit var mBinding: DialogLabCreateFriendBinding
    private var mAvatar: String = LabAvatarResolver.BUILTIN_FRIEND
    private var mBotAvatar: String = LabAvatarResolver.BUILTIN_BOT
    private val mAvatarPicker by lazy { LabAvatarPicker(this) }

    init {
        maxWidth = 320.dp.toInt()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = DialogLabCreateFriendBinding.inflate(inflater, container, false)
        .also { mBinding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LabStore.ensureInit(requireContext())
        mBinding.run {
            etBotName.setText(LabStore.defaultBotName())
            ivAvatar.setOnDebounceClickListener {
                mAvatarPicker.launch { path ->
                    mAvatar = path
                    GlideApp.with(ivAvatar).load(path).into(ivAvatar)
                }
            }
            ivBotAvatar.setOnDebounceClickListener {
                mAvatarPicker.launch { path ->
                    mBotAvatar = path
                    GlideApp.with(ivBotAvatar).load(path).into(ivBotAvatar)
                }
            }
            btnCancel.setOnDebounceClickListener { dismiss() }
            etName.addTextChangedListener { updateSaveState() }
            etQq.addTextChangedListener { updateSaveState() }
            btnSave.setOnDebounceClickListener { save() }
        }
    }

    private fun updateSaveState(): Unit = mBinding.run {
        btnSave.isEnabled = etName.text?.isNotBlank() == true && etQq.text?.isNotBlank() == true
    }

    private fun save(): Unit = mBinding.run {
        val name = etName.text?.toString()?.trim().orEmpty()
        val qq = etQq.text?.toString()?.trim().orEmpty()
        val botName = etBotName.text?.toString()?.trim().orEmpty()
            .ifEmpty { LabStore.defaultBotName() }
        if (name.isEmpty() || qq.isEmpty()) return
        if (LabStore.isFriendQqExists(qq)) {
            GlobalToast.showToast(getString(R.string.lab_friend_qq_exists))
            return
        }
        val conversation = LabStore.addFriend(name, qq, mAvatar, botName, mBotAvatar)
        onCreated?.invoke(conversation)
        dismiss()
    }
}
