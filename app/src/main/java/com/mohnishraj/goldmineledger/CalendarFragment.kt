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
import com.mohnishraj.goldmineledger.databinding.FragmentCalendarBinding
import kotlinx.coroutines.launch
import java.time.LocalDate

class CalendarFragment : Fragment() {
    private var _b: FragmentCalendarBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private val adapter = TransactionAdapter { item, action ->
        when (action) {
            "edit" -> TransactionDialog.newInstance(item).show(parentFragmentManager, "calendar-transaction")
            "attachments" -> AttachmentListDialog.newInstance(item.attachments).show(parentFragmentManager, "calendar-attachments")
            "history" -> TransactionHistoryDialog.newInstance(item).show(parentFragmentManager, "calendar-history")
            "duplicate" -> TransactionDialog.newInstance(item, duplicate = true).show(parentFragmentManager, "calendar-copy")
            "delete" -> MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete transaction?")
                .setMessage("It will be removed from balances and reports. You can undo immediately.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    vm.deleteTransaction(item.entity) { result ->
                        result.onSuccess {
                            Snackbar.make(b.root, "Transaction deleted", Snackbar.LENGTH_LONG)
                                .setAction("Undo") { vm.undoDeleteTransaction(item.entity.id) { } }
                                .show()
                        }.onFailure { Snackbar.make(b.root, it.message ?: "Delete failed", Snackbar.LENGTH_LONG).show() }
                    }
                }.show()
        }
    }
    private var selectedDate = LocalDate.now().toString()
    private var allItems: List<TransactionUiModel> = emptyList()
    private var workspaceItems: List<WorkspaceItemUiModel> = emptyList()
    private var recurringItems: List<RecurringUiModel> = emptyList()
    private var hideAmounts = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentCalendarBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.list.adapter = adapter
        b.calendar.setOnDateChangeListener { _, year, month, day ->
            selectedDate = LocalDate.of(year, month + 1, day).toString()
            render()
        }
        b.addExpense.setOnClickListener { TransactionDialog.newInstance(null, TransactionType.EXPENSE, selectedDate).show(parentFragmentManager, "calendar-add") }
        b.addIncome.setOnClickListener { TransactionDialog.newInstance(null, TransactionType.INCOME, selectedDate).show(parentFragmentManager, "calendar-add") }
        b.agendaCard.setOnClickListener { findNavController().navigate(R.id.planningHubFragment) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch { vm.calendarTransactions.collect { allItems = it; render() } }
                launch { vm.workspaceItems.collect { workspaceItems = it; render() } }
                launch { vm.recurring.collect { recurringItems = it; render() } }
                launch {
                    vm.settingsState.collect {
                        hideAmounts = it.hideAmounts
                        adapter.setHideAmounts(hideAmounts)
                        render()
                    }
                }
            }
        }
        render()
    }

    private fun render() {
        if (_b == null) return
        val day = allItems.filter { it.entity.transactionDate == selectedDate }
        adapter.submitList(day.sortedByDescending { it.entity.createdAt })
        b.empty.visibility = if (day.isEmpty()) View.VISIBLE else View.GONE
        b.dayTitle.text = selectedDate
        val byCurrency = linkedMapOf<String, Long>()
        day.filter { it.entity.isCleared }.forEach { item ->
            val tx = item.entity
            when (TransactionType.from(tx.type)) {
                TransactionType.INCOME -> byCurrency[tx.currencyCode] = (byCurrency[tx.currencyCode] ?: 0L) + tx.amountMinor
                TransactionType.EXPENSE -> byCurrency[tx.currencyCode] = (byCurrency[tx.currencyCode] ?: 0L) - tx.amountMinor
                TransactionType.TRANSFER -> {
                    if (tx.transferFeeMinor > 0) byCurrency[tx.currencyCode] = (byCurrency[tx.currencyCode] ?: 0L) - tx.transferFeeMinor
                }
            }
        }
        b.daySummary.text = if (day.isEmpty()) "No entries" else buildString {
            append("${day.size} entr${if (day.size == 1) "y" else "ies"}")
            byCurrency.forEach { (currency, net) -> append(if (hideAmounts) " • Net ••••••" else " • Net ${Utils.money(net, currency)}") }
        }
        renderAgenda()
    }

    private fun renderAgenda() {
        val planned = workspaceItems.filter {
            it.entity.status == WorkspaceStatus.ACTIVE.name && it.entity.dueDate == selectedDate
        }.map { model ->
            val entity = model.entity
            val type = WorkspaceType.from(entity.type)
            val amount = when (type) {
                WorkspaceType.BILL -> (entity.amountMinor - entity.currentMinor).coerceAtLeast(0L)
                WorkspaceType.EMI, WorkspaceType.LOAN, WorkspaceType.DEBT, WorkspaceType.LIABILITY -> {
                    val remaining = entity.currentMinor.coerceAtLeast(0L)
                    if (entity.secondaryMinor > 0L) entity.secondaryMinor.coerceAtMost(remaining) else remaining
                }
                else -> entity.amountMinor.coerceAtLeast(0L)
            }
            Triple(WorkspaceCatalog.forType(type).icon, entity.title, if (hideAmounts) "••••" else Utils.money(amount, entity.currencyCode))
        }
        val recurring = recurringItems.filter { it.entity.isActive && it.entity.nextDueDate == selectedDate }.map { model ->
            Triple("↻", model.entity.name, if (hideAmounts) "••••" else Utils.money(model.entity.amountMinor, model.entity.currencyCode))
        }
        val agenda = planned + recurring
        b.agendaCard.visibility = if (agenda.isEmpty()) View.GONE else View.VISIBLE
        if (agenda.isEmpty()) return
        b.agendaTitle.text = "${agenda.size} planned item${if (agenda.size == 1) "" else "s"} due"
        b.agendaSummary.text = agenda.take(3).joinToString("\n") { (icon, title, amount) -> "$icon  $title • $amount" } +
            if (agenda.size > 3) "\n+${agenda.size - 3} more" else ""
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
