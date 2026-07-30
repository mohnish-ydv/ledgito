package com.mohnishraj.goldmineledger

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mohnishraj.goldmineledger.databinding.DialogTransactionBinding
import java.time.LocalDate

class TransactionDialog : DialogFragment() {
    private lateinit var b: DialogTransactionBinding
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private var accounts: List<AccountEntity> = emptyList()
    private var categories: List<CategoryEntity> = emptyList()
    private val pendingUris = mutableListOf<String>()
    private val splitLines = mutableListOf<TransactionSplitDraft>()

    private val pickAttachments = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pendingUris.clear()
        pendingUris += uris.map { it.toString() }
        updateAttachmentSummary()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        b = DialogTransactionBinding.inflate(layoutInflater)
        accounts = vm.allAccounts.value.filter {
            !it.isArchived || it.id == arguments?.getString("accountId") || it.id == arguments?.getString("destinationId")
        }
        categories = vm.categories.value.filter { !it.isArchived || it.id == arguments?.getString("categoryId") }
        restoreSplits()
        childFragmentManager.setFragmentResultListener(SplitEditorDialog.RESULT_KEY, this) { _, result ->
            val ids = result.getStringArrayList(SplitEditorDialog.ARG_IDS).orEmpty()
            val amounts = result.getLongArray(SplitEditorDialog.ARG_AMOUNTS) ?: longArrayOf()
            val memos = result.getStringArrayList(SplitEditorDialog.ARG_MEMOS).orEmpty()
            splitLines.clear()
            ids.forEachIndexed { index, id ->
                amounts.getOrNull(index)?.let { splitLines += TransactionSplitDraft(id, it, memos.getOrNull(index).orEmpty()) }
            }
            b.splitSwitch.isChecked = splitLines.isNotEmpty()
            refreshSplitUi()
        }

