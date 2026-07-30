package com.mohnishraj.goldmineledger

import android.app.DatePickerDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.mohnishraj.goldmineledger.databinding.FragmentReportsBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class ReportsFragment : Fragment() {
    private var _b: FragmentReportsBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private var rendering = false
    private var hideAmounts = false
    private var latest = ReportState()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentReportsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.presetInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, ReportPreset.entries.map { it.label }))
        b.currencyInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, Utils.currencies.map(Utils::currencyLabel)))
        b.presetInput.setOnItemClickListener { _, _, _, _ ->
            if (!rendering) ReportPreset.entries.firstOrNull { it.label == b.presetInput.text.toString() }?.let(vm::setReportPreset)
        }
        b.currencyInput.setOnItemClickListener { _, _, _, _ ->
            if (!rendering) vm.setReportCurrency(Utils.currencyCode(b.currencyInput.text.toString()))
        }
        b.previous.setOnClickListener { vm.stepReport(-1) }
        b.next.setOnClickListener { vm.stepReport(1) }
        b.today.setOnClickListener { vm.setReportReference(LocalDate.now().toString()) }
        b.dateButton.setOnClickListener { pickDate(vm.report.value.referenceDate, vm::setReportReference) }
        b.fromInput.setOnClickListener { pickDate(b.fromInput.text?.toString().orEmpty()) { b.fromInput.setText(it) } }
        b.toInput.setOnClickListener { pickDate(b.toInput.text?.toString().orEmpty()) { b.toInput.setText(it) } }
        b.applyCustom.setOnClickListener { applyCustom() }
        b.budgetShortcut.setOnClickListener { findNavController().navigate(R.id.budgetsFragment) }
        b.calendarShortcut.setOnClickListener { findNavController().navigate(R.id.calendarFragment) }
        b.root.playScreenEntrance()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch { vm.report.collect { latest = it; render() } }
                launch { vm.settingsState.collect { hideAmounts = it.hideAmounts; render() } }
            }
        }
    }

    private fun render() {
        val state = latest
        rendering = true
        b.presetInput.setText(state.preset.label, false)
        b.currencyInput.setText(Utils.currencyLabel(state.currencyCode), false)
        b.dateButton.text = state.referenceDate
        b.dateControls.visibility = if (state.preset == ReportPreset.CUSTOM) View.GONE else View.VISIBLE
        b.customControls.visibility = if (state.preset == ReportPreset.CUSTOM) View.VISIBLE else View.GONE
        if (state.preset == ReportPreset.CUSTOM && !b.fromInput.hasFocus() && !b.toInput.hasFocus()) {
            b.fromInput.setText(state.fromDate)
            b.toInput.setText(state.toDate)
        }
        b.windowLabel.text = "${state.fromDate}  —  ${state.toDate}"
        b.netValue.text = money(state.netMinor, state.currencyCode)
        b.incomeValue.text = "INCOME\n${money(state.incomeMinor, state.currencyCode)}"
        b.expenseValue.text = "SPENT\n${money(state.expenseMinor, state.currencyCode)}"
        b.transferValue.text = "MOVED\n${money(state.transferVolumeMinor, state.currencyCode)}"
        b.flowRing.setValues(state.incomeMinor, state.expenseMinor)
        b.sparkline.setValues(state.dailyRows.asReversed().map { it.amountMinor })
        b.heatmap.setValues(state.spendingDailyRows, state.fromDate, state.toDate)
        b.financialScore.text = state.financialScore.toString()
        b.scoreLabel.text = state.scoreLabel
        b.savingsRate.text = state.savingsRatePercent?.let { "Savings rate $it%" } ?: "Savings rate — add income"
        b.budgetHealth.text = state.budgetUsedPercent?.let { used ->
            if (state.overBudgetCount > 0) "$used% used • ${state.overBudgetCount} over limit" else "$used% of active budgets used"
        } ?: "Budget health — no active budget"
        b.insightText.text = state.insightMessages.takeIf { it.isNotEmpty() }
            ?.joinToString("\n\n") { "• $it" }
            ?: "Add cleared transactions to unlock private, on-device insights."
        b.incomeStability.text = state.incomeStabilityLabel
        b.avgDailyMetric.text = "AVG / DAY\n${money(state.averageDailySpendMinor, state.currencyCode)}"
        b.transactionCountMetric.text = "TRANSACTIONS\n${state.transactionCount}"
        b.noSpendMetric.text = "NO-SPEND DAYS\n${state.noSpendDays}"
        b.volatilityMetric.text = "VARIATION\n${state.spendingVolatilityPercent?.let { "$it%" } ?: "—"}"
        val scoreColour = when {
            state.financialScore >= 80 -> R.color.income
            state.financialScore >= 45 -> R.color.primary
            else -> R.color.expense
        }
        b.financialScore.setTextColor(ContextCompat.getColor(requireContext(), scoreColour))
        val highestWeekday = state.weekdayRows.maxByOrNull { it.amountMinor }
        b.heatmapSummary.text = highestWeekday?.takeIf { it.amountMinor > 0L }?.let {
            if (hideAmounts) "${it.label} has the strongest recorded spending pattern" else "${it.label} is highest at ${Utils.money(it.amountMinor, state.currencyCode)}"
        } ?: "No spending recorded in this selected period"

        val previousNet = state.previousIncomeMinor - state.previousExpenseMinor
        val delta = state.netMinor - previousNet
        b.comparison.text = if (hideAmounts) {
            when {
                delta > 0 -> "Cash flow improved versus the previous equal-length period"
                delta < 0 -> "Cash flow softened versus the previous equal-length period"
                else -> "Cash flow is unchanged versus the previous period"
            }
        } else buildString {
            append(if (delta >= 0) "+" else "")
            append(Utils.money(delta, state.currencyCode))
            append(" versus previous period")
            if (previousNet != 0L) append(" • ${String.format(Locale.getDefault(), "%.1f", delta.toDouble() / abs(previousNet).toDouble() * 100)}%")
        }
        renderRows(b.categoryContainer, state.categoryRows, state.currencyCode, "No expense transactions in this period")
        renderRows(b.payeeContainer, state.topPayeeRows, state.currencyCode, "No labelled payees in this period")
        renderRows(b.accountContainer, state.accountRows, state.currencyCode, "No account movement in this period")
        renderRows(b.dailyContainer, state.dailyRows.take(60), state.currencyCode, "No daily activity in this period")
        rendering = false
    }

    private fun renderRows(container: LinearLayout, rows: List<ReportRow>, currency: String, emptyText: String) {
        container.removeAllViews()
        if (rows.isEmpty()) {
            container.addView(TextView(requireContext()).apply {
                text = emptyText
                setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
                gravity = android.view.Gravity.CENTER
                background = pillBackground(ContextCompat.getColor(requireContext(), R.color.surface_container_low))
                setPadding(dp(16), dp(20), dp(16), dp(20))
            })
            return
        }
        val maxAmount = rows.maxOfOrNull { abs(it.amountMinor) }?.coerceAtLeast(1L) ?: 1L
        rows.forEach { row ->
            container.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                background = pillBackground(ContextCompat.getColor(requireContext(), R.color.surface_container_low), 18f)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(5); bottomMargin = dp(5)
                }
                addView(LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    addView(TextView(requireContext()).apply {
                        text = row.label
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(TextView(requireContext()).apply {
                        text = money(row.amountMinor, currency)
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                    })
                })
                addView(LinearProgressIndicator(requireContext()).apply {
                    max = 100
                    progress = (abs(row.amountMinor).toDouble() / maxAmount.toDouble() * 100).roundToInt().coerceIn(0, 100)
                    trackColor = ContextCompat.getColor(requireContext(), R.color.outline_variant)
                    setIndicatorColor(ContextCompat.getColor(requireContext(), R.color.primary))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)).apply { topMargin = dp(10) }
                })
            })
        }
    }

    private fun pillBackground(colour: Int, radiusDp: Float = 22f) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(colour)
    }

    private fun money(value: Long, currency: String) = if (hideAmounts) "••••••" else Utils.money(value, currency)

    private fun applyCustom() {
        b.fromLayout.error = null
        b.toLayout.error = null
        val from = b.fromInput.text?.toString().orEmpty()
        val to = b.toInput.text?.toString().orEmpty()
        Utils.validDate(from)?.let { b.fromLayout.error = it; return }
        Utils.validDate(to)?.let { b.toLayout.error = it; return }
        if (from > to) { b.toLayout.error = "To date must be on or after from date"; return }
        vm.setReportCustomRange(from, to)
        Snackbar.make(b.root, "Custom range applied", Snackbar.LENGTH_SHORT).show()
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
