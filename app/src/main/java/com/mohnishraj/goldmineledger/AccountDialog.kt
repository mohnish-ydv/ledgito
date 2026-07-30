package com.mohnishraj.goldmineledger

import android.app.Dialog
import android.os.Bundle
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mohnishraj.goldmineledger.databinding.DialogAccountBinding
import java.time.LocalDate

class AccountDialog : DialogFragment() {
    private lateinit var b: DialogAccountBinding
    private val vm get() = (requireActivity() as MainActivity).viewModel

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        b = DialogAccountBinding.inflate(layoutInflater)
        b.typeInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, AccountType.entries.map { it.label }))
        b.currencyInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, Utils.currencies.map(Utils::currencyLabel)))
        b.colourInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, COLOURS.map { it.first }))
        val a = arguments
        val currency = a?.getString("currency") ?: vm.profile.value?.baseCurrency ?: "INR"
        b.nameInput.setText(a?.getString("name").orEmpty())
        b.typeInput.setText((a?.getString("type")?.let(AccountType::from) ?: AccountType.CASH).label, false)
        b.currencyInput.setText(Utils.currencyLabel(currency), false)
        b.balanceInput.setText(a?.getLong("balance")?.let { Utils.plain(it, currency) } ?: "0")
        b.dateInput.setText(a?.getString("date") ?: LocalDate.now().toString())
        b.includeSwitch.isChecked = a?.getBoolean("include", true) ?: true
        b.archivedSwitch.isChecked = a?.getBoolean("archived", false) ?: false
        val colour = a?.takeIf { it.containsKey("colour") }?.getInt("colour") ?: COLOURS.first().second
        b.colourInput.setText(COLOURS.firstOrNull { it.second == colour }?.first ?: COLOURS.first().first, false)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (a?.getString("id") == null) "New account" else "Edit account")
            .setView(b.root).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener { dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { save(dialog) } }
        return dialog
    }

    private fun save(dialog: AlertDialog) {
        b.nameLayout.error = null; b.currencyLayout.error = null; b.balanceLayout.error = null; b.dateLayout.error = null
        val name = b.nameInput.text?.toString().orEmpty()
        Utils.validName(name, 60)?.let { b.nameLayout.error = it; return }
        val currency = Utils.currencyCode(b.currencyInput.text.toString())
        Utils.validCurrency(currency)?.let { b.currencyLayout.error = it; return }
        val amount = Utils.parseMinor(b.balanceInput.text?.toString().orEmpty(), currency)
        if (amount.isFailure) { b.balanceLayout.error = amount.exceptionOrNull()?.message; return }
        val date = b.dateInput.text?.toString().orEmpty()
        Utils.validDate(date)?.let { b.dateLayout.error = it; return }
        val type = AccountType.entries.firstOrNull { it.label == b.typeInput.text.toString() } ?: AccountType.CUSTOM
        val colour = COLOURS.firstOrNull { it.first == b.colourInput.text.toString() }?.second ?: COLOURS.first().second
        vm.saveAccount(AccountDraft(arguments?.getString("id"), name, type, currency, amount.getOrThrow(), date, b.includeSwitch.isChecked, b.archivedSwitch.isChecked, colour)) {
            it.onSuccess { dialog.dismiss() }.onFailure { e -> Snackbar.make(b.root, e.message ?: "Could not save", Snackbar.LENGTH_LONG).show() }
        }
    }

    companion object {
        private val COLOURS = listOf(
            "Forest" to 0xFF2F6B4F.toInt(), "Ocean" to 0xFF315D8C.toInt(), "Plum" to 0xFF7B4D8E.toInt(),
            "Amber" to 0xFF8A5A00.toInt(), "Brick" to 0xFF9C4138.toInt(), "Slate" to 0xFF59646A.toInt()
        )
        fun newInstance(item: AccountEntity?) = AccountDialog().apply {
            arguments = Bundle().apply { if (item != null) {
                putString("id", item.id); putString("name", item.name); putString("type", item.type)
                putString("currency", item.currencyCode); putLong("balance", item.openingBalanceMinor)
                putString("date", item.openingDate); putBoolean("include", item.includeInTotal)
                putBoolean("archived", item.isArchived); putInt("colour", item.colourArgb)
            } }
        }
    }
}
