package com.mohnishraj.goldmineledger

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mohnishraj.goldmineledger.databinding.DialogTransactionFilterBinding
import java.time.LocalDate

class TransactionFilterDialog : DialogFragment() {
    private lateinit var b: DialogTransactionFilterBinding
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private var accounts: List<AccountEntity> = emptyList()
    private var categories: List<CategoryEntity> = emptyList()
    private var tags: List<TagEntity> = emptyList()

    private val attachmentOptions = listOf("All", "With attachments", "Without attachments")
    private val statusOptions = listOf("All", "Cleared", "Pending")
    private val originOptions = listOf("All", "Recurring", "Manual")

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        b = DialogTransactionFilterBinding.inflate(layoutInflater)
        accounts = vm.allAccounts.value
        categories = vm.categories.value
        tags = vm.tags.value
        val current = vm.transactionFilterState.value

        val accountNames = listOf("All accounts") + accounts.map { it.name }
        val categoryNames = listOf("All categories") + categories.map { label(it) }
        val tagNames = listOf("All tags") + tags.map { it.name }
        val currencyNames = listOf("All currencies") + Utils.currencies.map(Utils::currencyLabel)

        b.accountInput.setAdapter(adapter(accountNames))
        b.categoryInput.setAdapter(adapter(categoryNames))
        b.tagInput.setAdapter(adapter(tagNames))
        b.currencyInput.setAdapter(adapter(currencyNames))
        b.attachmentInput.setAdapter(adapter(attachmentOptions))
        b.statusInput.setAdapter(adapter(statusOptions))
        b.originInput.setAdapter(adapter(originOptions))
        b.sortInput.setAdapter(adapter(TransactionSort.entries.map { it.label }))

        b.accountInput.setText(accounts.firstOrNull { it.id == current.accountId }?.name ?: "All accounts", false)
        b.categoryInput.setText(categories.firstOrNull { it.id == current.categoryId }?.let(::label) ?: "All categories", false)
        b.tagInput.setText(tags.firstOrNull { it.id == current.tagId }?.name ?: "All tags", false)
        b.currencyInput.setText(current.currencyCode?.let(Utils::currencyLabel) ?: "All currencies", false)
        current.minMinor?.let { minor -> current.currencyCode?.let { b.minInput.setText(Utils.plain(minor, it)) } }
        current.maxMinor?.let { minor -> current.currencyCode?.let { b.maxInput.setText(Utils.plain(minor, it)) } }
        b.attachmentInput.setText(when (current.hasAttachment) { true -> attachmentOptions[1]; false -> attachmentOptions[2]; null -> attachmentOptions[0] }, false)
        b.statusInput.setText(when (current.cleared) { true -> statusOptions[1]; false -> statusOptions[2]; null -> statusOptions[0] }, false)
        b.originInput.setText(when (current.recurringOnly) { true -> originOptions[1]; false -> originOptions[2]; null -> originOptions[0] }, false)
        b.sortInput.setText(current.sort.label, false)
        b.fromInput.setText(current.fromDate.orEmpty())
        b.toInput.setText(current.toDate.orEmpty())
        b.fromInput.setOnClickListener { pickDate(b.fromInput.text?.toString().orEmpty()) { b.fromInput.setText(it) } }
        b.toInput.setOnClickListener { pickDate(b.toInput.text?.toString().orEmpty()) { b.toInput.setText(it) } }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter transactions")
            .setView(b.root)
            .setNeutralButton("Reset", null)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                vm.clearAdvancedFilters()
                dialog.dismiss()
            }
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { apply(dialog) }
        }
        return dialog
    }

    private fun adapter(items: List<String>) = ArrayAdapter(
        requireContext(),
        android.R.layout.simple_dropdown_item_1line,
        items
    )

    private fun label(item: CategoryEntity): String = "${item.name} • ${CategoryKind.from(item.kind).label}"

    private fun apply(dialog: AlertDialog) {
        listOf(b.fromLayout, b.toLayout, b.minLayout, b.maxLayout, b.currencyLayout).forEach { it.error = null }
        val from = b.fromInput.text?.toString().orEmpty().trim()
        val to = b.toInput.text?.toString().orEmpty().trim()
        if (from.isNotBlank()) Utils.validDate(from)?.let { b.fromLayout.error = it; return }
        if (to.isNotBlank()) Utils.validDate(to)?.let { b.toLayout.error = it; return }
        if (from.isNotBlank() && to.isNotBlank() && from > to) {
            b.toLayout.error = "To date must be on or after from date"
            return
        }

        val selectedCurrency = Utils.currencies.firstOrNull {
            Utils.currencyLabel(it) == b.currencyInput.text.toString()
        }
        val minText = b.minInput.text?.toString().orEmpty().trim()
        val maxText = b.maxInput.text?.toString().orEmpty().trim()
        if ((minText.isNotBlank() || maxText.isNotBlank()) && selectedCurrency == null) {
            b.currencyLayout.error = "Choose a currency for amount limits"
            return
        }
        val minMinor = if (minText.isBlank()) null else Utils.parseMinor(minText, selectedCurrency!!).getOrElse {
            b.minLayout.error = it.message ?: "Invalid amount"
            return
        }
        val maxMinor = if (maxText.isBlank()) null else Utils.parseMinor(maxText, selectedCurrency!!).getOrElse {
            b.maxLayout.error = it.message ?: "Invalid amount"
            return
        }
        if (minMinor != null && minMinor < 0) { b.minLayout.error = "Use a positive amount"; return }
        if (maxMinor != null && maxMinor < 0) { b.maxLayout.error = "Use a positive amount"; return }
        if (minMinor != null && maxMinor != null && minMinor > maxMinor) {
            b.maxLayout.error = "Maximum must be at least the minimum"
            return
        }

        vm.setAdvancedFilter(
            accountId = accounts.firstOrNull { it.name == b.accountInput.text.toString() }?.id,
            categoryId = categories.firstOrNull { label(it) == b.categoryInput.text.toString() }?.id,
            tagId = tags.firstOrNull { it.name == b.tagInput.text.toString() }?.id,
            currencyCode = selectedCurrency,
            minMinor = minMinor,
            maxMinor = maxMinor,
            hasAttachment = when (b.attachmentInput.text.toString()) { attachmentOptions[1] -> true; attachmentOptions[2] -> false; else -> null },
            cleared = when (b.statusInput.text.toString()) { statusOptions[1] -> true; statusOptions[2] -> false; else -> null },
            recurringOnly = when (b.originInput.text.toString()) { originOptions[1] -> true; originOptions[2] -> false; else -> null },
            from = from,
            to = to,
            sort = TransactionSort.entries.firstOrNull { it.label == b.sortInput.text.toString() } ?: TransactionSort.DATE_NEWEST
        )
        dialog.dismiss()
    }

    private fun pickDate(existing: String, done: (String) -> Unit) {
        val initial = runCatching { LocalDate.parse(existing) }.getOrDefault(LocalDate.now())
        DatePickerDialog(
            requireContext(),
            { _, year, month, day -> done(LocalDate.of(year, month + 1, day).toString()) },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }
}
