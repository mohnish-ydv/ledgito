package com.mohnishraj.goldmineledger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mohnishraj.goldmineledger.databinding.FragmentCategoriesBinding
import kotlinx.coroutines.launch

class CategoriesFragment : Fragment() {
    private var _b: FragmentCategoriesBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private val adapter = CategoryAdapter(::action)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentCategoriesBinding.inflate(inflater, container, false); return b.root
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.list.adapter = adapter
        b.add.setOnClickListener { CategoryDialog.newInstance(null).show(parentFragmentManager, "category") }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                vm.categories.collect { adapter.submitList(it); b.empty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE }
            }
        }
    }
    private fun action(item: CategoryEntity, action: String) {
        when(action) {
            "edit" -> CategoryDialog.newInstance(item).show(parentFragmentManager, "category")
            "merge" -> showMerge(item)
            "archive" -> vm.archiveCategory(item, true, ::result)
            "restore" -> vm.archiveCategory(item, false, ::result)
            "delete" -> MaterialAlertDialogBuilder(requireContext()).setTitle("Delete ${item.name}?")
                .setMessage("Subcategories must be moved or deleted first.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> vm.deleteCategory(item, ::result) }.show()
        }
    }

    private fun showMerge(source: CategoryEntity) {
        val targets = vm.categories.value.filter {
            it.id != source.id && it.kind == source.kind && !it.isArchived && it.parentId == source.parentId
        }.ifEmpty {
            vm.categories.value.filter { it.id != source.id && it.kind == source.kind && !it.isArchived }
        }
        if (targets.isEmpty()) {
            Snackbar.make(b.root, "Create another active ${CategoryKind.from(source.kind).label.lowercase()} category first", Snackbar.LENGTH_LONG).show()
            return
        }
        val labels = targets.map { target ->
            val parent = vm.categories.value.firstOrNull { it.id == target.parentId }?.name
            if (parent == null) target.name else "${target.name} • under $parent"
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Merge ${source.name} into…")
            .setItems(labels) { _, which ->
                val target = targets[which]
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Move everything to ${target.name}?")
                    .setMessage("Transactions, splits, recurring rules and budgets will move to ${target.name}. ${source.name} will be archived. This keeps your history intact.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Merge") { _, _ -> vm.mergeCategory(source, target, ::result) }
                    .show()
            }
            .show()
    }
    private fun result(value: Result<Unit>) = Snackbar.make(b.root, value.fold({ "Done" }, { it.message ?: "Action failed" }), Snackbar.LENGTH_LONG).show()
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
