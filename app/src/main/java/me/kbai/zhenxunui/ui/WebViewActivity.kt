package me.kbai.zhenxunui.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseActivity
import me.kbai.zhenxunui.databinding.ActivityWebViewBinding
import me.kbai.zhenxunui.extends.logE
import me.kbai.zhenxunui.extends.runWithoutReturn
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.tool.GlobalToast.showToast

/**
 * 应用内网页容器, 遇到 QQ 等第三方 scheme 时转交给对应应用打开
 *
 * @author Sean on 2026/10/23
 */
class WebViewActivity : BaseActivity<ActivityWebViewBinding>() {

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"

        fun start(context: Context, url: String, title: String) {
            context.startActivity(
                Intent(context, WebViewActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_TITLE, title)
            )
        }
    }

    override fun getViewBinding(inflater: LayoutInflater): ActivityWebViewBinding =
        ActivityWebViewBinding.inflate(inflater)

    @SuppressLint("SetJavaScriptEnabled")
    override fun initView() = viewBinding.runWithoutReturn {
        tvTitle.text = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        ivBack.setOnDebounceClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this@WebViewActivity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (wvContent.canGoBack()) {
                    wvContent.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        wvContent.settings.run {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        wvContent.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = handleUrl(request.url.toString())

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                handleUrl(url)
        }

        intent.getStringExtra(EXTRA_URL)?.let { wvContent.loadUrl(it) }
    }

    override fun onDestroy() {
        viewBinding.wvContent.stopLoading()
        viewBinding.wvContent.destroy()
        super.onDestroy()
    }

    private fun handleUrl(url: String): Boolean {
        if (url.startsWith("intent://", ignoreCase = true)) {
            return runCatching {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                startActivity(intent)
            }.isSuccess
        }
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        // http/https 留在应用内, 其余交给系统(QQ等)
        if (scheme == "http" || scheme == "https") return false
        val result = runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
        if (result.isFailure) {
            logE(result.exceptionOrNull() ?: IllegalStateException("open url failed: $url"))
            showToast(R.string.action_failed)
        }
        return true
    }
}
