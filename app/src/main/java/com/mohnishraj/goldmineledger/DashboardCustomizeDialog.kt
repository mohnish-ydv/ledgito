package com.mohnishraj.goldmineledger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.mohnishraj.goldmineledger.databinding.DialogDashboardCustomiseBinding

class DashboardCustomizeDialog : BottomSheetDialogFragment() {
    private var _binding: DialogDashboardCustomiseBinding? = null
    private val binding get() = _binding!!
    private val vm get() = (requireActivity() as MainActivity).viewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = DialogDashboardCustomiseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val current = vm.settingsState.value.dashboardSections
        binding.pulseSwitch.isChecked = current.monthlyPulse
        binding.budgetSwitch.isChecked = current.budgetPulse
        binding.planSwitch.isChecked = current.planAndGrow
        binding.recentSwitch.isChecked = current.recentActivity
        binding.recurringSwitch.isChecked = current.plannedMoney
        binding.saveButton.setOnClickListener {
            it.confirmHaptic()
            vm.setDashboardSections(
                DashboardSections(
                    monthlyPulse = binding.pulseSwitch.isChecked,
                    budgetPulse = binding.budgetSwitch.isChecked,
                    planAndGrow = binding.planSwitch.isChecked,
                    recentActivity = binding.recentSwitch.isChecked,
                    plannedMoney = binding.recurringSwitch.isChecked
                )
            ) { result ->
                if (result.isSuccess) {
                    dismiss()
                } else {
                    _binding?.let { current ->
                        Snackbar.make(current.root, result.exceptionOrNull()?.message ?: "Could not save Home layout", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
        binding.root.playScreenEntrance(8f, 220L)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
