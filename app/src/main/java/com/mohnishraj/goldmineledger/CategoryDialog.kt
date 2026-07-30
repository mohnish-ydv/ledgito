package com.mohnishraj.goldmineledger

import android.app.Dialog
import android.os.Bundle
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mohnishraj.goldmineledger.databinding.DialogCategoryBinding

class CategoryDialog : DialogFragment() {
    private lateinit var b: DialogCategoryBinding
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private var parents: List<CategoryEntity> = emptyList()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        b = DialogCategoryBinding.inflate(layoutInflater)
        b.kindInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, CategoryKind.entries.map { it.label }))
        b.iconInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, ICONS))
        b.colourInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, COLOURS.map { it.first }))
        val a = arguments
        val kind = a?.getString("kind")?.let(CategoryKind::from) ?: CategoryKind.EXPENSE
        b.nameInput.setText(a?.getString("name").orEmpty())
        b.kindInput.setText(kind.label, false)
        b.iconInput.setText(a?.getString("icon") ?: "other", false)
        b.archivedSwitch.isChecked = a?.getBoolean("archived", false) ?: false
        val colour = a?.takeIf { it.containsKey("colour") }?.getInt("colour") ?: COLOURS.first().second
        b.colourInput.setText(COLOURS.firstOrNull { it.second == colour }?.first ?: COLOURS.first().first, false)
        b.kindInput.setOnItemClickListener { _, _, _, _ -> refreshParents() }
        refreshParents()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (a?.getString("id") == null) "New category" else "Edit category")
            .setView(b.root).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener { dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { save(dialog) } }
        return dialog
    }

    private fun refreshParents() {
        val kind = CategoryKind.entries.firstOrNull { it.label == b.kindInput.text.toString() } ?: CategoryKind.EXPENSE
        val currentId = arguments?.getString("id")
        parents = vm.categories.value.filter { it.kind == kind.name && it.parentId == null && !it.isArchived && it.id != currentId }
        b.parentInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, listOf("None") + parents.map { it.name }))
        val currentParent = arguments?.getString("parent")
        b.parentInput.setText(parents.firstOrNull { it.id == currentParent }?.name ?: "None", false)
    }

    private fun save(dialog: AlertDialog) {
        val name = b.nameInput.text?.toString().orEmpty()
        Utils.validName(name, 50)?.let { b.nameLayout.error = it; return }
        val kind = CategoryKind.entries.firstOrNull { it.label == b.kindInput.text.toString() } ?: CategoryKind.EXPENSE
        val parentId = parents.firstOrNull { it.name == b.parentInput.text.toString() }?.id
        val colour = COLOURS.firstOrNull { it.first == b.colourInput.text.toString() }?.second ?: COLOURS.first().second
        vm.saveCategory(CategoryDraft(arguments?.getString("id"), name, kind, parentId, b.iconInput.text.toString().ifBlank { "other" }, colour, b.archivedSwitch.isChecked)) {
            it.onSuccess { dialog.dismiss() }.onFailure { e -> Snackbar.make(b.root, e.message ?: "Could not save", Snackbar.LENGTH_LONG).show() }
        }
    }

    companion object {
        private val ICONS = listOf("food", "transport", "shopping", "bills", "health", "education", "entertainment", "salary", "business", "gift", "other")
        private val COLOURS = listOf(
            "Forest" to 0xFF2F6B4F.toInt(), "Ocean" to 0xFF315D8C.toInt(), "Plum" to 0xFF7B4D8E.toInt(),
            "Amber" to 0xFF8A5A00.toInt(), "Brick" to 0xFF9C4138.toInt(), "Slate" to 0xFF59646A.toInt()
        )
        fun newInstance(item: CategoryEntity?) = CategoryDialog().apply {
            arguments = Bundle().apply { if (item != null) {
                putString("id", item.id); putString("name", item.name); putString("kind", item.kind)
                putString("parent", item.parentId); putString("icon", item.iconKey); putInt("colour", item.colourArgb)
                putBoolean("archived", item.isArchived)
            } }
        }
    }
}
