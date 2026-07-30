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
import com.mohnishraj.goldmineledger.databinding.FragmentAccountsBinding
import kotlinx.coroutines.launch

class AccountsFragment : Fragment() {
    private var _b: FragmentAccountsBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private val adapter = AccountAdapter(::action)
    private var latestDashboard = DashboardState()
    private var hideAmounts = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentAccountsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.list.adapter = adapter
        b.add.setOnClickListener { AccountDialog.newInstance(null).show(parentFragmentManager, "account") }
        b.filters.addOnButtonCheckedListener { _, id, checked ->
            if (checked) vm.setAccountFilter(when (id) {
                R.id.filterAll -> AccountFilter.ALL
                R.id.filterArchived -> AccountFilter.ARCHIVED
                else -> AccountFilter.ACTIVE
            })
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    vm.accountModels.collect {
                        adapter.submitList(it)
                        b.empty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    vm.dashboard.collect {
                        latestDashboard = it
                        renderSummary()
                    }
                }
                launch {
                    vm.settingsState.collect {
                        hideAmounts = it.hideAmounts
                        adapter.setHideAmounts(hideAmounts)
                        renderSummary()
                    }
                }
            }
        }
    }

    private fun renderSummary() {
        val currency = latestDashboard.profile?.baseCurrency ?: "INR"
        b.totalValue.text = if (hideAmounts) "••••••" else Utils.money(latestDashboard.currentTotal, currency)
        b.accountSummary.text = "${latestDashboard.accountCount} active account${if (latestDashboard.accountCount == 1) "" else "s"} • base currency $currency"
    }

    private fun action(item: AccountEntity, action: String) {
        when (action) {
            "edit" -> AccountDialog.newInstance(item).show(parentFragmentManager, "account")
            "adjust" -> BalanceAdjustmentDialog.newInstance(item.id).show(parentFragmentManager, "balance-adjustment")
            "archive" -> vm.archiveAccount(item, true, ::result)
            "restore" -> vm.archiveAccount(item, false, ::result)
            "delete" -> MaterialAlertDialogBuilder(requireContext()).setTitle("Delete ${item.name}?")
                .setMessage("Accounts used by transactions or recurring rules cannot be deleted. Archive them instead.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ -> vm.deleteAccount(item, ::result) }
                .show()
        }
    }

    private fun result(value: Result<Unit>) = Snackbar.make(
        b.root,
        value.fold({ "Done" }, { it.message ?: "Action failed" }),
        Snackbar.LENGTH_LONG
    ).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
