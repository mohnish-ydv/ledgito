package com.mohnishraj.goldmineledger

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class AttachmentListDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val names = arguments?.getStringArrayList("names").orEmpty()
        val paths = arguments?.getStringArrayList("paths").orEmpty()
        val mimes = arguments?.getStringArrayList("mimes").orEmpty()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Attachments")
            .setItems(names.toTypedArray()) { _, index ->
                val file = paths.getOrNull(index)?.let(::File)
                if (file == null || !file.exists()) {
                    Toast.makeText(requireContext(), "Attachment file is missing", Toast.LENGTH_LONG).show()
                    return@setItems
                }
                runCatching {
                    val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.files", file)
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimes.getOrNull(index) ?: "application/octet-stream")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Open attachment"))
                }.onFailure { Toast.makeText(requireContext(), "No app can open this attachment", Toast.LENGTH_LONG).show() }
            }
            .setPositiveButton("Close", null)
            .create()
    }

    companion object {
        fun newInstance(items: List<AttachmentEntity>) = AttachmentListDialog().apply {
            arguments = Bundle().apply {
                putStringArrayList("names", ArrayList(items.map { it.displayName }))
                putStringArrayList("paths", ArrayList(items.map { it.localPath }))
                putStringArrayList("mimes", ArrayList(items.map { it.mimeType }))
            }
        }
    }
}
