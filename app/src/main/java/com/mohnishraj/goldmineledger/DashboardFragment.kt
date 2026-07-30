package com.mohnishraj.goldmineledger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.mohnishraj.goldmineledger.databinding.FragmentDashboardBinding
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

class DashboardFragment : Fragment() {
    private var _b: FragmentDashboardBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private val recentAdapter = TransactionAdapter(::transactionAction)
    private var latest = DashboardState()
    private var latestForecast = ForecastState()
    private var latestNetWorth = NetWorthState()
    private var latestReport = ReportState()
    private var sections = DashboardSections()
    private var hiddenAmounts = false
    private var recentEmpty = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentDashboardBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.recentList.adapter = recentAdapter
        b.recentList.isNestedScrollingEnabled = false
        b.addExpense.setOnClickListener { it.confirmHaptic(); openTransaction(TransactionType.EXPENSE) }
        b.addIncome.setOnClickListener { it.confirmHaptic(); openTransaction(TransactionType.INCOME) }
        b.addTransfer.setOnClickListener { it.confirmHaptic(); openTransaction(TransactionType.TRANSFER) }
        b.settingsButton.setOnClickListener { findNavController().navigate(R.id.settingsFragment) }
        b.customiseButton.setOnClickListener {
            it.confirmHaptic()
            DashboardCustomizeDialog().show(parentFragmentManager, "dashboard-customise")
        }
        b.reportButton.setOnClickListener { findNavController().navigate(R.id.reportsFragment) }
        b.smartInsightCard.setOnClickListener { findNavController().navigate(R.id.reportsFragment) }
        b.budgetButton.setOnClickListener { findNavController().navigate(R.id.budgetsFragment) }
        b.dueRecurring.setOnClickListener { findNavController().navigate(R.id.recurringFragment) }
        b.viewAllTransactions.setOnClickListener { findNavController().navigate(R.id.transactionsFragment) }
        b.exploreButton.setOnClickListener { findNavController().navigate(R.id.moreFragment) }
        b.planningHubCard.setOnClickListener { findNavController().navigate(R.id.planningHubFragment) }
        b.wealthHubCard.setOnClickListener { findNavController().navigate(R.id.wealthHubFragment) }
        b.hideAmountsButton.setOnClickListener { vm.setHideAmounts(!hiddenAmounts) }

