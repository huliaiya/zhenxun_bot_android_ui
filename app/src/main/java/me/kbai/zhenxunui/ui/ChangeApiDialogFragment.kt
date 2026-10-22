package me.kbai.zhenxunui.ui

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kbai.zhenxunui.Constants
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseDialogFragment
import me.kbai.zhenxunui.databinding.DialogChangeApiBinding
import me.kbai.zhenxunui.extends.dp
import me.kbai.zhenxunui.extends.hideSoftInputWhenInputDone
import me.kbai.zhenxunui.extends.viewLifecycleScope
import me.kbai.zhenxunui.tool.UpdateChecker
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * @author Sean on 2023/6/1
 */
class ChangeApiDialogFragment : BaseDialogFragment() {
    private lateinit var mBinding: DialogChangeApiBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = DialogChangeApiBinding.inflate(layoutInflater).also { mBinding = it }.root

    init {
        isCancelable = false
        maxWidth = 300.dp.toInt()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mBinding.run {
            UpdateChecker.styleSecondaryButton(root.context, btnBack)
            etApi.run {
                setText(Constants.apiBaseUrl.value)
                hideSoftInputWhenInputDone(btnConfirm)
                addTextChangedListener {
                    if (!it.isNullOrBlank()) {
                        tilApi.isErrorEnabled = false
                    }
                }
            }

            btnBack.setOnClickListener {
                dismiss()
            }

            btnConfirm.setOnClickListener {
                val text = etApi.text
                if (text.isNullOrBlank()) {
                    tilApi.error = view.context.getString(R.string.error_input_api)
                    return@setOnClickListener
                }
                val raw = text.toString().trim()
                val candidate = if (raw.startsWith("http://") || raw.startsWith("https://")) {
                    raw
                } else {
                    //无协议头时默认按https处理, 探测不通会自动回退http
                    "https://$raw"
                }
                if (!Patterns.WEB_URL.matcher(candidate).find()) {
                    tilApi.error = view.context.getString(R.string.error_wrong_api_address)
                    return@setOnClickListener
                }
                btnConfirm.isEnabled = false
                viewLifecycleScope.launch {
                    val working = withContext(Dispatchers.IO) { probeApiUrl(candidate) }
                    btnConfirm.isEnabled = true
                    if (working == null) {
                        tilApi.error = view.context.getString(R.string.error_api_unreachable)
                        return@launch
                    }
                    if (!Constants.updateApiUrl(working)) {
                        tilApi.error = view.context.getString(R.string.error_wrong_api_address)
                        return@launch
                    }
                    dismiss()
                }
            }
        }
    }

    /**
     * Probes the server over https first, then http, and returns the first
     * reachable url. An explicit http:// input is only tried over http.
     */
    private fun probeApiUrl(url: String): String? {
        val candidates = if (url.startsWith("http://")) {
            listOf(url)
        } else {
            listOf(url, url.replaceFirst("https://", "http://"))
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
        for (candidate in candidates) {
            try {
                client.newCall(
                    Request.Builder()
                        .url(candidate + Constants.API_PREFIX + "login")
                        .get()
                        .build()
                ).execute().use { response ->
                    //任何HTTP响应(404/405/401等)都证明服务器可达
                    if (response.code > 0) return candidate
                }
            } catch (_: Exception) {
            }
        }
        return null
    }
}