package com.mohnishraj.goldmineledger

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mohnishraj.goldmineledger.databinding.FragmentTransactionsBinding
import kotlinx.coroutines.launch

class TransactionsFragment : Fragment() {
    private var _b: FragmentTransactionsBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private val adapter = TransactionAdapter(::action)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentTransactionsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.list.adapter = adapter
        attachSwipeActions()
        val currentFilter = vm.transactionFilterState.value
        b.searchInput.setText(currentFilter.query)
        b.typeChips.check(when (currentFilter.type) {
            TransactionType.EXPENSE -> R.id.chipExpense
            TransactionType.INCOME -> R.id.chipIncome
            TransactionType.TRANSFER -> R.id.chipTransfer
            null -> R.id.chipAll
        })
        b.add.setOnClickListener { it.confirmHaptic(); TransactionDialog.newInstance(null).show(parentFragmentManager, "transaction") }
        b.filterButton.setOnClickListener { TransactionFilterDialog().show(parentFragmentManager, "transactionFilter") }
        b.savedFiltersButton.setOnClickListener { SavedFiltersDialog().show(parentFragmentManager, "saved-filters") }
        b.calendarButton.setOnClickListener { findNavController().navigate(R.id.calendarFragment) }
        b.recurringButton.setOnClickListener { findNavController().navigate(R.id.recurringFragment) }
        b.searchInput.doAfterTextChanged { vm.setSearch(it?.toString().orEmpty()) }
        b.typeChips.setOnCheckedStateChangeListener { _, ids ->
            vm.setTransactionType(when (ids.firstOrNull()) {
                R.id.chipExpense -> TransactionType.EXPENSE
                R.id.chipIncome -> TransactionType.INCOME
                R.id.chipTransfer -> TransactionType.TRANSFER
                else -> null
            })
        }
        b.root.playScreenEntrance()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    vm.transactions.collect { list ->
                        adapter.submitList(list)
                        b.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    vm.transactionFilterState.collect { filter ->
                        val count = filter.advancedCount()
                        b.filterButton.text = if (count == 0) "Filters" else "Filters ($count)"
                    }
                }
                launch { vm.settingsState.collect { adapter.setHideAmounts(it.hideAmounts) } }
            }
        }
    }

    private fun attachSwipeActions() {
        val deleteBackground = ColorDrawable(ContextCompat.getColor(requireContext(), R.color.expense))
        val editBackground = ColorDrawable(ContextCompat.getColor(requireContext(), R.color.primary))
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * resources.displayMetrics.scaledDensity
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, holder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.34f

            override fun onChildDraw(
                canvas: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val item = viewHolder.itemView
                if (dX > 0f) {
                    deleteBackground.setBounds(item.left, item.top, item.left + dX.toInt(), item.bottom)
                    deleteBackground.draw(canvas)
                    canvas.drawText("Delete", item.left + 22f * resources.displayMetrics.density, item.top + item.height / 2f + labelPaint.textSize / 3f, labelPaint)
                } else if (dX < 0f) {
                    editBackground.setBounds(item.right + dX.toInt(), item.top, item.right, item.bottom)
                    editBackground.draw(canvas)
                    val label = "Edit"
                    canvas.drawText(label, item.right - 22f * resources.displayMetrics.density - labelPaint.measureText(label), item.top + item.height / 2f + labelPaint.textSize / 3f, labelPaint)
                }
                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val model = adapter.itemAt(position)
                if (model == null) {
                    adapter.notifyDataSetChanged()
                    return
                }
                b.list.confirmHaptic()
                if (direction == ItemTouchHelper.LEFT) {
                    if (position != RecyclerView.NO_POSITION) adapter.notifyItemChanged(position)
                    TransactionDialog.newInstance(model).show(parentFragmentManager, "transaction-swipe-edit")
                } else {
                    deleteWithUndo(model)
                }
            }
        })
        helper.attachToRecyclerView(b.list)
    }

    private fun deleteWithUndo(item: TransactionUiModel) {
        vm.deleteTransaction(item.entity) { result ->
            val currentBinding = _b ?: return@deleteTransaction
            result.onSuccess {
                Snackbar.make(currentBinding.root, "Transaction deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        vm.undoDeleteTransaction(item.entity.id) { undo ->
                            val undoBinding = _b ?: return@undoDeleteTransaction
                            Snackbar.make(
                                undoBinding.root,
                                undo.fold({ "Transaction restored" }, { it.message ?: "Undo failed" }),
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                    .show()
            }.onFailure {
                adapter.notifyDataSetChanged()
                Snackbar.make(currentBinding.root, it.message ?: "Delete failed", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun action(item: TransactionUiModel, action: String) {
        when (action) {
            "edit" -> TransactionDialog.newInstance(item).show(parentFragmentManager, "transaction")
            "duplicate" -> TransactionDialog.newInstance(item, duplicate = true).show(parentFragmentManager, "transaction-copy")
            "history" -> TransactionHistoryDialog.newInstance(item).show(parentFragmentManager, "transaction-history")
            "attachments" -> AttachmentListDialog.newInstance(item.attachments).show(parentFragmentManager, "attachments")
            "delete" -> MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete transaction?")
                .setMessage("It will disappear from balances and reports. You can undo immediately; copied attachments stay protected.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ -> deleteWithUndo(item) }
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
