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
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mohnishraj.goldmineledger.databinding.FragmentWealthHubBinding
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class WealthHubFragment : Fragment() {
    private var _b: FragmentWealthHubBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private var netWorth = NetWorthState()
    private var items: List<WorkspaceItemUiModel> = emptyList()
    private var hideAmounts = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentWealthHubBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.netWorthButton.setOnClickListener { findNavController().navigate(R.id.netWorthFragment) }
        b.investmentsCard.setOnClickListener { open(WorkspaceType.INVESTMENT) }
        b.mutualFundsCard.setOnClickListener { open(WorkspaceType.MUTUAL_FUND) }
        b.goldCard.setOnClickListener { open(WorkspaceType.GOLD) }
        b.fdCard.setOnClickListener { open(WorkspaceType.FIXED_DEPOSIT) }
        b.ppfCard.setOnClickListener { open(WorkspaceType.PPF) }
        b.epfCard.setOnClickListener { open(WorkspaceType.EPF) }
        b.cryptoCard.setOnClickListener { open(WorkspaceType.CRYPTO) }
        b.assetsCard.setOnClickListener { open(WorkspaceType.ASSET) }
        b.liabilitiesCard.setOnClickListener { open(WorkspaceType.LIABILITY) }
        b.creditCard.setOnClickListener { open(WorkspaceType.CREDIT) }
        b.performanceCard.setOnClickListener { open(WorkspaceType.INVESTMENT) }
        b.root.playScreenEntrance()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.netWorth.collect { netWorth = it; render() } }
                launch { vm.workspaceItems.collect { items = it; render() } }
                launch { vm.settingsState.collect { hideAmounts = it.hideAmounts; render() } }
            }
        }
    }

    private fun render() {
        if (_b == null) return
        val currency = netWorth.currencyCode
        b.netWorthValue.text = money(netWorth.netWorthMinor, currency)
        b.netWorthCaption.text = if (netWorth.netWorthMinor >= 0) {
            "Accounts + holdings + assets − liabilities"
        } else {
            "Recorded liabilities are above recorded assets"
        }
        b.accountsValue.text = money(netWorth.accountValueMinor, currency)
        b.investmentsValue.text = money(netWorth.investmentValueMinor + netWorth.assetValueMinor, currency)
        b.liabilitiesValue.text = money(netWorth.debtMinor, currency)
        renderPerformance(currency)
        renderAllocation(currency)
    }

    private fun renderPerformance(currency: String) {
        val performanceTypes = setOf(
            WorkspaceType.INVESTMENT.name, WorkspaceType.MUTUAL_FUND.name,
            WorkspaceType.GOLD.name, WorkspaceType.FIXED_DEPOSIT.name,
            WorkspaceType.PPF.name, WorkspaceType.EPF.name, WorkspaceType.CRYPTO.name
        )
        val holdings = items.filter {
            it.entity.status != WorkspaceStatus.ARCHIVED.name &&
                it.entity.currencyCode == currency && it.entity.type in performanceTypes &&
                it.entity.amountMinor > 0L && it.entity.currentMinor > 0L
        }
        val cost = holdings.sumOf { it.entity.amountMinor }
        val current = holdings.sumOf { it.entity.currentMinor }
        val change = current - cost
        val percent = if (cost > 0L) (change.toDouble() / cost.toDouble() * 100.0) else 0.0
        b.performanceValue.text = when {
            holdings.isEmpty() -> "—"
            hideAmounts -> if (change >= 0L) "Positive" else "Negative"
            else -> "${if (change >= 0L) "+" else ""}${Utils.money(change, currency)}"
        }
        b.performanceCaption.text = when {
            holdings.isEmpty() -> "Add cost and current values to compare holdings"
            hideAmounts -> "${holdings.size} holding${if (holdings.size == 1) "" else "s"} compared locally"
            else -> "${String.format(java.util.Locale.getDefault(), "%+.1f", percent)}% across ${holdings.size} holding${if (holdings.size == 1) "" else "s"}"
        }
        b.performanceValue.setTextColor(
            ContextCompat.getColor(requireContext(), when {
                holdings.isEmpty() -> R.color.on_surface_variant
                change >= 0L -> R.color.income
                else -> R.color.expense
            })
        )
        b.performanceCard.contentDescription = when {
            holdings.isEmpty() -> "Recorded performance. Add a holding to compare cost and current value."
            hideAmounts -> "Recorded performance for ${holdings.size} holdings. Amounts hidden."
            else -> "Recorded performance ${b.performanceValue.text}, ${b.performanceCaption.text}"
        }
    }

    private fun renderAllocation(currency: String) {
        val active = items.filter {
            it.entity.status != WorkspaceStatus.ARCHIVED.name && it.entity.currencyCode == currency
        }
        fun total(vararg types: WorkspaceType) = active.filter { model -> types.any { it.name == model.entity.type } }.sumOf { it.entity.currentMinor }
        val rows = listOf(
            "Cash & accounts" to netWorth.accountValueMinor,
            "General investments" to total(WorkspaceType.INVESTMENT),
            "Mutual funds" to total(WorkspaceType.MUTUAL_FUND),
            "Gold" to total(WorkspaceType.GOLD),
            "Fixed deposits" to total(WorkspaceType.FIXED_DEPOSIT),
            "PPF & EPF" to total(WorkspaceType.PPF, WorkspaceType.EPF),
            "Crypto" to total(WorkspaceType.CRYPTO),
            "Other assets" to netWorth.assetValueMinor,
            "Liabilities" to -netWorth.debtMinor
        ).filter { it.second != 0L }

        val positiveRows = rows.filter { it.second > 0L }
        b.allocationDonut.setValues(
            positiveRows,
            if (hideAmounts) "••••" else Utils.compactMoney(netWorth.netWorthMinor, currency)
        )
        val positiveTotal = positiveRows.sumOf { it.second }
        val largest = positiveRows.maxByOrNull { it.second }
        val concentration = if (positiveTotal > 0L && largest != null) {
            (largest.second.toDouble() / positiveTotal.toDouble() * 100.0).roundToInt()
        } else 0
        b.diversificationMessage.text = when {
            positiveRows.isEmpty() -> "Add accounts or wealth items to build an allocation picture."
            positiveRows.size == 1 -> "Your recorded positive value is in one bucket. Add other asset types for a fuller picture."
            concentration >= 75 -> "${largest?.first} represents $concentration% of recorded positive value. This is a concentration note, not investment advice."
            concentration >= 50 -> "${largest?.first} is the largest bucket at $concentration%. Keep valuations current for a useful comparison."
            else -> "Recorded value is spread across ${positiveRows.size} buckets; the largest is ${largest?.first} at $concentration%."
        }
        b.allocationContainer.removeAllViews()
        if (rows.isEmpty()) {
            b.allocationContainer.addView(TextView(requireContext()).apply {
                text = "Add an account or wealth item to see a transparent allocation breakdown."
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(20), dp(8), dp(20))
                setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
            })
            return
        }
        val maxValue = rows.maxOf { abs(it.second) }.coerceAtLeast(1L)
        rows.forEachIndexed { index, row ->
            b.allocationContainer.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(2), dp(9), dp(2), dp(9))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(context).apply {
                        text = row.first
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(TextView(context).apply {
                        text = money(row.second, currency)
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                        setTextColor(ContextCompat.getColor(context, if (row.second < 0) R.color.expense else R.color.on_surface))
                    })
                })
                addView(LinearProgressIndicator(context).apply {
                    max = 100
                    progress = (abs(row.second).toDouble() / maxValue.toDouble() * 100).roundToInt().coerceIn(0, 100)
                    trackColor = ContextCompat.getColor(context, R.color.outline_variant)
                    setIndicatorColor(ContextCompat.getColor(context, if (row.second < 0) R.color.expense else R.color.primary))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)).apply { topMargin = dp(8) }
                })
            })
            if (index < rows.lastIndex) b.allocationContainer.addView(View(requireContext()).apply {
                setBackgroundColor(ContextCompat.getColor(context, R.color.outline_variant))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            })
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
