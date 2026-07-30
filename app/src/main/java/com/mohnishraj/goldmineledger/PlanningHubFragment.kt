package com.mohnishraj.goldmineledger

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mohnishraj.goldmineledger.databinding.FragmentPlanningHubBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

class PlanningHubFragment : Fragment() {
    private var _b: FragmentPlanningHubBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private var items: List<WorkspaceItemUiModel> = emptyList()
    private var recurring: List<RecurringUiModel> = emptyList()
    private var hideAmounts = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentPlanningHubBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.forecastButton.setOnClickListener { findNavController().navigate(R.id.forecastFragment) }
        b.calendarButton.setOnClickListener { findNavController().navigate(R.id.calendarFragment) }
        b.billsCard.setOnClickListener { open(WorkspaceType.BILL) }
        b.emiCard.setOnClickListener { open(WorkspaceType.EMI) }
        b.loansCard.setOnClickListener { open(WorkspaceType.LOAN) }
        b.goalsCard.setOnClickListener { open(WorkspaceType.GOAL) }
        b.goalProgressCard.setOnClickListener { open(WorkspaceType.GOAL) }
        b.debtCard.setOnClickListener { open(WorkspaceType.DEBT) }
        b.subscriptionsCard.setOnClickListener { open(WorkspaceType.SUBSCRIPTION) }
        b.plannedCard.setOnClickListener { open(WorkspaceType.PLANNED_PAYMENT) }
        b.recurringCard.setOnClickListener { findNavController().navigate(R.id.recurringFragment) }
        b.debtStrategyButton.setOnClickListener { it.confirmHaptic(); showDebtStrategies() }
        b.root.playScreenEntrance()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.workspaceItems.collect { items = it; render() } }
                launch { vm.recurring.collect { recurring = it; render() } }
                launch { vm.settingsState.collect { hideAmounts = it.hideAmounts; render() } }
            }
        }
    }

    private fun render() {
        if (_b == null) return
        val currency = vm.profile.value?.baseCurrency ?: "INR"
        val active = items.filter {
            it.entity.status in setOf(WorkspaceStatus.ACTIVE.name, WorkspaceStatus.PAUSED.name) &&
                it.entity.currencyCode == currency
        }
        val commitmentTypes = setOf(
            WorkspaceType.BILL.name, WorkspaceType.EMI.name,
            WorkspaceType.SUBSCRIPTION.name, WorkspaceType.PLANNED_PAYMENT.name
        )
        val debtTypes = setOf(
            WorkspaceType.DEBT.name, WorkspaceType.LOAN.name,
            WorkspaceType.LIABILITY.name, WorkspaceType.EMI.name
        )
        val commitments = active.filter { it.entity.type in commitmentTypes }
        val debts = active.filter { it.entity.type in debtTypes }
        val goals = active.filter { it.entity.type == WorkspaceType.GOAL.name }

        val commitmentValue = commitments.sumOf(::scheduledValue)
        val debtValue = debts.sumOf { it.entity.currentMinor }
        val goalSaved = goals.sumOf { it.entity.currentMinor }
        val goalTarget = goals.sumOf { it.entity.amountMinor }

        b.commitmentsValue.text = money(commitmentValue, currency)
        b.commitmentsCaption.text = "${commitments.size} active item${if (commitments.size == 1) "" else "s"}"
        b.debtValue.text = money(debtValue, currency)
        b.debtCaption.text = "${debts.size} tracked balance${if (debts.size == 1) "" else "s"}"
        b.goalsValue.text = money(goalSaved, currency)
        b.goalsCaption.text = if (goals.isEmpty()) "No active goals" else if (hideAmounts) {
            "${goals.size} active goal${if (goals.size == 1) "" else "s"}"
        } else {
            "of ${Utils.money(goalTarget, currency)} target"
        }
        val goalPercent = if (goalTarget > 0L) {
            (goalSaved.toDouble() / goalTarget.toDouble() * 100.0).roundToInt().coerceIn(0, 100)
        } else 0
        b.goalProgress.progress = goalPercent
        b.goalProgressPercent.text = "$goalPercent%"
        b.goalProgressSummary.text = when {
            goals.isEmpty() -> "Add a savings goal to see progress here"
            hideAmounts -> "${goals.size} active goal${if (goals.size == 1) "" else "s"} progressing locally"
            else -> "${Utils.money(goalSaved, currency)} saved of ${Utils.money(goalTarget, currency)}"
        }

        val agenda = buildAgenda(active, currency)
        val next30 = agenda.filter { it.date <= LocalDate.now().plusDays(30) }
        val upcomingTotal = next30.sumOf { it.amountMinor.coerceAtLeast(0L) }
        b.upcomingAmount.text = money(upcomingTotal, currency)
        b.upcomingCaption.text = when {
            next30.isEmpty() -> "Nothing scheduled in the next 30 days"
            next30.size == 1 -> "1 scheduled obligation • ${next30.first().date}"
            else -> "${next30.size} scheduled obligations • next ${next30.first().date}"
        }
        renderAgenda(agenda.take(6), currency)
        renderPlanHealth(active, debts, goalPercent, agenda)
    }

    private fun renderPlanHealth(
        active: List<WorkspaceItemUiModel>,
        debts: List<WorkspaceItemUiModel>,
        goalPercent: Int,
        agenda: List<AgendaEntry>
    ) {
        val today = LocalDate.now()
        val overdue = active.count { model ->
            model.entity.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.isBefore(today) == true
        }
        val dueSoon = agenda.count { !it.date.isAfter(today.plusDays(7)) }
        var score = 62
        if (active.isNotEmpty()) score += 8
        if (goalPercent >= 20) score += 8
        if (goalPercent >= 60) score += 6
        score -= overdue * 14
        if (dueSoon > 4) score -= 8
        if (debts.size > 3) score -= 5
        score = score.coerceIn(0, 100)
        b.planHealthScore.text = score.toString()
        b.planHealthTitle.text = when {
            score >= 82 -> "Plan health • Strong"
            score >= 65 -> "Plan health • Stable"
            score >= 45 -> "Plan health • Building"
            else -> "Plan health • Needs attention"
        }
        b.planHealthScore.setTextColor(
            ContextCompat.getColor(requireContext(), when {
                score >= 75 -> R.color.income
                score >= 45 -> R.color.primary
                else -> R.color.expense
            })
        )
        b.planHealthMessage.text = when {
            overdue > 0 -> "$overdue recorded obligation${if (overdue == 1) " is" else "s are"} past its due date. Review the details before planning new spending."
            dueSoon > 0 -> "$dueSoon item${if (dueSoon == 1) " is" else "s are"} due in the next 7 days. Your timeline is ready for review."
            active.isEmpty() -> "Add commitments and goals to build your planning picture."
            else -> "No recorded obligation is overdue. Keep dates and balances updated for a reliable outlook."
        }
        b.debtStrategyButton.isEnabled = debts.isNotEmpty()
        b.debtStrategyButton.text = if (debts.isEmpty()) "Add debt to compare strategies" else "Compare ${debts.size} debt payoff strategies"
    }

    private fun showDebtStrategies() {
        val currency = vm.profile.value?.baseCurrency ?: "INR"
        val debtTypes = setOf(
            WorkspaceType.DEBT.name, WorkspaceType.LOAN.name,
            WorkspaceType.LIABILITY.name, WorkspaceType.EMI.name
        )
        val debts = items.filter {
            it.entity.status != WorkspaceStatus.ARCHIVED.name &&
                it.entity.type in debtTypes && it.entity.currentMinor > 0L &&
                it.entity.currencyCode == currency
        }
        if (debts.isEmpty()) return
        val snowball = debts.sortedBy { it.entity.currentMinor }
        fun apr(model: WorkspaceItemUiModel): Double? {
            val text = model.entity.metadata + " " + model.entity.note
            return Regex("(\\d+(?:\\.\\d+)?)\\s*%").find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        }
        val avalanche = debts.sortedWith(
            compareByDescending<WorkspaceItemUiModel> { apr(it) ?: Double.NEGATIVE_INFINITY }
                .thenBy { it.entity.currentMinor }
        )
        fun rows(models: List<WorkspaceItemUiModel>, showApr: Boolean) = models.mapIndexed { index, model ->
            val amount = if (hideAmounts) "amount hidden" else Utils.money(model.entity.currentMinor, currency)
            val rate = apr(model)?.let { " • ${String.format(java.util.Locale.getDefault(), "%.1f", it)}% recorded APR" }.orEmpty()
            "${index + 1}. ${model.entity.title} • $amount${if (showApr) rate else ""}"
        }.joinToString("\n")
        val knownApr = debts.count { apr(it) != null }
        val message = buildString {
            append("SNOWBALL — smallest recorded balance first\n")
            append(rows(snowball, false))
            append("\n\nAVALANCHE — highest recorded APR first\n")
            append(rows(avalanche, true))
            if (knownApr < debts.size) append("\n\n${debts.size - knownApr} item(s) have no APR in their notes, so they appear after known rates.")
            append("\n\nThis is an organising comparison based only on your entries, not financial advice.")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Debt payoff comparison")
            .setMessage(message)
            .setNegativeButton("Close", null)
            .setPositiveButton("Open debt tools") { _, _ -> open(WorkspaceType.DEBT) }
            .show()
    }

    private fun scheduledValue(model: WorkspaceItemUiModel): Long {
        val item = model.entity
        return when (WorkspaceType.from(item.type)) {
            WorkspaceType.BILL -> (item.amountMinor - item.currentMinor).coerceAtLeast(0L)
            WorkspaceType.EMI, WorkspaceType.LOAN, WorkspaceType.DEBT, WorkspaceType.LIABILITY ->
                item.secondaryMinor.takeIf { it > 0L }?.coerceAtMost(item.currentMinor) ?: item.currentMinor
            else -> item.amountMinor
        }
    }

    private data class AgendaEntry(
        val title: String,
        val date: LocalDate,
        val amountMinor: Long,
        val type: WorkspaceType?,
        val recurringRule: RecurringUiModel? = null
    )

    private fun buildAgenda(active: List<WorkspaceItemUiModel>, currency: String): List<AgendaEntry> {
        val today = LocalDate.now()
        val horizon = today.plusDays(90)
        val workspaceRows = active.flatMap { model ->
            val item = model.entity
            val dueDate = item.dueDate ?: return@flatMap emptyList()
            val type = WorkspaceType.from(item.type)
            val dates = if (type == WorkspaceType.SUBSCRIPTION) {
                Utils.billingOccurrences(dueDate, BillingCadence.from(item.secondaryCode), today, horizon)
            } else {
                listOfNotNull(runCatching { LocalDate.parse(dueDate) }.getOrNull())
                    .filter { !it.isBefore(today) && !it.isAfter(horizon) }
            }
            dates.map { date -> AgendaEntry(item.title, date, scheduledValue(model), type) }
        }
        val recurringRows = recurring.flatMap { rule ->
            val item = rule.entity
            if (!item.isActive || item.currencyCode != currency || item.type == TransactionType.INCOME.name) {
                return@flatMap emptyList()
            }
            val endDate = item.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            var remaining = item.occurrencesRemaining
            var date = runCatching { LocalDate.parse(item.nextDueDate) }.getOrNull() ?: return@flatMap emptyList()
            val rows = mutableListOf<AgendaEntry>()
            var safety = 0
            while (!date.isAfter(horizon) && safety < 1000) {
                if (remaining != null && remaining <= 0) break
                if (endDate != null && date.isAfter(endDate)) break
                if (!date.isBefore(today)) {
                    rows += AgendaEntry(item.name, date, item.amountMinor + item.transferFeeMinor, null, rule)
                }
                remaining = remaining?.minus(1)
                date = LocalDate.parse(
                    Utils.nextRecurrence(
                        date.toString(), RecurrenceFrequency.from(item.frequency), item.intervalCount,
                        item.anchorDay, MonthEndMode.from(item.monthEndMode)
                    )
                )
                safety++
            }
            rows
        }
        return (workspaceRows + recurringRows).sortedWith(compareBy<AgendaEntry> { it.date }.thenBy { it.title.lowercase() })
    }

    private fun renderAgenda(rows: List<AgendaEntry>, currency: String) {
        b.agendaContainer.removeAllViews()
        if (rows.isEmpty()) {
            b.agendaContainer.addView(TextView(requireContext()).apply {
                text = "Your next 90 days are clear. Add a bill, EMI, subscription or planned payment to build the timeline."
                setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface_variant))
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(18), dp(8), dp(18))
            })
            return
        }
        rows.forEachIndexed { index, row ->
            b.agendaContainer.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(60)
                isClickable = true
                isFocusable = true
                setPadding(dp(4), dp(8), dp(4), dp(8))
                contentDescription = "${row.title}, due ${row.date}, ${if (hideAmounts) "amount hidden" else Utils.money(row.amountMinor, currency)}"
                addView(TextView(context).apply {
                    text = row.date.dayOfMonth.toString()
                    gravity = Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.on_primary_container))
                    setBackgroundColor(ContextCompat.getColor(context, R.color.primary_container))
                    layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
                    textSize = 16f
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
                    addView(TextView(context).apply {
                        text = row.title
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                        maxLines = 1
                    })
                    addView(TextView(context).apply {
                        text = "${row.date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} • ${row.date}"
                        setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                        textSize = 12f
                    })
                })
                addView(TextView(context).apply {
                    text = money(row.amountMinor, currency)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                })
                setOnClickListener {
                    row.type?.let(::open) ?: findNavController().navigate(R.id.recurringFragment)
                }
            })
            if (index < rows.lastIndex) {
                b.agendaContainer.addView(View(requireContext()).apply {
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.outline_variant))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { marginStart = dp(54) }
                })
            }
        }
    }

    private fun open(type: WorkspaceType) {
        findNavController().navigate(
            R.id.workspaceFragment,
            Bundle().apply { putString(WorkspaceFragment.ARG_TYPE, type.name) }
        )
    }

    private fun money(value: Long, currency: String) = if (hideAmounts) "••••" else Utils.money(value, currency)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
