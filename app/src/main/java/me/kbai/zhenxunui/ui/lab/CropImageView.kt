package me.kbai.zhenxunui.ui.lab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.max
import kotlin.math.min

class CropImageView(context: Context, attrs: AttributeSet?) : AppCompatImageView(context, attrs) {

    private val mSquare = RectF()
    private val mMatrixValues = FloatArray(9)
    private val mInverse = Matrix()
    private val mTmpRect = RectF()
    private val mDimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
    private val mBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var mBitmap: Bitmap? = null
    private var mLastX = 0f
    private var mLastY = 0f
    private var mInitialized = false
    private val mImageMatrix = Matrix()

    private val mScaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                mImageMatrix.postScale(
                    detector.scaleFactor,
                    detector.scaleFactor,
                    detector.focusX,
                    detector.focusY
                )
                imageMatrix = mImageMatrix
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    fun setCropBitmap(bitmap: Bitmap) {
        mBitmap = bitmap
        setImageBitmap(bitmap)
        mInitialized = false
        requestLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val side = min(w, h) * 0.8f
        val cx = w / 2f
        val cy = h / 2f
        mSquare.set(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        if (!mInitialized && mBitmap != null) {
            resetMatrix()
            mInitialized = true
        }
    }

    private fun resetMatrix() {
        val bitmap = mBitmap ?: return
        val scale = max(mSquare.width() / bitmap.width, mSquare.height() / bitmap.height)
        val dx = mSquare.centerX() - bitmap.width * scale / 2
        val dy = mSquare.centerY() - bitmap.height * scale / 2
        mImageMatrix.reset()
        mImageMatrix.postScale(scale, scale)
        mImageMatrix.postTranslate(dx, dy)
        imageMatrix = mImageMatrix
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        mScaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mLastX = event.x
                mLastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!mScaleDetector.isInProgress) {
                    mImageMatrix.postTranslate(event.x - mLastX, event.y - mLastY)
                    imageMatrix = mImageMatrix
                    mLastX = event.x
                    mLastY = event.y
                }
            }
        }
        return true
    }

    fun getCroppedBitmap(): Bitmap? {
        val bitmap = mBitmap ?: return null
        mImageMatrix.getValues(mMatrixValues)
        mInverse.reset()
        mImageMatrix.invert(mInverse)
        mTmpRect.set(mSquare)
        mInverse.mapRect(mTmpRect)
        val left = max(0f, mTmpRect.left).toInt()
        val top = max(0f, mTmpRect.top).toInt()
        val width = min(bitmap.width - left, mTmpRect.width().toInt()).coerceAtLeast(1)
        val height = min(bitmap.height - top, mTmpRect.height().toInt()).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, left, top, min(width, height), min(width, height))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mSquare.isEmpty) return
        canvas.drawRect(0f, 0f, width.toFloat(), mSquare.top, mDimPaint)
        canvas.drawRect(0f, mSquare.bottom, width.toFloat(), height.toFloat(), mDimPaint)
        canvas.drawRect(0f, mSquare.top, mSquare.left, mSquare.bottom, mDimPaint)
        canvas.drawRect(mSquare.right, mSquare.top, width.toFloat(), mSquare.bottom, mDimPaint)
        canvas.drawRect(mSquare, mBorderPaint)
    }
}
