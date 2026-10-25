package me.kbai.zhenxunui.ui.lab

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import me.kbai.zhenxunui.extends.logE

class LabAvatarPicker(private val fragment: DialogFragment) {

    private var mCallback: ((String) -> Unit)? = null

    private val mLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            fragment.requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.onFailure { logE(it) }
        CropAvatarDialogFragment().apply {
            arguments = android.os.Bundle().apply { putString(CropAvatarDialogFragment.ARG_URI, uri.toString()) }
            onCropped = { path ->
                mCallback?.invoke(path)
                mCallback = null
            }
        }.show(fragment.childFragmentManager, "crop_avatar")
    }

    fun launch(onResult: (String) -> Unit) {
        mCallback = onResult
        mLauncher.launch(arrayOf("image/*"))
    }
}
