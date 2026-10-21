package me.kbai.zhenxunui.ui.console

import android.annotation.SuppressLint
import android.graphics.PointF
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import me.kbai.zhenxunui.base.BaseFragment
import me.kbai.zhenxunui.databinding.FragmentBackendLogBinding
import me.kbai.zhenxunui.extends.launchAndCollectIn
import me.kbai.zhenxunui.extends.runWithoutReturn
import me.kbai.zhenxunui.viewmodel.ConsoleViewModel
import kotlin.math.abs

/**
 * @author Sean on 2026/10/21
 */
class BackendLogFragment : BaseFragment<FragmentBackendLogBinding>() {

    private val mViewModel by viewModels<ConsoleViewModel>()

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentBackendLogBinding =
        FragmentBackendLogBinding.inflate(inflater, container, false)

    @SuppressLint("ClickableViewAccessibility")
    override fun initView() = viewBinding.runWithoutReturn {
        tvLogs.movementMethod = ScrollingMovementMethod.getInstance()
        var lastPoint: PointF? = null
        tvLogs.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastPoint = PointF(event.x, event.y)
                    v.parent.requestDisallowInterceptTouchEvent(true)
                }

                MotionEvent.ACTION_MOVE -> lastPoint?.let {
                    if (abs(event.y - it.y) > 10) {
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    it.set(event.x, event.y)
                }

                MotionEvent.ACTION_UP -> {
                    lastPoint = null
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
    }

    override fun initData() = viewBinding.runWithoutReturn {
        mViewModel.botLogs.launchAndCollectIn(this@BackendLogFragment) { data ->
            tvLogs.run {
                text = data.obj
                post {
                    val y = lineCount * lineHeight - height
                    if (y > height) {
                        scrollTo(0, y)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mViewModel.openWebSocket()
    }

    override fun onStop() {
        super.onStop()
        mViewModel.closeWebSocket()
    }
}
