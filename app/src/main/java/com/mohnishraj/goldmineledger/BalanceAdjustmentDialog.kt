package com.mohnishraj.goldmineledger

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mohnishraj.goldmineledger.databinding.DialogBalanceAdjustmentBinding

class BalanceAdjustmentDialog : DialogFragment() {
    private lateinit var binding: DialogBalanceAdjustmentBinding
    private val vm get() = (requireActivity() as MainActivity).viewModel

    override fun onCreateDialog(state: Bundle?): Dialog {
        binding = DialogBalanceAdjustmentBinding.inflate(LayoutInflater.from(requireContext()))
        val accountId = requireArguments().getString(ARG_ACCOUNT_ID).orEmpty()
        val model = vm.accountModels.value.firstOrNull { it.entity.id == accountId }
            ?: return MaterialAlertDialogBuilder(requireContext()).setMessage("Account no longer exists").setPositiveButton("Close", null).create()
        binding.summary.text = "${model.entity.name} • ${model.entity.currencyCode}\nCurrent: ${Utils.money(model.currentBalanceMinor, model.entity.currencyCode)}"
        binding.amountInput.setText(Utils.plain(model.currentBalanceMinor, model.entity.currencyCode))
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Adjust account balance")
            .setView(binding.root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Record adjustment", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                binding.amountLayout.error = null
                binding.reasonLayout.error = null
                val target = Utils.parseMinor(binding.amountInput.text?.toString().orEmpty(), model.entity.currencyCode)
                    .getOrElse { binding.amountLayout.error = it.message ?: "Invalid amount"; return@setOnClickListener }
                val reason = binding.reasonInput.text?.toString().orEmpty()
                if (reason.trim().length < 3) {
                    binding.reasonLayout.error = "Add a short reason"
                    return@setOnClickListener
                }
                vm.adjustAccountBalance(model.entity, target, reason) { result ->
                    result.onSuccess { dismiss() }
                    result.onFailure { binding.reasonLayout.error = it.message ?: "Could not adjust balance" }
                }
            }
        }
        return dialog
    }

    companion object {
        private const val ARG_ACCOUNT_ID = "account_id"
        fun newInstance(accountId: String) = BalanceAdjustmentDialog().apply {
            arguments = Bundle().apply { putString(ARG_ACCOUNT_ID, accountId) }
        }
    }
}
