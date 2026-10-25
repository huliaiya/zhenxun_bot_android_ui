package me.kbai.zhenxunui.ui.lab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import me.kbai.zhenxunui.Constants
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseDialogFragment
import me.kbai.zhenxunui.databinding.DialogLabConnectionBinding
import me.kbai.zhenxunui.extends.dp
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.tool.LabBotConnection
import me.kbai.zhenxunui.tool.LabBotConnectionConfig

class ConnectionDialogFragment : BaseDialogFragment() {

    private lateinit var mBinding: DialogLabConnectionBinding

    init {
        maxWidth = 320.dp.toInt()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = DialogLabConnectionBinding.inflate(inflater, container, false)
        .also { mBinding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val config = LabBotConnection.config()
        mBinding.run {
            etWsUrl.setText(config?.url ?: deriveDefaultUrl())
            etWsToken.setText(config?.token.orEmpty())
            etSelfId.setText(config?.selfId ?: Constants.currentBot?.selfId.orEmpty())
            btnDisconnect.isVisible = config != null
            btnCancel.setOnDebounceClickListener { dismiss() }
            btnSave.setOnDebounceClickListener { save() }
            btnDisconnect.setOnDebounceClickListener {
                LabBotConnection.saveConfig(requireContext(), null)
                dismiss()
            }
        }
    }

    private fun deriveDefaultUrl(): String {
        val base = Constants.apiBaseUrl.value
        if (base.isNullOrBlank()) return "ws://127.0.0.1:8080/onebot/v11/ws"
        val uri = runCatching { java.net.URI(base) }.getOrNull()
            ?: return "ws://127.0.0.1:8080/onebot/v11/ws"
        val scheme = if (uri.scheme == "https") "wss" else "ws"
        val host = uri.host ?: "127.0.0.1"
        val port = if (uri.port > 0) uri.port else 8080
        return "$scheme://$host:$port/onebot/v11/ws"
    }

    private fun save(): Unit = mBinding.run {
        val url = etWsUrl.text?.toString()?.trim().orEmpty()
        if (url.isEmpty()) return
        val token = etWsToken.text?.toString()?.trim().orEmpty()
        val selfId = etSelfId.text?.toString()?.trim().orEmpty()
        LabBotConnection.saveConfig(
            requireContext(),
            LabBotConnectionConfig(url, token, selfId)
        )
        dismiss()
    }
}
