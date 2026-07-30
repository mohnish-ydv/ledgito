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
import com.mohnishraj.goldmineledger.databinding.DialogRecurringBinding
import java.time.LocalDate

class RecurringDialog : DialogFragment() {
    private lateinit var b: DialogRecurringBinding
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private var accounts: List<AccountEntity> = emptyList()
    private var categories: List<CategoryEntity> = emptyList()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        b = DialogRecurringBinding.inflate(layoutInflater)
        accounts = vm.allAccounts.value.filter {
            !it.isArchived || it.id == arguments?.getString("accountId") || it.id == arguments?.getString("destinationId")
        }
        categories = vm.categories.value.filter { !it.isArchived || it.id == arguments?.getString("categoryId") }
        b.typeInput.setAdapter(adapter(TransactionType.entries.map { it.label }))
        b.frequencyInput.setAdapter(adapter(RecurrenceFrequency.entries.map { it.label }))
        b.monthEndInput.setAdapter(adapter(MonthEndMode.entries.map { it.label }))
        b.postingModeInput.setAdapter(adapter(RecurringPostingMode.entries.map { it.label }))
        b.nameInput.setText(arguments?.getString("name").orEmpty())
        val type = arguments?.getString("type")?.let(TransactionType::from) ?: TransactionType.EXPENSE
        b.typeInput.setText(type.label, false)
        b.frequencyInput.setText((arguments?.getString("frequency")?.let(RecurrenceFrequency::from) ?: RecurrenceFrequency.MONTHLY).label, false)
        b.monthEndInput.setText((arguments?.getString("monthEnd")?.let(MonthEndMode::from) ?: MonthEndMode.LAST_VALID_DAY).label, false)
        b.postingModeInput.setText((arguments?.getString("postingMode")?.let(RecurringPostingMode::from) ?: RecurringPostingMode.AUTO).label, false)
        b.amountInput.setText(arguments?.takeIf { it.containsKey("amount") }?.getLong("amount")?.let {
            Utils.plain(it, arguments?.getString("currency") ?: accounts.firstOrNull()?.currencyCode ?: "INR")
        }.orEmpty())
        arguments?.takeIf { it.containsKey("receivedAmount") }?.getLong("receivedAmount")?.takeIf { it > 0 }?.let { value ->
            b.receivedAmountInput.setText(Utils.plain(value, arguments?.getString("destinationCurrency") ?: "INR"))
        }
        arguments?.takeIf { it.containsKey("transferFee") }?.getLong("transferFee")?.takeIf { it > 0 }?.let { value ->
            b.transferFeeInput.setText(Utils.plain(value, arguments?.getString("currency") ?: "INR"))
        }
        b.payeeInput.setText(arguments?.getString("payee").orEmpty())
        b.noteInput.setText(arguments?.getString("note").orEmpty())
        b.tagsInput.setText(arguments?.getString("tags").orEmpty())
        b.intervalInput.setText((arguments?.getInt("interval", 1) ?: 1).toString())
        arguments?.takeIf { it.containsKey("occurrences") }?.getInt("occurrences")?.takeIf { it > 0 }?.let { b.occurrencesInput.setText(it.toString()) }
        b.nextDateInput.setText(arguments?.getString("nextDate") ?: LocalDate.now().toString())
        b.endDateInput.setText(arguments?.getString("endDate").orEmpty())
        b.nextDateInput.setOnClickListener { pickDate(b.nextDateInput.text?.toString().orEmpty()) { b.nextDateInput.setText(it) } }
        b.endDateInput.setOnClickListener { pickDate(b.endDateInput.text?.toString().orEmpty()) { b.endDateInput.setText(it) } }
        b.endDateInput.setOnLongClickListener { b.endDateInput.setText(""); true }
        b.activeSwitch.isChecked = arguments?.getBoolean("active", true) ?: true
        b.typeInput.setOnItemClickListener { _, _, _, _ -> refreshFields() }
        b.frequencyInput.setOnItemClickListener { _, _, _, _ -> refreshScheduleFields() }
        b.accountInput.setOnItemClickListener { _, _, _, _ -> refreshDestination() }
        b.destinationInput.setOnItemClickListener { _, _, _, _ -> refreshTransferAmounts() }
        refreshFields(initial = true)
        refreshScheduleFields()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (arguments?.getString("id") == null) "New recurring rule" else "Edit recurring rule")
            .setView(b.root).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener { dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { save(dialog) } }
        return dialog
    }

    private fun adapter(items: List<String>) = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)
    private fun selectedType() = TransactionType.entries.firstOrNull { it.label == b.typeInput.text.toString() } ?: TransactionType.EXPENSE
    private fun selectedAccount() = accounts.firstOrNull { it.name == b.accountInput.text.toString() }
    private fun selectedDestination() = accounts.firstOrNull { "${it.name} (${it.currencyCode})" == b.destinationInput.text.toString() }

    private fun refreshFields(initial: Boolean = false) {
        val type = selectedType()
        val currentAccountId = if (initial) arguments?.getString("accountId") else selectedAccount()?.id
        b.accountInput.setAdapter(adapter(accounts.map { it.name }))
        val account = accounts.firstOrNull { it.id == currentAccountId } ?: accounts.firstOrNull()
        b.accountInput.setText(account?.name.orEmpty(), false)
        val transfer = type == TransactionType.TRANSFER
        b.destinationLayout.visibility = if (transfer) View.VISIBLE else View.GONE
        b.transferFeeLayout.visibility = if (transfer) View.VISIBLE else View.GONE
        b.categoryLayout.visibility = if (transfer) View.GONE else View.VISIBLE
        refreshDestination(initial)
        val kind = if (type == TransactionType.INCOME) CategoryKind.INCOME.name else CategoryKind.EXPENSE.name
        val currentCategoryId = arguments?.getString("categoryId")
        val available = categories.filter { it.kind == kind && (!it.isArchived || it.id == currentCategoryId) }
        b.categoryInput.setAdapter(adapter(available.map { it.name }))
        val categoryId = if (initial) currentCategoryId else available.firstOrNull { it.name == b.categoryInput.text.toString() }?.id
        b.categoryInput.setText(available.firstOrNull { it.id == categoryId }?.name ?: available.firstOrNull()?.name.orEmpty(), false)
    }

    private fun refreshDestination(initial: Boolean = false) {
        val source = selectedAccount()
        val available = accounts.filter { it.id != source?.id }
        b.destinationInput.setAdapter(adapter(available.map { "${it.name} (${it.currencyCode})" }))
        val targetId = if (initial) arguments?.getString("destinationId") else selectedDestination()?.id
        val target = available.firstOrNull { it.id == targetId } ?: available.firstOrNull()
        b.destinationInput.setText(target?.let { "${it.name} (${it.currencyCode})" }.orEmpty(), false)
        refreshTransferAmounts()
    }

    private fun refreshTransferAmounts() {
        val source = selectedAccount()
        val target = selectedDestination()
        val cross = selectedType() == TransactionType.TRANSFER && source != null && target != null && source.currencyCode != target.currencyCode
        b.receivedAmountLayout.visibility = if (cross) View.VISIBLE else View.GONE
        b.receivedAmountLayout.hint = target?.let { "Amount received (${it.currencyCode})" } ?: "Amount received"
        b.amountLayout.hint = source?.let { "Amount (${it.currencyCode})" } ?: "Amount"
        b.transferFeeLayout.hint = source?.let { "Transfer fee (${it.currencyCode}, optional)" } ?: "Transfer fee"
    }

    private fun refreshScheduleFields() {
        val frequency = RecurrenceFrequency.entries.firstOrNull { it.label == b.frequencyInput.text.toString() } ?: RecurrenceFrequency.MONTHLY
        b.monthEndLayout.visibility = if (frequency == RecurrenceFrequency.MONTHLY || frequency == RecurrenceFrequency.YEARLY) View.VISIBLE else View.GONE
    }

    private fun save(dialog: AlertDialog) {
        listOf(b.nameLayout, b.amountLayout, b.receivedAmountLayout, b.transferFeeLayout, b.accountLayout, b.destinationLayout,
            b.categoryLayout, b.intervalLayout, b.occurrencesLayout, b.nextDateLayout, b.endDateLayout).forEach { it.error = null }
        val name = b.nameInput.text?.toString().orEmpty()
        Utils.validName(name, 60)?.let { b.nameLayout.error = it; return }
        val type = selectedType()
        val account = selectedAccount()
        if (account == null) { b.accountLayout.error = "Choose an account"; return }
        val amount = Utils.parseMinor(b.amountInput.text?.toString().orEmpty(), account.currencyCode)
        if (amount.isFailure || amount.getOrNull() == null || amount.getOrThrow() <= 0) {
            b.amountLayout.error = amount.exceptionOrNull()?.message ?: "Amount must be greater than zero"; return
        }
        val destination = selectedDestination()
        val category = categories.firstOrNull { it.name == b.categoryInput.text.toString() }
        if (type == TransactionType.TRANSFER && destination == null) { b.destinationLayout.error = "Choose a destination account"; return }
        if (type != TransactionType.TRANSFER && category == null) { b.categoryLayout.error = "Choose a category"; return }
        val cross = type == TransactionType.TRANSFER && destination != null && destination.currencyCode != account.currencyCode
        val received = if (cross) {
            val parsed = Utils.parseMinor(b.receivedAmountInput.text?.toString().orEmpty(), destination!!.currencyCode)
            if (parsed.isFailure || parsed.getOrNull() == null || parsed.getOrThrow() <= 0) {
                b.receivedAmountLayout.error = parsed.exceptionOrNull()?.message ?: "Enter received amount"; return
            }
            parsed.getOrThrow()
        } else null
        val feeText = b.transferFeeInput.text?.toString().orEmpty().trim()
        val fee = if (type == TransactionType.TRANSFER && feeText.isNotBlank()) {
            val parsed = Utils.parseMinor(feeText, account.currencyCode)
            if (parsed.isFailure || parsed.getOrNull() == null || parsed.getOrThrow() < 0) {
                b.transferFeeLayout.error = parsed.exceptionOrNull()?.message ?: "Invalid fee"; return
            }
            parsed.getOrThrow()
        } else 0L
        val interval = b.intervalInput.text?.toString()?.toIntOrNull()
        if (interval == null || interval !in 1..365) { b.intervalLayout.error = "Enter 1 to 365"; return }
        val occurrencesText = b.occurrencesInput.text?.toString().orEmpty().trim()
        val occurrences = occurrencesText.toIntOrNull()
        if (occurrencesText.isNotBlank() && (occurrences == null || occurrences !in 1..10000)) {
            b.occurrencesLayout.error = "Enter 1 to 10000, or leave blank"; return
        }
        val nextDate = b.nextDateInput.text?.toString().orEmpty()
        Utils.validDate(nextDate)?.let { b.nextDateLayout.error = it; return }
        val endDate = b.endDateInput.text?.toString().orEmpty().trim()
        if (endDate.isNotBlank()) {
            Utils.validDate(endDate)?.let { b.endDateLayout.error = it; return }
            if (endDate < nextDate) { b.endDateLayout.error = "End date must be after next due date"; return }
        }
        val frequency = RecurrenceFrequency.entries.firstOrNull { it.label == b.frequencyInput.text.toString() } ?: RecurrenceFrequency.MONTHLY
        vm.saveRecurring(RecurringRuleDraft(
            id = arguments?.getString("id"), name = name, type = type, accountId = account.id,
            destinationAccountId = destination?.id, categoryId = category?.id, amountMinor = amount.getOrThrow(),
            destinationAmountMinor = received, transferFeeMinor = fee,
            payee = b.payeeInput.text?.toString().orEmpty(), note = b.noteInput.text?.toString().orEmpty(),
            tags = b.tagsInput.text?.toString().orEmpty().split(','), frequency = frequency, intervalCount = interval,
            monthEndMode = MonthEndMode.entries.firstOrNull { it.label == b.monthEndInput.text.toString() } ?: MonthEndMode.LAST_VALID_DAY,
            postingMode = RecurringPostingMode.entries.firstOrNull { it.label == b.postingModeInput.text.toString() } ?: RecurringPostingMode.AUTO,
            occurrencesRemaining = occurrences, nextDueDate = nextDate, endDate = endDate.ifBlank { null }, active = b.activeSwitch.isChecked
        )) { result ->
            result.onSuccess { dialog.dismiss() }.onFailure { Snackbar.make(b.root, it.message ?: "Could not save rule", Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun pickDate(existing: String, done: (String) -> Unit) {
        val initial = runCatching { LocalDate.parse(existing) }.getOrDefault(LocalDate.now())
        DatePickerDialog(requireContext(), { _, year, month, day -> done(LocalDate.of(year, month + 1, day).toString()) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }

    companion object {
        fun newInstance(item: RecurringUiModel?) = RecurringDialog().apply {
            arguments = Bundle().apply {
                if (item != null) {
                    val r = item.entity
                    putString("id", r.id); putString("name", r.name); putString("type", r.type)
                    putString("accountId", r.accountId); putString("destinationId", r.destinationAccountId)
                    putString("categoryId", r.categoryId); putLong("amount", r.amountMinor); putString("currency", r.currencyCode)
                    putLong("receivedAmount", r.destinationAmountMinor); putString("destinationCurrency", r.destinationCurrencyCode)
                    putLong("transferFee", r.transferFeeMinor); putString("payee", r.payee); putString("note", r.note)
                    putString("tags", r.tagsCsv); putString("frequency", r.frequency); putInt("interval", r.intervalCount)
                    putString("monthEnd", r.monthEndMode); putString("postingMode", r.postingMode)
                    r.occurrencesRemaining?.let { putInt("occurrences", it) }
                    putString("nextDate", r.nextDueDate); putString("endDate", r.endDate); putBoolean("active", r.isActive)
                }
            }
        }
    }
}
