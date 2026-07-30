package com.mohnishraj.goldmineledger

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mohnishraj.goldmineledger.databinding.DialogBudgetBinding
import java.time.LocalDate

class BudgetDialog : DialogFragment() {
    private lateinit var b: DialogBudgetBinding
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private var categories: List<CategoryEntity> = emptyList()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        b = DialogBudgetBinding.inflate(layoutInflater)
        val existingCategoryId = arguments?.getString("categoryId")
        categories = vm.categories.value.filter {
            it.kind == CategoryKind.EXPENSE.name && (!it.isArchived || it.id == existingCategoryId)
        }
        val categoryLabels = listOf("All expense categories") + categories.map(::categoryLabel)
        b.categoryInput.setAdapter(adapter(categoryLabels))
        b.currencyInput.setAdapter(adapter(Utils.currencies.map(Utils::currencyLabel)))
        b.periodInput.setAdapter(adapter(BudgetPeriodType.entries.map { it.label }))
        b.carryInput.setAdapter(adapter(BudgetCarryover.entries.map { it.label }))

        val currency = arguments?.getString("currency") ?: vm.profile.value?.baseCurrency ?: Utils.defaultCurrency()
        val period = arguments?.getString("period")?.let(BudgetPeriodType::from) ?: BudgetPeriodType.MONTHLY
        val carry = arguments?.getString("carry")?.let(BudgetCarryover::from) ?: BudgetCarryover.OFF
        b.nameInput.setText(arguments?.getString("name").orEmpty())
        val amount = arguments?.getLong("amount", 0L) ?: 0L
        if (amount > 0) b.amountInput.setText(Utils.plain(amount, currency))
        b.currencyInput.setText(Utils.currencyLabel(currency), false)
        b.categoryInput.setText(categories.firstOrNull { it.id == existingCategoryId }?.let(::categoryLabel) ?: "All expense categories", false)
        b.periodInput.setText(period.label, false)
        b.startInput.setText(arguments?.getString("anchor") ?: LocalDate.now().toString())
        b.customEndInput.setText(arguments?.getString("customEnd").orEmpty())
        b.intervalInput.setText((arguments?.getInt("interval", 1) ?: 1).toString())
        b.repeatUntilInput.setText(arguments?.getString("repeatUntil").orEmpty())
        b.carryInput.setText(carry.label, false)
        b.activeSwitch.isChecked = arguments?.getBoolean("active", true) ?: true

