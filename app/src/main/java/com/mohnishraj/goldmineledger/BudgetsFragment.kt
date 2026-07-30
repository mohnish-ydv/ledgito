package com.mohnishraj.goldmineledger

import android.app.DatePickerDialog
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
import com.mohnishraj.goldmineledger.databinding.FragmentBudgetsBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

class BudgetsFragment : Fragment() {
    private var _b: FragmentBudgetsBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private val adapter = BudgetAdapter(::action)
    private var hideAmounts = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentBudgetsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.list.adapter = adapter
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.add.setOnClickListener { BudgetDialog.newInstance(null).show(parentFragmentManager, "budget") }
        b.previous.setOnClickListener { vm.stepBudgetReference(-1, ::showGenerationResult) }
        b.next.setOnClickListener { vm.stepBudgetReference(1, ::showGenerationResult) }
        b.today.setOnClickListener { vm.setBudgetReference(LocalDate.now().toString(), ::showGenerationResult) }
        b.dateButton.setOnClickListener { pickDate(vm.budgetReference.value) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    vm.budgets.collect { list ->
                        adapter.submitList(list)
                        b.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        b.list.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                        renderOverview(list)
                    }
                }
                launch { vm.budgetReference.collect { b.dateButton.text = it } }
                launch {
                    vm.settingsState.collect {
                        hideAmounts = it.hideAmounts
                        adapter.setHideAmounts(hideAmounts)
                        renderOverview(vm.budgets.value)
                    }
                }
            }
        }
    }

    private fun renderOverview(list: List<BudgetUiModel>) {
        if (_b == null) return
        val active = list.filter { it.entity.isActive && it.period != null }
        val attention = active.count { it.remainingMinor < 0 || (it.availableMinor > 0 && it.spentMinor.toDouble() / it.availableMinor.toDouble() >= 0.9) }
        val progressValues = active.mapNotNull {
            if (it.availableMinor <= 0) null else (it.spentMinor.toDouble() / it.availableMinor.toDouble() * 100).roundToInt().coerceAtLeast(0)
        }
        val percent = if (progressValues.isEmpty()) 0 else progressValues.average().roundToInt()
        val byCurrency = active.groupBy { it.entity.currencyCode }
        b.activeCount.text = "${active.size} active"
        b.attentionCount.text = "$attention need attention"
        b.budgetOverallProgress.progress = percent.coerceIn(0, 100)
        b.budgetOverallProgress.setIndicatorColor(
            resources.getColor(if (attention > 0) R.color.expense else R.color.secondary, requireContext().theme)
        )
        if (active.isEmpty()) {
            b.budgetHeadline.text = "No active budgets"
            b.budgetSubline.text = "Create a calm limit for the money that matters."
        } else {
            b.budgetHeadline.text = if (attention == 0) "Everything is on track" else "$attention budget${if (attention == 1) "" else "s"} need a look"
            b.budgetSubline.text = when {
                hideAmounts -> "$percent% average progress across active plans"
                byCurrency.size == 1 -> {
                    val currency = byCurrency.keys.first()
                    val spent = active.sumOf { it.spentMinor.coerceAtLeast(0) }
                    "${Utils.money(spent, currency)} used across active plans • $percent% average"
                }
                else -> "${byCurrency.size} currencies tracked separately • $percent% average progress"
            }
        }
    }

    private fun pickDate(value: String) {
        val initial = LocalDate.parse(value)
        DatePickerDialog(
            requireContext(),
            { _, year, month, day -> vm.setBudgetReference(LocalDate.of(year, month + 1, day).toString(), ::showGenerationResult) },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    private fun action(item: BudgetUiModel, action: String) {
        if (action == "edit") {
            BudgetDialog.newInstance(item).show(parentFragmentManager, "budget")
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete budget?")
                .setMessage("Budget history will be removed. Transactions will not be changed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    vm.deleteBudget(item.entity) { result ->
                        Snackbar.make(b.root, result.fold({ "Budget deleted" }, { it.message ?: "Delete failed" }), Snackbar.LENGTH_LONG).show()
                    }
                }
                .show()
        }
    }

    private fun showGenerationResult(result: Result<Int>) {
        result.exceptionOrNull()?.let {
            Snackbar.make(b.root, it.message ?: "Could not load budget period", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
