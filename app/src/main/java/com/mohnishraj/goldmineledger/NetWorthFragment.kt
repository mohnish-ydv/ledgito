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
import com.mohnishraj.goldmineledger.databinding.FragmentNetWorthBinding
import kotlinx.coroutines.launch

class NetWorthFragment : Fragment() {
    private var _b: FragmentNetWorthBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private var state = NetWorthState()
    private var hideAmounts = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentNetWorthBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.investmentsRow.setOnClickListener { open(WorkspaceType.INVESTMENT) }
        b.assetsRow.setOnClickListener { open(WorkspaceType.ASSET) }
        b.debtsRow.setOnClickListener { open(WorkspaceType.DEBT) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.netWorth.collect { this@NetWorthFragment.state = it; render() } }
                launch { vm.settingsState.collect { hideAmounts = it.hideAmounts; render() } }
            }
        }
    }

    private fun render() {
        if (_b == null) return
        fun money(value: Long) = if (hideAmounts) "••••" else Utils.money(value, state.currencyCode)
        b.netWorthAmount.text = money(state.netWorthMinor)
        b.accountsAmount.text = money(state.accountValueMinor)
        b.investmentsAmount.text = money(state.investmentValueMinor)
        b.assetsAmount.text = money(state.assetValueMinor)
        b.debtsAmount.text = if (hideAmounts) "••••" else "-${Utils.money(state.debtMinor, state.currencyCode)}"
        b.netWorthMeta.text = "Accounts + investments + assets - debts • ${state.currencyCode} only"
    }

    private fun open(type: WorkspaceType) {
        findNavController().navigate(R.id.workspaceFragment, Bundle().apply { putString(WorkspaceFragment.ARG_TYPE, type.name) })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
