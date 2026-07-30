package com.mohnishraj.goldmineledger

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.Date

class TransactionHistoryDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val actions = requireArguments().getStringArrayList("actions").orEmpty()
        val summaries = requireArguments().getStringArrayList("summaries").orEmpty()
        val times = requireArguments().getLongArray("times") ?: longArrayOf()
        val text = if (actions.isEmpty()) "No revision history available." else actions.indices.joinToString("\n\n") { index ->
            val timestamp = times.getOrNull(index)?.let { DateFormat.getDateTimeInstance().format(Date(it)) }.orEmpty()
            "${actions[index].replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }} • $timestamp\n${summaries.getOrNull(index).orEmpty()}"
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Transaction history")
            .setMessage(text)
            .setPositiveButton("Close", null)
            .create()
    }

    companion object {
        fun newInstance(item: TransactionUiModel) = TransactionHistoryDialog().apply {
            arguments = Bundle().apply {
                putStringArrayList("actions", ArrayList(item.revisions.map { it.action }))
                putStringArrayList("summaries", ArrayList(item.revisions.map { it.summary }))
                putLongArray("times", item.revisions.map { it.timestamp }.toLongArray())
            }
        }
    }
}
