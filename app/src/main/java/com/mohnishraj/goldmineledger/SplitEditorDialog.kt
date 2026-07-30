package com.mohnishraj.goldmineledger

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mohnishraj.goldmineledger.databinding.DialogSplitEditorBinding
import com.mohnishraj.goldmineledger.databinding.DialogSplitLineBinding
import com.mohnishraj.goldmineledger.databinding.ItemSplitBinding

class SplitEditorDialog : DialogFragment() {
    private lateinit var b: DialogSplitEditorBinding
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private val lines = mutableListOf<TransactionSplitDraft>()
    private lateinit var adapter: SplitAdapter
    private val totalMinor get() = requireArguments().getLong(ARG_TOTAL)
    private val currency get() = requireArguments().getString(ARG_CURRENCY) ?: "INR"
    private val type get() = TransactionType.from(requireArguments().getString(ARG_TYPE) ?: TransactionType.EXPENSE.name)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        b = DialogSplitEditorBinding.inflate(layoutInflater)
        val ids = requireArguments().getStringArrayList(ARG_IDS).orEmpty()
        val amounts = requireArguments().getLongArray(ARG_AMOUNTS) ?: longArrayOf()
        val memos = requireArguments().getStringArrayList(ARG_MEMOS).orEmpty()
        ids.forEachIndexed { index, id ->
            val amount = amounts.getOrNull(index) ?: return@forEachIndexed
            lines += TransactionSplitDraft(id, amount, memos.getOrNull(index).orEmpty())
        }
        adapter = SplitAdapter(
            categoryName = { id -> vm.categories.value.firstOrNull { it.id == id }?.name ?: "Missing category" },
            currency = currency,
            edit = { index -> editLine(index) },
            remove = { index -> lines.removeAt(index); adapter.submit(lines); updateTotals() }
        )
        b.list.adapter = adapter
        b.addButton.setOnClickListener { editLine(null) }
        adapter.submit(lines)
        updateTotals()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Split transaction")
            .setView(b.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Done", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { finish(dialog) }
        }
        return dialog
    }

    private fun editLine(index: Int?) {
        val binding = DialogSplitLineBinding.inflate(layoutInflater)
        val kind = if (type == TransactionType.INCOME) CategoryKind.INCOME.name else CategoryKind.EXPENSE.name
        val current = index?.let(lines::get)
        val categories = vm.categories.value.filter { it.kind == kind && (!it.isArchived || it.id == current?.categoryId) }
        binding.categoryInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories.map { it.name }))
        binding.categoryInput.setText(categories.firstOrNull { it.id == current?.categoryId }?.name ?: categories.firstOrNull()?.name.orEmpty(), false)
        current?.let {
            binding.amountInput.setText(Utils.plain(it.amountMinor, currency))
            binding.memoInput.setText(it.memo)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (index == null) "Add split line" else "Edit split line")
            .setView(binding.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                binding.categoryLayout.error = null
                binding.amountLayout.error = null
                val category = categories.firstOrNull { it.name == binding.categoryInput.text.toString() }
                if (category == null) { binding.categoryLayout.error = "Choose a category"; return@setOnClickListener }
                val amount = Utils.parseMinor(binding.amountInput.text?.toString().orEmpty(), currency)
                if (amount.isFailure || amount.getOrNull() == null || amount.getOrThrow() <= 0) {
                    binding.amountLayout.error = amount.exceptionOrNull()?.message ?: "Enter an amount greater than zero"
                    return@setOnClickListener
                }
                val line = TransactionSplitDraft(category.id, amount.getOrThrow(), binding.memoInput.text?.toString().orEmpty())
                if (index == null) lines += line else lines[index] = line
                adapter.submit(lines)
                updateTotals()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun updateTotals() {
        val used = lines.sumOf { it.amountMinor }
        val remaining = totalMinor - used
        b.totalText.text = "Transaction total: ${Utils.money(totalMinor, currency)}"
        b.remainingText.text = when {
            remaining > 0 -> "Unassigned: ${Utils.money(remaining, currency)}"
            remaining < 0 -> "Over-assigned: ${Utils.money(-remaining, currency)}"
            else -> "Fully assigned"
        }
    }

    private fun finish(dialog: AlertDialog) {
        when {
            lines.size < 2 -> Snackbar.make(b.root, "Add at least two split lines", Snackbar.LENGTH_LONG).show()
            lines.size > 20 -> Snackbar.make(b.root, "Use no more than 20 split lines", Snackbar.LENGTH_LONG).show()
            lines.sumOf { it.amountMinor } != totalMinor -> Snackbar.make(b.root, "Split amounts must equal ${Utils.money(totalMinor, currency)}", Snackbar.LENGTH_LONG).show()
            else -> {
                parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle().apply {
                    putStringArrayList(ARG_IDS, ArrayList(lines.map { it.categoryId }))
                    putLongArray(ARG_AMOUNTS, lines.map { it.amountMinor }.toLongArray())
                    putStringArrayList(ARG_MEMOS, ArrayList(lines.map { it.memo }))
                })
                dialog.dismiss()
            }
        }
    }

    private class SplitAdapter(
        private val categoryName: (String) -> String,
        private val currency: String,
        private val edit: (Int) -> Unit,
        private val remove: (Int) -> Unit
    ) : RecyclerView.Adapter<SplitAdapter.Holder>() {
        private var items: List<TransactionSplitDraft> = emptyList()
        fun submit(value: List<TransactionSplitDraft>) { items = value.toList(); notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            ItemSplitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
        inner class Holder(private val b: ItemSplitBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: TransactionSplitDraft) {
                b.category.text = categoryName(item.categoryId)
                b.memo.text = item.memo.ifBlank { "Tap to edit" }
                b.amount.text = Utils.money(item.amountMinor, currency)
                b.root.setOnClickListener {
                    bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(edit)
                }
                b.remove.setOnClickListener {
                    bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(remove)
                }
            }
        }
    }

    companion object {
        const val RESULT_KEY = "transaction_splits_result"
        private const val ARG_TOTAL = "total"
        private const val ARG_CURRENCY = "currency"
        private const val ARG_TYPE = "type"
        const val ARG_IDS = "split_ids"
        const val ARG_AMOUNTS = "split_amounts"
        const val ARG_MEMOS = "split_memos"

        fun newInstance(total: Long, currency: String, type: TransactionType, lines: List<TransactionSplitDraft>) =
            SplitEditorDialog().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TOTAL, total)
                    putString(ARG_CURRENCY, currency)
                    putString(ARG_TYPE, type.name)
                    putStringArrayList(ARG_IDS, ArrayList(lines.map { it.categoryId }))
                    putLongArray(ARG_AMOUNTS, lines.map { it.amountMinor }.toLongArray())
                    putStringArrayList(ARG_MEMOS, ArrayList(lines.map { it.memo }))
                }
            }
    }
}
