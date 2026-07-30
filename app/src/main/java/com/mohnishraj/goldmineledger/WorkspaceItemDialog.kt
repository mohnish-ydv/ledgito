package com.mohnishraj.goldmineledger

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mohnishraj.goldmineledger.databinding.DialogWorkspaceItemBinding
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class WorkspaceItemDialog : DialogFragment() {
    private lateinit var b: DialogWorkspaceItemBinding
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private val type by lazy { WorkspaceType.from(requireArguments().getString(ARG_TYPE).orEmpty()) }
    private val itemId by lazy { requireArguments().getString(ARG_ID) }

    override fun onCreateDialog(state: Bundle?): Dialog {
        b = DialogWorkspaceItemBinding.inflate(LayoutInflater.from(requireContext()))
        val config = WorkspaceCatalog.forType(type)
        val existing = itemId?.let { id -> vm.workspaceItems.value.firstOrNull { it.entity.id == id }?.entity }
        val accounts = vm.allAccounts.value.filter { !it.isArchived || it.id == existing?.accountId }
        val categories = vm.categories.value.filter {
            it.kind == CategoryKind.EXPENSE.name && (!it.isArchived || it.id == existing?.categoryId)
        }

        b.titleLabel.text = if (existing == null) "Add ${config.title.lowercase()}" else "Edit ${config.title.lowercase()}"
        b.subtitle.text = config.subtitle
        b.amountLayout.hint = config.amountLabel
        b.currentLayout.hint = config.currentLabel
        b.startLayout.hint = config.startLabel
        b.dueLayout.hint = config.dueLabel
        b.metadataLayout.hint = config.metadataLabel
        val usesCadence = type == WorkspaceType.SUBSCRIPTION
        b.secondaryCodeLayout.isVisible = config.usesRate || usesCadence
        b.secondaryCodeLayout.hint = if (usesCadence) "Billing cycle" else "Quote currency"
        b.accountLayout.isVisible = config.showAccountAndCategory
        b.categoryLayout.isVisible = config.showAccountAndCategory

        val currencyLabels = Utils.currencies.map(Utils::currencyLabel)
        b.currencyInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, currencyLabels))
        val secondaryLabels = if (usesCadence) BillingCadence.entries.map { it.label } else currencyLabels
        b.secondaryCodeInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, secondaryLabels))
        b.accountInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, accounts.map { it.name }))
        b.categoryInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories.map { it.name }))
        b.statusInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, WorkspaceStatus.entries.map { it.label }))

        val defaultCurrency = existing?.currencyCode ?: vm.profile.value?.baseCurrency ?: "INR"
        b.currencyInput.setText(Utils.currencyLabel(defaultCurrency), false)
        b.secondaryCodeInput.setText(
            if (usesCadence) BillingCadence.from(existing?.secondaryCode).label
            else Utils.currencyLabel(existing?.secondaryCode?.takeIf { it.isNotBlank() } ?: "USD"),
            false
        )
        b.startInput.setText(existing?.startDate ?: LocalDate.now().toString())
        b.dueInput.setText(existing?.dueDate.orEmpty())
        b.startInput.setOnClickListener { pickDate(b.startInput.text?.toString().orEmpty()) { b.startInput.setText(it) } }
        b.dueInput.setOnClickListener { pickDate(b.dueInput.text?.toString().orEmpty()) { b.dueInput.setText(it) } }
        b.startLayout.setEndIconOnClickListener { b.startInput.performClick() }
        b.dueLayout.setEndIconOnClickListener { b.dueInput.performClick() }
        b.statusInput.setText(WorkspaceStatus.from(existing?.status ?: WorkspaceStatus.ACTIVE.name).label, false)
        b.nameInput.setText(existing?.title.orEmpty())
        b.metadataInput.setText(existing?.metadata.orEmpty())
        b.noteInput.setText(existing?.note.orEmpty())
        b.accountInput.setText(existing?.accountId?.let { id -> accounts.firstOrNull { it.id == id }?.name }.orEmpty(), false)
        b.categoryInput.setText(existing?.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }.orEmpty(), false)

        val supportsScheduledAmount = type in setOf(WorkspaceType.EMI, WorkspaceType.LOAN, WorkspaceType.DEBT, WorkspaceType.LIABILITY)
        b.secondaryAmountLayout.isVisible = supportsScheduledAmount
        b.secondaryAmountLayout.hint = when (type) {
            WorkspaceType.EMI -> "Instalment amount"
            WorkspaceType.LOAN -> "Expected repayment"
            WorkspaceType.DEBT, WorkspaceType.LIABILITY -> "Planned payment"
            else -> "Scheduled payment"
        }
        b.secondaryAmountInput.setText(existing?.secondaryMinor?.takeIf { it > 0L }?.let { Utils.plain(it, defaultCurrency) }.orEmpty())

        if (config.usesRate) {
            b.amountInput.setText("1")
            b.currentInput.setText(existing?.currentMinor?.takeIf { it > 0 }?.let { BigDecimal.valueOf(it, 6).stripTrailingZeros().toPlainString() }.orEmpty())
        } else if (config.usesPoints) {
            b.amountInput.setText(existing?.amountMinor?.toString().orEmpty())
            b.currentInput.setText(existing?.currentMinor?.toString().orEmpty())
        } else {
            b.amountInput.setText(existing?.let { Utils.plain(it.amountMinor, it.currencyCode) }.orEmpty())
            b.currentInput.setText(existing?.let { Utils.plain(it.currentMinor, it.currencyCode) }.orEmpty())
        }

        val eventDriven = type in setOf(
            WorkspaceType.GOAL, WorkspaceType.DEBT, WorkspaceType.LOAN, WorkspaceType.LIABILITY,
            WorkspaceType.EMI, WorkspaceType.BILL, WorkspaceType.SHOPPING_LIST,
            WorkspaceType.SHARED_EXPENSE, WorkspaceType.LOYALTY
        )
        if (existing != null && eventDriven) b.currentInput.isEnabled = false
        if (type == WorkspaceType.SHOPPING_LIST) {
            b.amountInput.isEnabled = false
            b.currentInput.isEnabled = false
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(b.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                clearErrors()
                val currency = Utils.currencyCode(b.currencyInput.text.toString())
                val secondaryCode = if (usesCadence) {
                    BillingCadence.entries.firstOrNull { it.label == b.secondaryCodeInput.text.toString() }?.name
                        ?: BillingCadence.MONTHLY.name
                } else Utils.currencyCode(b.secondaryCodeInput.text.toString())
                val amountResult = if (type == WorkspaceType.SHOPPING_LIST) Result.success(0L)
                else parseValue(b.amountInput.text?.toString().orEmpty(), currency, config.usesPoints, false)
                val currentResult = if (config.usesRate) parseRate(b.currentInput.text?.toString().orEmpty())
                else parseValue(b.currentInput.text?.toString().orEmpty().ifBlank { "0" }, currency, config.usesPoints, true)
                val amount = amountResult.getOrElse { b.amountLayout.error = it.message; return@setOnClickListener }
                var current = currentResult.getOrElse { b.currentLayout.error = it.message; return@setOnClickListener }
                val secondary = if (b.secondaryAmountLayout.isVisible) {
                    parseValue(b.secondaryAmountInput.text?.toString().orEmpty(), currency, points = false, allowBlank = true)
                        .getOrElse { b.secondaryAmountLayout.error = it.message; return@setOnClickListener }
                } else existing?.secondaryMinor ?: 0L
                if (existing == null) {
                    current = when (type) {
                        WorkspaceType.DEBT, WorkspaceType.LOAN, WorkspaceType.LIABILITY, WorkspaceType.EMI -> amount
                        WorkspaceType.WARRANTY -> amount
                        WorkspaceType.BILL, WorkspaceType.SHOPPING_LIST, WorkspaceType.GOAL,
                        WorkspaceType.SHARED_EXPENSE, WorkspaceType.LOYALTY -> 0L
                        WorkspaceType.INVESTMENT, WorkspaceType.MUTUAL_FUND, WorkspaceType.GOLD,
                        WorkspaceType.FIXED_DEPOSIT, WorkspaceType.CRYPTO, WorkspaceType.ASSET ->
                            current.takeIf { it > 0L } ?: amount
                        else -> current
                    }
                }
                val status = WorkspaceStatus.entries.firstOrNull { it.label == b.statusInput.text.toString() } ?: WorkspaceStatus.ACTIVE
                val accountId = accounts.firstOrNull { it.name == b.accountInput.text.toString() }?.id
                val categoryId = categories.firstOrNull { it.name == b.categoryInput.text.toString() }?.id
                vm.saveWorkspaceItem(
                    WorkspaceItemDraft(
                        id = existing?.id,
                        type = type,
                        title = b.nameInput.text?.toString().orEmpty(),
                        amountMinor = amount,
                        currentMinor = current,
                        secondaryMinor = secondary,
                        currencyCode = currency,
                        secondaryCode = when {
                            config.usesRate || usesCadence -> secondaryCode
                            else -> existing?.secondaryCode.orEmpty()
                        },
                        startDate = b.startInput.text?.toString(),
                        dueDate = b.dueInput.text?.toString(),
                        accountId = accountId,
                        categoryId = categoryId,
                        linkedTransactionId = existing?.linkedTransactionId,
                        status = status,
                        note = b.noteInput.text?.toString().orEmpty(),
                        metadata = b.metadataInput.text?.toString().orEmpty()
                    )
                ) { result ->
                    result.onSuccess { dismiss() }
                    result.onFailure { b.nameLayout.error = it.message ?: "Could not save" }
                }
            }
        }
        return dialog
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

    private fun parseRate(text: String): Result<Long> = runCatching {
        require(text.trim().isNotEmpty()) { "Enter a rate" }
        BigDecimal(text.trim()).setScale(6, RoundingMode.HALF_UP).movePointRight(6).longValueExact()
    }

    private fun parseValue(text: String, currency: String, points: Boolean, allowBlank: Boolean): Result<Long> = runCatching {
        if (text.isBlank() && allowBlank) return@runCatching 0L
        if (points) {
            require(text.trim().isNotEmpty()) { "Enter points" }
            text.trim().toLong()
        } else Utils.parseMinor(text, currency).getOrThrow()
    }

    private fun clearErrors() {
        b.nameLayout.error = null
        b.amountLayout.error = null
        b.currentLayout.error = null
        b.currencyLayout.error = null
        b.secondaryCodeLayout.error = null
        b.secondaryAmountLayout.error = null
        b.startLayout.error = null
        b.dueLayout.error = null
    }

    companion object {
        private const val ARG_TYPE = "type"
        private const val ARG_ID = "id"
        fun newInstance(type: WorkspaceType, id: String? = null) = WorkspaceItemDialog().apply {
            arguments = Bundle().apply { putString(ARG_TYPE, type.name); putString(ARG_ID, id) }
        }
    }
}