        b.greeting.text = greeting()
        b.root.playScreenEntrance()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    vm.dashboard.collect {
                        latest = it
                        renderDashboard()
                    }
                }
                launch {
                    vm.calendarTransactions.collect { list ->
                        recentAdapter.submitList(list.take(4))
                        recentEmpty = list.isEmpty()
                        renderSectionVisibility()
                    }
                }
                launch { vm.budgets.collect(::renderBudget) }
                launch { vm.forecast.collect { latestForecast = it; renderPlanAndGrow(); renderSmartInsight() } }
                launch { vm.netWorth.collect { latestNetWorth = it; renderPlanAndGrow() } }
                launch { vm.report.collect { latestReport = it; renderPulseAndInsight() } }
                launch {
                    vm.settingsState.collect {
                        hiddenAmounts = it.hideAmounts
                        sections = it.dashboardSections
                        recentAdapter.setHideAmounts(hiddenAmounts)
                        b.hideAmountsButton.setImageResource(if (hiddenAmounts) R.drawable.ic_eye_off else R.drawable.ic_eye)
                        b.hideAmountsButton.contentDescription = if (hiddenAmounts) "Show amounts" else "Hide amounts"
                        renderDashboard()
                        renderPulseAndInsight()
                        renderSectionVisibility()
                    }
                }
            }
        }
    }

    private fun renderDashboard() {
        if (_b == null) return
        val currency = latest.profile?.baseCurrency ?: "INR"
        val profileName = latest.profile?.name?.ifBlank { "Your money space" } ?: "Your money space"
        b.profileName.text = profileName
        b.avatar.text = profileName.firstOrNull()?.uppercase() ?: "L"
        b.totalValue.text = money(latest.currentTotal, currency)
        b.monthIncome.text = money(latest.monthIncome, currency)
        b.monthExpense.text = money(latest.monthExpense, currency)
        val net = latest.monthIncome - latest.monthExpense
        b.monthNet.text = money(net, currency)
        b.monthNetCaption.text = if (net >= 0) "Positive cash flow" else "Spending above income"
        b.balanceCaption.text = "Across ${latest.accountCount} active account${if (latest.accountCount == 1) "" else "s"} • fully offline"
        b.flowRing.setValues(latest.monthIncome, latest.monthExpense)
        b.recurringSummary.text = when (latest.dueRecurringCount) {
            0 -> "Everything is clear — nothing due today"
            1 -> "1 recurring item is ready to review"
            else -> "${latest.dueRecurringCount} recurring items are ready to review"
        }
        b.accountCount.text = latest.accountCount.toString()
        b.categoryCount.text = latest.categoryCount.toString()
        b.transactionCount.text = latest.transactionCount.toString()
        renderPlanAndGrow()
        renderSmartInsight()
    }

    private fun renderPulseAndInsight() {
        if (_b == null) return
        val values = latestReport.dailyRows.asReversed().map { it.amountMinor }
        b.homeSparkline.setValues(values)
        b.homeTrendCaption.text = when {
            values.isEmpty() -> "No data"
            latestReport.expenseChangePercent == null -> "First period"
            latestReport.expenseChangePercent!! <= -5 -> "Spending down ${-latestReport.expenseChangePercent!!}%"
            latestReport.expenseChangePercent!! >= 5 -> "Spending up ${latestReport.expenseChangePercent}%"
            else -> "Steady"
        }
        renderSmartInsight()
    }

    private fun renderSmartInsight() {
        if (_b == null) return
        val currency = latest.profile?.baseCurrency ?: latestReport.currencyCode
        val forecastDrop = latestForecast.lowestMinor < 0L
        val reportMessage = latestReport.insightMessages.firstOrNull()
        when {
            latest.dueRecurringCount > 0 -> {
                b.smartInsightIcon.text = "↻"
                b.smartInsightTitle.text = "Review planned money"
                b.smartInsightMessage.text = "${latest.dueRecurringCount} recurring item${if (latest.dueRecurringCount == 1) " is" else "s are"} ready to review today."
                b.smartInsightCard.setOnClickListener { findNavController().navigate(R.id.recurringFragment) }
            }
            latest.overBudgetCount > 0 -> {
                b.smartInsightIcon.text = "!"
                b.smartInsightTitle.text = "Budget needs attention"
                b.smartInsightMessage.text = "${latest.overBudgetCount} active budget${if (latest.overBudgetCount == 1) " is" else "s are"} currently over limit."
                b.smartInsightCard.setOnClickListener { findNavController().navigate(R.id.budgetsFragment) }
            }
            forecastDrop -> {
                b.smartInsightIcon.text = "◷"
                b.smartInsightTitle.text = "Cash-flow warning"
                b.smartInsightMessage.text = if (hiddenAmounts) {
                    "Your recorded 90-day plan falls below zero. Open the outlook to see when."
                } else {
                    "Your recorded plan reaches ${Utils.money(latestForecast.lowestMinor, currency)} around ${latestForecast.lowestDate}."
                }
                b.smartInsightCard.setOnClickListener { findNavController().navigate(R.id.forecastFragment) }
            }
            reportMessage != null -> {
                b.smartInsightIcon.text = "✦"
                b.smartInsightTitle.text = "Private money insight"
                b.smartInsightMessage.text = reportMessage
                b.smartInsightCard.setOnClickListener { findNavController().navigate(R.id.reportsFragment) }
            }
            else -> {
                b.smartInsightIcon.text = "✦"
                b.smartInsightTitle.text = "Your private money coach"
                b.smartInsightMessage.text = "Record a few cleared transactions and Ledgito will surface useful patterns here — completely offline."
                b.smartInsightCard.setOnClickListener { findNavController().navigate(R.id.transactionsFragment) }
            }
        }
        b.smartInsightCard.contentDescription = "${b.smartInsightTitle.text}. ${b.smartInsightMessage.text}. Double tap to open."
    }

    private fun renderPlanAndGrow() {
        if (_b == null) return
        val forecastCurrency = latestForecast.currencyCode
        val projectedChange = latestForecast.endingMinor - latestForecast.startingMinor
        b.planningHubValue.text = when {
            hiddenAmounts -> "90-day outlook ••••••"
            latestForecast.points.isEmpty() -> "Add bills and recurring money"
            else -> "90-day change ${Utils.money(projectedChange, forecastCurrency)}"
        }
        b.wealthHubValue.text = if (hiddenAmounts) {
            "Net worth ••••••"
        } else {
            "Net worth ${Utils.money(latestNetWorth.netWorthMinor, latestNetWorth.currencyCode)}"
        }
    }

    private fun renderBudget(models: List<BudgetUiModel>) {
        if (_b == null) return
        val active = models.firstOrNull { it.entity.isActive && it.period != null }
        if (active == null) {
            b.budgetTitle.text = "No active budget"
            b.budgetSummary.text = "Create one to get a calm spending guardrail"
            b.budgetPercent.text = "0%"
            b.budgetProgress.progress = 0
            return
        }
        val currency = active.entity.currencyCode
        val available = active.availableMinor.coerceAtLeast(1L)
        val percent = (active.spentMinor.toDouble() / available.toDouble() * 100).roundToInt().coerceAtLeast(0)
        b.budgetTitle.text = active.entity.name
        b.budgetSummary.text = if (hiddenAmounts) {
            if (active.remainingMinor >= 0) "On track for this period" else "Budget needs attention"
        } else {
            if (active.remainingMinor >= 0) "${Utils.money(active.remainingMinor, currency)} remaining" else "${Utils.money(-active.remainingMinor, currency)} over budget"
        }
        b.budgetPercent.text = "$percent%"
        b.budgetProgress.progress = percent.coerceIn(0, 100)
        b.budgetProgress.setIndicatorColor(resources.getColor(if (percent > 100) R.color.expense else R.color.primary, requireContext().theme))
    }

    private fun renderSectionVisibility() {
        if (_b == null) return
        val visible = View.VISIBLE
        val gone = View.GONE
        b.monthlyPulseHeader.visibility = if (sections.monthlyPulse) visible else gone
        b.monthlyPulseStats.visibility = if (sections.monthlyPulse) visible else gone
        b.homeTrendCard.visibility = if (sections.monthlyPulse) visible else gone
        b.budgetPulseHeader.visibility = if (sections.budgetPulse) visible else gone
        b.budgetCard.visibility = if (sections.budgetPulse) visible else gone
        b.planGrowHeader.visibility = if (sections.planAndGrow) visible else gone
        b.planGrowRow.visibility = if (sections.planAndGrow) visible else gone
        b.recentHeader.visibility = if (sections.recentActivity) visible else gone
        b.recentList.visibility = if (sections.recentActivity && !recentEmpty) visible else gone
        b.recentEmpty.visibility = if (sections.recentActivity && recentEmpty) visible else gone
        b.dueRecurring.visibility = if (sections.plannedMoney) visible else gone
    }

    private fun transactionAction(item: TransactionUiModel, action: String) {
        if (action == "edit") TransactionDialog.newInstance(item).show(parentFragmentManager, "transaction")
    }

    private fun openTransaction(type: TransactionType) {
        TransactionDialog.newInstance(null, type).show(parentFragmentManager, "transaction")
    }

    private fun money(value: Long, currency: String): String = if (hiddenAmounts) "••••••" else Utils.money(value, currency)

    private fun greeting(): String = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
