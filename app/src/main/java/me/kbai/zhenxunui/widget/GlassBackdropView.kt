package me.kbai.zhenxunui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import me.kbai.zhenxunui.R

/**
 * Renders a soft, blurred-looking gradient backdrop. Translucent glass surfaces drawn on top
 * of this view get the frosted glass look without paying the cost of a real-time blur pass.
 */
class GlassBackdropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var baseShader: Shader? = null
    private var blobs: List<Blob> = emptyList()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildShaders(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthF = width.toFloat()
        val heightF = height.toFloat()
        if (widthF <= 0f || heightF <= 0f) return

        baseShader?.let {
            paint.shader = it
            canvas.drawRect(0f, 0f, widthF, heightF, paint)
        }

        blobs.forEach { blob ->
            paint.shader = blob.shader
            canvas.drawOval(blob.bounds, paint)
        }
        paint.shader = null
    }

    private fun buildShaders(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return

        baseShader = LinearGradient(
            0f, 0f, 0f, height,
            context.getColor(R.color.glass_backdrop_top),
            context.getColor(R.color.glass_backdrop_bottom),
            Shader.TileMode.CLAMP
        )

        val maxSize = maxOf(width, height)
        blobs = listOf(
            blob(width * 0.10f, height * 0.26f, maxSize * 0.50f, R.color.glass_blob_pink),
            blob(width * 0.95f, height * 0.34f, maxSize * 0.44f, R.color.glass_blob_teal),
            blob(width * 0.92f, height * 0.80f, maxSize * 0.45f, R.color.glass_blob_violet),
            blob(width * 0.05f, height * 0.94f, maxSize * 0.40f, R.color.glass_blob_blue)
        )
    }

    private fun blob(cx: Float, cy: Float, radius: Float, colorRes: Int): Blob {
        val shader = RadialGradient(
            cx, cy, radius,
            context.getColor(colorRes),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        return Blob(shader, RectF(cx - radius, cy - radius, cx + radius, cy + radius))
    }

    private data class Blob(val shader: Shader, val bounds: RectF)
}
