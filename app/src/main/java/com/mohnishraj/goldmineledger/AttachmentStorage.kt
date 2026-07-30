package com.mohnishraj.goldmineledger

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

class AttachmentStorage(private val context: Context) {
    fun copy(transactionId: String, uriText: String): AttachmentEntity {
        val uri = Uri.parse(uriText)
        val resolver = context.contentResolver
        var displayName = "attachment"
        var reportedSize = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) reportedSize = cursor.getLong(sizeIndex)
            }
        }
        require(reportedSize <= MAX_ATTACHMENT_BYTES) { "Attachments must be 20 MB or smaller" }
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(100).ifBlank { "attachment" }
        val id = Utils.id()
        val folder = File(context.filesDir, "attachments/$transactionId").apply { mkdirs() }
        val destination = File(folder, "${id}_$safeName")
        var copiedBytes = 0L
        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not read $displayName" }
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copiedBytes += count
                        require(copiedBytes <= MAX_ATTACHMENT_BYTES) { "Attachments must be 20 MB or smaller" }
                        output.write(buffer, 0, count)
                    }
                }
            }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
        return AttachmentEntity(
            id = id,
            transactionId = transactionId,
            displayName = displayName.take(120),
            mimeType = resolver.getType(uri) ?: "application/octet-stream",
            localPath = destination.absolutePath,
            sizeBytes = copiedBytes,
            createdAt = System.currentTimeMillis()
        )
    }

    fun delete(items: List<AttachmentEntity>) {
        items.forEach { item ->
            runCatching {
                val file = File(item.localPath)
                val parent = file.parentFile
                file.delete()
                if (parent?.listFiles()?.isEmpty() == true) parent.delete()
            }
        }
    }

    companion object {
        const val MAX_ATTACHMENT_BYTES: Long = 20L * 1024L * 1024L
    }
}
