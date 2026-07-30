package com.mohnishraj.goldmineledger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.mohnishraj.goldmineledger.databinding.FragmentForecastBinding
import kotlinx.coroutines.launch

class ForecastFragment : Fragment() {
    private var _b: FragmentForecastBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private var state = ForecastState()
    private var days = 90
    private var hideAmounts = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentForecastBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.rangeChips.setOnCheckedStateChangeListener { _, checked ->
            days = when (checked.firstOrNull()) {
                R.id.chip30 -> 30
                R.id.chip60 -> 60
                else -> 90
            }
            render()
        }
        b.plannedButton.setOnClickListener { openWorkspace(WorkspaceType.PLANNED_PAYMENT) }
        b.recurringButton.setOnClickListener { findNavController().navigate(R.id.recurringFragment) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.forecast.collect { this@ForecastFragment.state = it; render() } }
                launch { vm.settingsState.collect { hideAmounts = it.hideAmounts; render() } }
            }
        }
    }

    private fun render() {
        if (_b == null) return
        val visible = state.points.take(days)
        val ending = visible.lastOrNull()?.balanceMinor ?: state.startingMinor
        val lowestPoint = visible.minByOrNull { it.balanceMinor }
        fun money(value: Long) = if (hideAmounts) "••••" else Utils.money(value, state.currencyCode)
        b.endingAmount.text = money(ending)
        b.startingAmount.text = money(state.startingMinor)
        b.lowestAmount.text = money(lowestPoint?.balanceMinor ?: state.startingMinor)
        b.lowestDate.text = lowestPoint?.date.orEmpty()
        b.chart.setValues(visible.map { it.balanceMinor })
        b.assumptions.text = state.assumptions.joinToString("\n\n") { "• $it" } +
            "\n\n• Forecasts are planning aids, not guarantees. Manual future income or expenses not recorded in Ledgito are excluded."
    }

    private fun openWorkspace(type: WorkspaceType) {
        findNavController().navigate(R.id.workspaceFragment, Bundle().apply { putString(WorkspaceFragment.ARG_TYPE, type.name) })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