        b.typeInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, TransactionType.entries.map { it.label }))
        val type = arguments?.getString("type")?.let(TransactionType::from) ?: TransactionType.EXPENSE
        b.typeInput.setText(type.label, false)
        b.amountInput.setText(arguments?.takeIf { it.containsKey("amount") }?.getLong("amount")?.let {
            Utils.plain(it, arguments?.getString("currency") ?: accounts.firstOrNull()?.currencyCode ?: "INR")
        }.orEmpty())
        arguments?.takeIf { it.containsKey("receivedAmount") }?.getLong("receivedAmount")?.takeIf { it > 0 }?.let { amount ->
            val currency = arguments?.getString("destinationCurrency") ?: "INR"
            b.receivedAmountInput.setText(Utils.plain(amount, currency))
        }
        arguments?.takeIf { it.containsKey("transferFee") }?.getLong("transferFee")?.takeIf { it > 0 }?.let { fee ->
            val currency = arguments?.getString("currency") ?: "INR"
            b.transferFeeInput.setText(Utils.plain(fee, currency))
        }
        b.dateInput.setText(arguments?.getString("date") ?: LocalDate.now().toString())
        b.dateInput.setOnClickListener { pickDate(b.dateInput.text?.toString().orEmpty()) { b.dateInput.setText(it) } }
        b.payeeInput.setText(arguments?.getString("payee").orEmpty())
        b.noteInput.setText(arguments?.getString("note").orEmpty())
        b.tagsInput.setText(arguments?.getString("tags").orEmpty())
        b.clearedSwitch.isChecked = arguments?.getBoolean("cleared", true) ?: true
        b.splitSwitch.isChecked = splitLines.isNotEmpty()
        b.splitSwitch.setOnCheckedChangeListener { _, checked ->
            if (!checked) splitLines.clear()
            refreshSplitUi()
        }
        b.splitButton.setOnClickListener { openSplitEditor() }
        val existingCount = arguments?.getInt("attachmentCount", 0) ?: 0
        b.removeAttachmentsSwitch.visibility = if (existingCount > 0) View.VISIBLE else View.GONE
        b.attachmentButton.setOnClickListener { pickAttachments.launch(arrayOf("image/*", "application/pdf", "text/plain")) }
        b.typeInput.setOnItemClickListener { _, _, _, _ -> refreshFields() }
        b.accountInput.setOnItemClickListener { _, _, _, _ -> refreshDestination() }
        b.destinationInput.setOnItemClickListener { _, _, _, _ -> refreshTransferAmounts() }
        refreshFields(initial = true)
        updateAttachmentSummary()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (arguments?.getString("id") == null) "New transaction" else "Edit transaction")
            .setView(b.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener { dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { save(dialog) } }
        return dialog
    }

    private fun restoreSplits() {
        val ids = arguments?.getStringArrayList("splitIds").orEmpty()
        val amounts = arguments?.getLongArray("splitAmounts") ?: longArrayOf()
        val memos = arguments?.getStringArrayList("splitMemos").orEmpty()
        ids.forEachIndexed { index, id -> amounts.getOrNull(index)?.let { splitLines += TransactionSplitDraft(id, it, memos.getOrNull(index).orEmpty()) } }
    }

    private fun selectedType() = TransactionType.entries.firstOrNull { it.label == b.typeInput.text.toString() } ?: TransactionType.EXPENSE
    private fun selectedAccount() = accounts.firstOrNull { it.name == b.accountInput.text.toString() }
    private fun selectedDestination() = accounts.firstOrNull { it.name == b.destinationInput.text.toString() }

    private fun refreshFields(initial: Boolean = false) {
        val type = selectedType()
        val currentAccountId = if (initial) arguments?.getString("accountId") else selectedAccount()?.id
        b.accountInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, accounts.map { it.name }))
        val account = accounts.firstOrNull { it.id == currentAccountId } ?: accounts.firstOrNull()
        b.accountInput.setText(account?.name.orEmpty(), false)
        val transfer = type == TransactionType.TRANSFER
        b.destinationLayout.visibility = if (transfer) View.VISIBLE else View.GONE
        b.transferFeeLayout.visibility = if (transfer) View.VISIBLE else View.GONE
        b.categoryLayout.visibility = if (transfer || b.splitSwitch.isChecked) View.GONE else View.VISIBLE
        b.splitSwitch.visibility = if (transfer) View.GONE else View.VISIBLE
        if (transfer) {
            if (splitLines.isNotEmpty()) splitLines.clear()
            b.splitSwitch.isChecked = false
        }
        refreshDestination(initial)
        val kind = if (type == TransactionType.INCOME) CategoryKind.INCOME.name else CategoryKind.EXPENSE.name
        val currentCategoryId = arguments?.getString("categoryId")
        val available = categories.filter { it.kind == kind && (!it.isArchived || it.id == currentCategoryId) }
        b.categoryInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, available.map { it.name }))
        val categoryId = if (initial) currentCategoryId else available.firstOrNull { it.name == b.categoryInput.text.toString() }?.id
        b.categoryInput.setText(available.firstOrNull { it.id == categoryId }?.name ?: available.firstOrNull()?.name.orEmpty(), false)
        refreshSplitUi()
    }

    private fun refreshDestination(initial: Boolean = false) {
        val source = selectedAccount()
        val available = accounts.filter { it.id != source?.id }
        b.destinationInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, available.map { "${it.name} (${it.currencyCode})" }))
        val targetId = if (initial) arguments?.getString("destinationId") else selectedDestinationByLabel()?.id
        val target = available.firstOrNull { it.id == targetId } ?: available.firstOrNull()
        b.destinationInput.setText(target?.let { "${it.name} (${it.currencyCode})" }.orEmpty(), false)
        refreshTransferAmounts()
    }

    private fun selectedDestinationByLabel(): AccountEntity? = accounts.firstOrNull { "${it.name} (${it.currencyCode})" == b.destinationInput.text.toString() }

    private fun refreshTransferAmounts() {
        val source = selectedAccount()
        val target = selectedDestinationByLabel()
        val crossCurrency = selectedType() == TransactionType.TRANSFER && source != null && target != null && source.currencyCode != target.currencyCode
        b.receivedAmountLayout.visibility = if (crossCurrency) View.VISIBLE else View.GONE
        b.receivedAmountLayout.hint = target?.let { "Amount received (${it.currencyCode})" } ?: "Amount received"
        b.amountLayout.hint = source?.let { "Amount (${it.currencyCode})" } ?: "Amount"
        b.transferFeeLayout.hint = source?.let { "Transfer fee (${it.currencyCode}, optional)" } ?: "Transfer fee (optional)"
    }

    private fun refreshSplitUi() {
        val enabled = b.splitSwitch.isChecked && selectedType() != TransactionType.TRANSFER
        b.splitButton.visibility = if (enabled) View.VISIBLE else View.GONE
        b.splitSummary.visibility = if (enabled) View.VISIBLE else View.GONE
        b.categoryLayout.visibility = if (selectedType() == TransactionType.TRANSFER || enabled) View.GONE else View.VISIBLE
        if (enabled) {
            val account = selectedAccount()
            val used = splitLines.sumOf { it.amountMinor }
            b.splitSummary.text = if (account == null) "${splitLines.size} split lines" else {
                "${splitLines.size} lines • Assigned ${Utils.money(used, account.currencyCode)}"
            }
        }
    }

    private fun openSplitEditor() {
        b.amountLayout.error = null
        val account = selectedAccount() ?: run { b.accountLayout.error = "Choose an account"; return }
        val amount = Utils.parseMinor(b.amountInput.text?.toString().orEmpty(), account.currencyCode)
        if (amount.isFailure || amount.getOrNull() == null || amount.getOrThrow() <= 0) {
            b.amountLayout.error = amount.exceptionOrNull()?.message ?: "Enter the total amount first"
            return
        }
        SplitEditorDialog.newInstance(amount.getOrThrow(), account.currencyCode, selectedType(), splitLines)
            .show(childFragmentManager, "split-editor")
    }

    private fun updateAttachmentSummary() {
        val existing = arguments?.getInt("attachmentCount", 0) ?: 0
        b.attachmentSummary.text = "Existing: $existing • New: ${pendingUris.size}"
    }

    private fun save(dialog: AlertDialog) {
        listOf(b.amountLayout, b.receivedAmountLayout, b.transferFeeLayout, b.accountLayout, b.destinationLayout, b.categoryLayout, b.dateLayout).forEach { it.error = null }
        val type = selectedType()
        val account = selectedAccount()
        if (account == null) { b.accountLayout.error = "Create and choose an active account"; return }
        val amount = Utils.parseMinor(b.amountInput.text?.toString().orEmpty(), account.currencyCode)
        if (amount.isFailure || amount.getOrNull() == null || amount.getOrThrow() <= 0) {
            b.amountLayout.error = amount.exceptionOrNull()?.message ?: "Amount must be greater than zero"; return
        }
        val date = b.dateInput.text?.toString().orEmpty()
        Utils.validDate(date)?.let { b.dateLayout.error = it; return }
        val destination = selectedDestinationByLabel()
        val category = categories.firstOrNull { it.name == b.categoryInput.text.toString() }
        if (type == TransactionType.TRANSFER && destination == null) { b.destinationLayout.error = "Choose a destination account"; return }
        if (type != TransactionType.TRANSFER && !b.splitSwitch.isChecked && category == null) { b.categoryLayout.error = "Choose a category"; return }

        val crossCurrency = type == TransactionType.TRANSFER && destination != null && destination.currencyCode != account.currencyCode
        val receivedAmount = if (crossCurrency) {
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
                b.transferFeeLayout.error = parsed.exceptionOrNull()?.message ?: "Invalid transfer fee"; return
            }
            parsed.getOrThrow()
        } else 0L
        val splits = if (b.splitSwitch.isChecked) splitLines.toList() else emptyList()
        if (b.splitSwitch.isChecked && (splits.size < 2 || splits.sumOf { it.amountMinor } != amount.getOrThrow())) {
            Snackbar.make(b.root, "Manage splits so every amount adds up to the transaction total", Snackbar.LENGTH_LONG).show(); return
        }
        val draft = TransactionDraft(
            id = arguments?.getString("id"), type = type, accountId = account.id,
            destinationAccountId = destination?.id, categoryId = category?.id,
            amountMinor = amount.getOrThrow(), destinationAmountMinor = receivedAmount,
            transferFeeMinor = fee, splits = splits, date = date,
            payee = b.payeeInput.text?.toString().orEmpty(), note = b.noteInput.text?.toString().orEmpty(),
            tags = b.tagsInput.text?.toString().orEmpty().split(','), cleared = b.clearedSwitch.isChecked,
            attachmentUris = pendingUris.toList(), removeExistingAttachments = b.removeAttachmentsSwitch.isChecked
        )
        vm.saveTransaction(draft) { result ->
            result.onSuccess { dialog.dismiss() }
                .onFailure { Snackbar.make(b.root, it.message ?: "Could not save transaction", Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun pickDate(existing: String, done: (String) -> Unit) {
        val initial = runCatching { LocalDate.parse(existing) }.getOrDefault(LocalDate.now())
        DatePickerDialog(requireContext(), { _, year, month, day -> done(LocalDate.of(year, month + 1, day).toString()) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }

    companion object {
        fun newInstance(item: TransactionUiModel?, preset: TransactionType? = null, presetDate: String? = null, duplicate: Boolean = false) = TransactionDialog().apply {
            arguments = Bundle().apply {
                putString("type", (item?.entity?.type?.let(TransactionType::from) ?: preset ?: TransactionType.EXPENSE).name)
                presetDate?.let { putString("date", it) }
                if (item != null) {
                    val tx = item.entity
                    if (!duplicate) putString("id", tx.id)
                    putString("accountId", tx.accountId); putString("destinationId", tx.destinationAccountId)
                    putString("categoryId", tx.categoryId); putLong("amount", tx.amountMinor); putString("currency", tx.currencyCode)
                    putLong("receivedAmount", tx.destinationAmountMinor); putString("destinationCurrency", tx.destinationCurrencyCode)
                    putLong("transferFee", tx.transferFeeMinor); putString("date", tx.transactionDate)
                    putString("payee", tx.payee); putString("note", tx.note); putString("tags", item.tags.joinToString(", "))
                    putBoolean("cleared", tx.isCleared); putInt("attachmentCount", if (duplicate) 0 else item.attachments.size)
                    putStringArrayList("splitIds", ArrayList(item.splits.map { it.categoryId.orEmpty() }))
                    putLongArray("splitAmounts", item.splits.map { it.amountMinor }.toLongArray())
                    putStringArrayList("splitMemos", ArrayList(item.splits.map { it.memo }))
                }
            }
        }
    }
}