        b.periodInput.setOnItemClickListener { _, _, _, _ -> refreshCustomVisibility() }
        b.startInput.setOnClickListener { pickDate(b.startInput.text?.toString().orEmpty()) { b.startInput.setText(it) } }
        b.customEndInput.setOnClickListener { pickDate(b.customEndInput.text?.toString().orEmpty()) { b.customEndInput.setText(it) } }
        b.repeatUntilInput.setOnClickListener { pickDate(b.repeatUntilInput.text?.toString().orEmpty()) { b.repeatUntilInput.setText(it) } }
        b.repeatUntilInput.setOnLongClickListener { b.repeatUntilInput.setText(""); true }
        refreshCustomVisibility()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (arguments?.getString("id") == null) "New budget" else "Edit budget")
            .setView(b.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { save(dialog) }
        }
        return dialog
    }

    private fun adapter(values: List<String>) = ArrayAdapter(
        requireContext(), android.R.layout.simple_dropdown_item_1line, values
    )

    private fun categoryLabel(item: CategoryEntity): String {
        val parent = categories.firstOrNull { it.id == item.parentId }?.name
        return if (parent == null) item.name else "$parent › ${item.name}"
    }

    private fun selectedPeriod(): BudgetPeriodType = BudgetPeriodType.entries.firstOrNull {
        it.label == b.periodInput.text.toString()
    } ?: BudgetPeriodType.MONTHLY

    private fun refreshCustomVisibility() {
        b.customEndLayout.visibility = if (selectedPeriod() == BudgetPeriodType.CUSTOM) View.VISIBLE else View.GONE
        b.intervalLayout.visibility = if (selectedPeriod() == BudgetPeriodType.CUSTOM) View.GONE else View.VISIBLE
        b.repeatUntilLayout.visibility = if (selectedPeriod() == BudgetPeriodType.CUSTOM) View.GONE else View.VISIBLE
    }

    private fun save(dialog: AlertDialog) {
        listOf(
            b.nameLayout, b.amountLayout, b.currencyLayout, b.startLayout,
            b.customEndLayout, b.intervalLayout, b.repeatUntilLayout
        ).forEach { it.error = null }
        val name = b.nameInput.text?.toString().orEmpty()
        Utils.validName(name, 60)?.let { b.nameLayout.error = it; return }
        val currency = Utils.currencyCode(b.currencyInput.text.toString())
        Utils.validCurrency(currency)?.let { b.currencyLayout.error = it; return }
        val amount = Utils.parseMinor(b.amountInput.text?.toString().orEmpty(), currency).getOrElse {
            b.amountLayout.error = it.message ?: "Invalid amount"
            return
        }
        if (amount <= 0) { b.amountLayout.error = "Amount must be greater than zero"; return }
        val start = b.startInput.text?.toString().orEmpty()
        Utils.validDate(start)?.let { b.startLayout.error = it; return }
        val period = selectedPeriod()
        val customEnd = b.customEndInput.text?.toString().orEmpty().trim().ifBlank { null }
        if (period == BudgetPeriodType.CUSTOM) {
            if (customEnd == null || Utils.validDate(customEnd) != null || customEnd < start) {
                b.customEndLayout.error = "Choose an end date on or after the start date"
                return
            }
        }
        val interval = if (period == BudgetPeriodType.CUSTOM) 1 else {
            b.intervalInput.text?.toString()?.toIntOrNull() ?: 0
        }
        if (interval !in 1..24) { b.intervalLayout.error = "Use a value from 1 to 24"; return }
        val repeatUntil = if (period == BudgetPeriodType.CUSTOM) null else {
            b.repeatUntilInput.text?.toString().orEmpty().trim().ifBlank { null }
        }
        if (repeatUntil != null && (Utils.validDate(repeatUntil) != null || repeatUntil < start)) {
            b.repeatUntilLayout.error = "Choose a date on or after the start date"
            return
        }
        val category = categories.firstOrNull { categoryLabel(it) == b.categoryInput.text.toString() }
        val carry = BudgetCarryover.entries.firstOrNull { it.label == b.carryInput.text.toString() } ?: BudgetCarryover.OFF
        vm.saveBudget(
            BudgetDraft(
                id = arguments?.getString("id"),
                name = name,
                categoryId = category?.id,
                amountMinor = amount,
                currencyCode = currency,
                periodType = period,
                anchorDate = start,
                customEndDate = customEnd,
                repeatInterval = interval,
                repeatUntil = repeatUntil,
                carryover = carry,
                active = b.activeSwitch.isChecked
            )
        ) { result ->
            result.onSuccess { dialog.dismiss() }
                .onFailure { Snackbar.make(b.root, it.message ?: "Could not save budget", Snackbar.LENGTH_LONG).show() }
        }
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

    companion object {
        fun newInstance(item: BudgetUiModel?) = BudgetDialog().apply {
            arguments = Bundle().apply {
                item?.entity?.let { budget ->
                    putString("id", budget.id)
                    putString("name", budget.name)
                    putString("categoryId", budget.categoryId)
                    putLong("amount", budget.amountMinor)
                    putString("currency", budget.currencyCode)
                    putString("period", budget.periodType)
                    putString("anchor", budget.anchorDate)
                    putString("customEnd", budget.customEndDate)
                    putInt("interval", budget.repeatInterval)
                    putString("repeatUntil", budget.repeatUntil)
                    putString("carry", budget.carryoverMode)
                    putBoolean("active", budget.isActive)
                }
            }
        }
    }
}
