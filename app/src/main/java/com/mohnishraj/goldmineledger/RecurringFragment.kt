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
import com.mohnishraj.goldmineledger.databinding.FragmentRecurringBinding
import kotlinx.coroutines.launch
import java.time.LocalDate

class RecurringFragment : Fragment() {
    private var _b: FragmentRecurringBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private val adapter = RecurringAdapter(::action)
    private var hideAmounts = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentRecurringBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.list.adapter = adapter
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.add.setOnClickListener { RecurringDialog.newInstance(null).show(parentFragmentManager, "recurring") }
        b.postDue.setOnClickListener {
            vm.postDueRecurring { result ->
                Snackbar.make(b.root, result.fold({ "$it due transaction${if (it == 1) "" else "s"} posted" }, { it.message ?: "Could not post due rules" }), Snackbar.LENGTH_LONG).show()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    vm.recurring.collect { list ->
                        adapter.submitList(list)
                        b.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        b.list.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                        renderOverview(list)
                    }
                }
                launch {
                    vm.settingsState.collect {
                        hideAmounts = it.hideAmounts
                        adapter.setHideAmounts(hideAmounts)
                    }
                }
            }
        }
    }

    private fun renderOverview(list: List<RecurringUiModel>) {
        if (_b == null) return
        val today = LocalDate.now().toString()
        val active = list.count { it.entity.isActive }
        val due = list.count { it.entity.isActive && it.entity.nextDueDate <= today }
        b.activeCount.text = "$active active rule${if (active == 1) "" else "s"} • ${list.size - active} paused"
        b.dueCount.text = when (due) {
            0 -> "Nothing due today"
            1 -> "1 item is ready"
            else -> "$due items are ready"
        }
        b.postDue.isEnabled = due > 0
        b.postDue.text = if (due > 0) "Review and post $due due" else "All caught up"
    }

    private fun action(item: RecurringUiModel, action: String) {
        when (action) {
            "edit" -> RecurringDialog.newInstance(item).show(parentFragmentManager, "recurring")
            "run" -> vm.runRecurringNow(item.entity) { result ->
                Snackbar.make(b.root, result.fold({ "Transaction created now" }, { it.message ?: "Could not run rule" }), Snackbar.LENGTH_LONG).show()
            }
            "skip" -> vm.skipRecurring(item.entity) { result ->
                Snackbar.make(b.root, result.fold({ "Next occurrence skipped" }, { it.message ?: "Could not skip" }), Snackbar.LENGTH_LONG).show()
            }
            "pause" -> vm.setRecurringActive(item.entity, false) { result ->
                Snackbar.make(b.root, result.fold({ "Rule paused" }, { it.message ?: "Could not pause" }), Snackbar.LENGTH_LONG).show()
            }
            "resume" -> vm.setRecurringActive(item.entity, true) { result ->
                Snackbar.make(b.root, result.fold({ "Rule resumed" }, { it.message ?: "Could not resume" }), Snackbar.LENGTH_LONG).show()
            }
            "delete" -> MaterialAlertDialogBuilder(requireContext()).setTitle("Delete recurring rule?")
                .setMessage("Transactions already posted from this rule will remain.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                    vm.deleteRecurring(item.entity) { result -> Snackbar.make(b.root, result.fold({ "Rule deleted" }, { it.message ?: "Delete failed" }), Snackbar.LENGTH_LONG).show() }
                }.show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
