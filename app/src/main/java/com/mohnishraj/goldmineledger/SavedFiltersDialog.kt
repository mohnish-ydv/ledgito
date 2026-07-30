package com.mohnishraj.goldmineledger

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SavedFiltersDialog : DialogFragment() {
    private val vm get() = (requireActivity() as MainActivity).viewModel

    override fun onCreateDialog(state: Bundle?): Dialog {
        val filters = vm.savedFilters.value
        val rows = buildList {
            add("＋ Save current view")
            filters.forEach { add(it.name) }
        }.toTypedArray()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Saved views")
            .setMessage(if (filters.isEmpty()) "Save a search and its advanced filters for one-tap reuse." else "Apply, rename by saving a new view, or remove an old one.")
            .setItems(rows) { _, which ->
                if (which == 0) showSaveDialog() else showFilterActions(filters[which - 1])
            }
            .setNegativeButton("Close", null)
            .create()
    }

    private fun showSaveDialog() {
        val input = TextInputEditText(requireContext()).apply {
            hint = "e.g. Pending bills"
            maxLines = 1
        }
        val layout = TextInputLayout(requireContext()).apply {
            hint = "View name"
            setPadding(40, 8, 40, 0)
            addView(input, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Save current view")
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                layout.error = null
                vm.saveCurrentFilter(input.text?.toString().orEmpty()) { result ->
                    result.onSuccess {
                        dialog.dismiss()
                        dismiss()
                    }.onFailure { layout.error = it.message ?: "Could not save view" }
                }
            }
        }
        dialog.show()
    }

    private fun showFilterActions(filter: SavedFilterEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(filter.name)
            .setItems(arrayOf("Apply view", "Delete view")) { _, which ->
                if (which == 0) {
                    vm.applySavedFilter(filter)
                    dismiss()
                } else {
                    vm.deleteSavedFilter(filter) { result ->
                        result.onSuccess { dismiss() }
                    }
                }
            }
            .show()
    }
}
