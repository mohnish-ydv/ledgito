package com.mohnishraj.goldmineledger

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mohnishraj.goldmineledger.databinding.DialogWorkspaceEventBinding
import java.time.LocalDate

class WorkspaceEventDialog : DialogFragment() {
    private lateinit var b: DialogWorkspaceEventBinding
    private val vm get() = (requireActivity() as MainActivity).viewModel

    override fun onCreateDialog(state: Bundle?): Dialog {
        b = DialogWorkspaceEventBinding.inflate(LayoutInflater.from(requireContext()))
        val itemId = requireArguments().getString(ARG_ID).orEmpty()
        val model = vm.workspaceItems.value.firstOrNull { it.entity.id == itemId }
            ?: return MaterialAlertDialogBuilder(requireContext()).setMessage("Item no longer exists").setPositiveButton("Close", null).create()
        val config = WorkspaceCatalog.forType(WorkspaceType.from(model.entity.type))
        val kind = config.eventKind ?: "EVENT"
        b.titleLabel.text = config.eventAction ?: "Add activity"
        b.amountLayout.hint = config.eventAmountLabel
        b.dateInput.setText(LocalDate.now().toString())
        b.dateInput.setOnClickListener { pickDate() }
        b.dateLayout.setEndIconOnClickListener { b.dateInput.performClick() }
        b.completedSwitch.isVisible = kind == "ITEM"
        b.completedSwitch.isChecked = kind == "ITEM"
        val canPost = kind == "PAYMENT" && config.supportsLedgerPost
        b.postToLedgerSwitch.isVisible = canPost
        b.postToLedgerSwitch.isEnabled = model.entity.accountId != null && model.entity.categoryId != null
        b.postToLedgerSwitch.text = if (b.postToLedgerSwitch.isEnabled) {
            "Also record this payment in Activity"
        } else {
            "Choose an account and expense category on this item to post payments"
        }
        b.labelInput.setText(
            when (kind) {
                "PAYMENT" -> "Payment"
                "CONTRIBUTION" -> "Contribution"
                "SETTLEMENT" -> "Settlement"
                "ITEM" -> "List item"
                "VALUE" -> "Value update"
                "POINTS" -> "Points earned"
                else -> "Activity"
            }
        )
        val type = WorkspaceType.from(model.entity.type)
        val suggestedAmount = when (type) {
            WorkspaceType.BILL -> (model.entity.amountMinor - model.entity.currentMinor).coerceAtLeast(0L)
            WorkspaceType.EMI, WorkspaceType.LOAN, WorkspaceType.DEBT, WorkspaceType.LIABILITY -> {
                val remaining = model.entity.currentMinor.coerceAtLeast(0L)
                model.entity.secondaryMinor.takeIf { it > 0L }?.coerceAtMost(remaining) ?: remaining
            }
            WorkspaceType.SHARED_EXPENSE -> (model.entity.amountMinor - model.entity.currentMinor).coerceAtLeast(0L)
            WorkspaceType.INVESTMENT, WorkspaceType.MUTUAL_FUND, WorkspaceType.GOLD,
            WorkspaceType.FIXED_DEPOSIT, WorkspaceType.PPF, WorkspaceType.EPF,
            WorkspaceType.CRYPTO, WorkspaceType.ASSET, WorkspaceType.CREDIT -> model.entity.currentMinor
            else -> 0L
        }
        if (suggestedAmount > 0L && !config.usesPoints) {
            b.amountInput.setText(Utils.plain(suggestedAmount, model.entity.currencyCode))
        }
        b.amountLayout.helperText = when (kind) {
            "PAYMENT", "SETTLEMENT" -> "Suggested amount shown; edit it for a partial payment."
            "VALUE" -> "Enter the latest total value, not only the change."
            "ITEM" -> "Enter the expected price for this list item."
            else -> null
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(b.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                b.labelLayout.error = null
                b.amountLayout.error = null
                val amount = runCatching {
                    if (config.usesPoints) b.amountInput.text?.toString().orEmpty().trim().toLong()
                    else Utils.parseMinor(b.amountInput.text?.toString().orEmpty(), model.entity.currencyCode).getOrThrow()
                }.getOrElse { b.amountLayout.error = it.message ?: "Invalid value"; return@setOnClickListener }
                vm.saveWorkspaceEvent(
                    WorkspaceEventDraft(
                        itemId = model.entity.id,
                        kind = kind,
                        label = b.labelInput.text?.toString().orEmpty(),
                        amountMinor = amount,
                        eventDate = b.dateInput.text?.toString().orEmpty(),
                        isCompleted = b.completedSwitch.isChecked,
                        note = b.noteInput.text?.toString().orEmpty(),
                        postToLedger = b.postToLedgerSwitch.isChecked && b.postToLedgerSwitch.isEnabled
                    )
                ) { result ->
                    result.onSuccess { dismiss() }
                    result.onFailure { b.labelLayout.error = it.message ?: "Could not save" }
                }
            }
        }
        return dialog
    }

    private fun pickDate() {
        val initial = runCatching { LocalDate.parse(b.dateInput.text?.toString().orEmpty()) }.getOrDefault(LocalDate.now())
        DatePickerDialog(
            requireContext(),
            { _, year, month, day -> b.dateInput.setText(LocalDate.of(year, month + 1, day).toString()) },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    companion object {
        private const val ARG_ID = "item_id"
        fun newInstance(itemId: String) = WorkspaceEventDialog().apply {
            arguments = Bundle().apply { putString(ARG_ID, itemId) }
        }
    }
}
