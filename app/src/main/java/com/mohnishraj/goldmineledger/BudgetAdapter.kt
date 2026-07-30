package com.mohnishraj.goldmineledger

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mohnishraj.goldmineledger.databinding.ItemBudgetBinding
import kotlin.math.roundToInt

class BudgetAdapter(private val action: (BudgetUiModel, String) -> Unit) :
    ListAdapter<BudgetUiModel, BudgetAdapter.Holder>(object : DiffUtil.ItemCallback<BudgetUiModel>() {
        override fun areItemsTheSame(a: BudgetUiModel, b: BudgetUiModel) = a.entity.id == b.entity.id
        override fun areContentsTheSame(a: BudgetUiModel, b: BudgetUiModel) = a == b
    }) {

    private var hideAmounts = false

    fun setHideAmounts(value: Boolean) {
        if (hideAmounts == value) return
        hideAmounts = value
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemBudgetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val b: ItemBudgetBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(model: BudgetUiModel) {
            val item = model.entity
            b.name.text = item.name
            b.meta.text = buildString {
                append(BudgetPeriodType.from(item.periodType).label)
                model.categoryName?.let { append(" • $it") } ?: append(" • All expenses")
                model.period?.let { append("\n${it.periodStart} — ${it.periodEnd}") }
                if (!item.isActive) append(" • Paused")
            }
            val rawProgress = if (model.availableMinor <= 0) 0 else {
                ((model.spentMinor.toDouble() / model.availableMinor.toDouble()) * 100).roundToInt().coerceAtLeast(0)
            }
            b.progress.setProgressCompat(rawProgress.coerceIn(0, 100), true)
            val context = b.root.context
            val needsAttention = model.remainingMinor < 0 || rawProgress >= 90
            b.progress.setIndicatorColor(ContextCompat.getColor(context, if (needsAttention) R.color.expense else R.color.primary))
            b.remaining.setTextColor(ContextCompat.getColor(context, if (model.remainingMinor < 0) R.color.expense else R.color.secondary))
            b.icon.text = when {
                !item.isActive -> "Ⅱ"
                model.remainingMinor < 0 -> "!"
                rawProgress >= 75 -> "◔"
                else -> "◎"
            }
            b.spent.text = if (hideAmounts) {
                "${rawProgress.coerceAtLeast(0)}% used"
            } else {
                "Spent ${Utils.money(model.spentMinor, item.currencyCode)} of ${Utils.money(model.availableMinor, item.currencyCode)}"
            }
            b.remaining.text = if (hideAmounts) {
                if (model.remainingMinor >= 0) "On track" else "Over budget"
            } else if (model.remainingMinor >= 0) {
                "${Utils.money(model.remainingMinor, item.currencyCode)} left"
            } else {
                "${Utils.money(-model.remainingMinor, item.currencyCode)} over"
            }
            b.root.setOnClickListener { action(model, "edit") }
            b.more.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    menuInflater.inflate(R.menu.budget_actions, menu)
                    setOnMenuItemClickListener {
                        action(model, if (it.itemId == R.id.deleteBudget) "delete" else "edit")
                        true
                    }
                    show()
                }
            }
        }
    }
}
