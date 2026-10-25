package me.kbai.zhenxunui.ui.lab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.base.BaseDialogFragment
import me.kbai.zhenxunui.databinding.DialogLabCropBinding
import me.kbai.zhenxunui.extends.logE
import me.kbai.zhenxunui.extends.setOnDebounceClickListener
import me.kbai.zhenxunui.extends.viewLifecycleScope
import java.io.File

class CropAvatarDialogFragment : BaseDialogFragment() {

    companion object {
        const val ARG_URI = "uri"
        private const val MAX_DIM = 1024
    }

    var onCropped: ((String) -> Unit)? = null

    private lateinit var mBinding: DialogLabCropBinding
    private var mSourceBitmap: Bitmap? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = DialogLabCropBinding.inflate(inflater, container, false)
        .also { mBinding = it }
        .root

    override fun Window.setDialogSize() {
        setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uri = arguments?.getString(ARG_URI)
        if (uri == null) {
            dismissAllowingStateLoss()
            return
        }
        viewLifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeBitmap(Uri.parse(uri)) }
            if (bitmap == null || !isAdded) {
                dismissAllowingStateLoss()
                return@launch
            }
            mSourceBitmap = bitmap
            mBinding.cropView.setCropBitmap(bitmap)
        }
        mBinding.btnCancel.setOnDebounceClickListener { dismissAllowingStateLoss() }
        mBinding.btnConfirm.setOnDebounceClickListener { confirm() }
    }

    private fun confirm() {
        val cropped = mBinding.cropView.getCroppedBitmap()
        if (cropped == null) {
            dismissAllowingStateLoss()
            return
        }
        viewLifecycleScope.launch {
            val path = withContext(Dispatchers.IO) { saveBitmap(cropped) }
            if (isAdded) {
                if (path != null) onCropped?.invoke(path)
                dismissAllowingStateLoss()
            }
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? = runCatching {
        val resolver = requireContext().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_DIM || bounds.outHeight / (sample * 2) >= MAX_DIM) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.onFailure { logE(it) }.getOrNull()

    private fun saveBitmap(bitmap: Bitmap): String? = runCatching {
        val dir = File(requireContext().filesDir, "lab_avatars").apply { mkdirs() }
        val file = File(dir, "av_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        file.absolutePath
    }.onFailure { logE(it) }.getOrNull()
}
