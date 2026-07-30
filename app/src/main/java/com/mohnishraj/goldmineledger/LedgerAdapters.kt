package com.mohnishraj.goldmineledger

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mohnishraj.goldmineledger.databinding.ItemAccountBinding
import com.mohnishraj.goldmineledger.databinding.ItemCategoryBinding
import com.mohnishraj.goldmineledger.databinding.ItemRecurringBinding
import com.mohnishraj.goldmineledger.databinding.ItemTransactionBinding

class AccountAdapter(private val action: (AccountEntity, String) -> Unit) :
    ListAdapter<AccountUiModel, AccountAdapter.Holder>(object : DiffUtil.ItemCallback<AccountUiModel>() {
        override fun areItemsTheSame(a: AccountUiModel, b: AccountUiModel) = a.entity.id == b.entity.id
        override fun areContentsTheSame(a: AccountUiModel, b: AccountUiModel) = a == b
    }) {
    private var hideAmounts = false

    fun setHideAmounts(value: Boolean) {
        if (hideAmounts == value) return
        hideAmounts = value
        notifyItemRangeChanged(0, itemCount)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    inner class Holder(private val b: ItemAccountBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(model: AccountUiModel) {
            val item = model.entity
            b.name.text = item.name
            b.meta.text = buildString {
                append(AccountType.from(item.type).label)
                append(" • ${item.currencyCode}")
                if (!item.includeInTotal) append(" • Hidden from total")
                if (item.isArchived) append(" • Archived")
            }
            b.balance.text = if (hideAmounts) "Current: ••••••" else "Current: ${Utils.money(model.currentBalanceMinor, item.currencyCode)}"
            b.icon.text = item.name.firstOrNull()?.uppercase() ?: "A"
            b.icon.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(item.colourArgb) }
            b.root.setOnClickListener { action(item, "edit") }
            b.more.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    menuInflater.inflate(R.menu.item_actions, menu)
                    menu.findItem(R.id.archive).isVisible = !item.isArchived
                    menu.findItem(R.id.restore).isVisible = item.isArchived
                    menu.findItem(R.id.adjustBalance).isVisible = !item.isArchived
                    menu.findItem(R.id.mergeCategory).isVisible = false
                    setOnMenuItemClickListener {
                        action(item, when (it.itemId) {
                            R.id.archive -> "archive"
                            R.id.restore -> "restore"
                            R.id.adjustBalance -> "adjust"
                            R.id.delete -> "delete"
                            else -> "edit"
                        })
                        true
                    }
                    show()
                }
            }
        }
    }
}

class CategoryAdapter(private val action: (CategoryEntity, String) -> Unit) :
    ListAdapter<CategoryEntity, CategoryAdapter.Holder>(object : DiffUtil.ItemCallback<CategoryEntity>() {
        override fun areItemsTheSame(a: CategoryEntity, b: CategoryEntity) = a.id == b.id
        override fun areContentsTheSame(a: CategoryEntity, b: CategoryEntity) = a == b
    }) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    inner class Holder(private val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: CategoryEntity) {
            val parent = currentList.firstOrNull { it.id == item.parentId }?.name
            b.name.text = if (parent == null) item.name else "↳ ${item.name}"
            b.meta.text = buildString {
                append(CategoryKind.from(item.kind).label)
                if (parent != null) append(" • Under $parent")
                if (item.isSystem) append(" • Default")
                if (item.isArchived) append(" • Archived")
            }
            b.icon.text = item.name.firstOrNull()?.uppercase() ?: "C"
            b.icon.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(item.colourArgb) }
            b.root.setOnClickListener { action(item, "edit") }
            b.more.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    menuInflater.inflate(R.menu.item_actions, menu)
                    menu.findItem(R.id.archive).isVisible = !item.isArchived
                    menu.findItem(R.id.restore).isVisible = item.isArchived
                    menu.findItem(R.id.delete).isVisible = !item.isSystem
                    menu.findItem(R.id.adjustBalance).isVisible = false
                    menu.findItem(R.id.mergeCategory).isVisible = !item.isArchived
                    setOnMenuItemClickListener {
                        action(item, when (it.itemId) {
                            R.id.archive -> "archive"
                            R.id.restore -> "restore"
                            R.id.mergeCategory -> "merge"
                            R.id.delete -> "delete"
                            else -> "edit"
                        })
                        true
                    }
                    show()
                }
            }
        }
    }
}

class TransactionAdapter(private val action: (TransactionUiModel, String) -> Unit) :
    ListAdapter<TransactionUiModel, TransactionAdapter.Holder>(object : DiffUtil.ItemCallback<TransactionUiModel>() {
        override fun areItemsTheSame(a: TransactionUiModel, b: TransactionUiModel) = a.entity.id == b.entity.id
        override fun areContentsTheSame(a: TransactionUiModel, b: TransactionUiModel) = a == b
    }) {
    private var hideAmounts = false

    fun setHideAmounts(value: Boolean) {
        if (hideAmounts == value) return
        hideAmounts = value
        notifyItemRangeChanged(0, itemCount)
    }
    fun itemAt(position: Int): TransactionUiModel? = currentList.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    inner class Holder(private val b: ItemTransactionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: TransactionUiModel) {
            val tx = item.entity
            val type = TransactionType.from(tx.type)
            b.typeBadge.text = when (type) { TransactionType.EXPENSE -> "E"; TransactionType.INCOME -> "I"; TransactionType.TRANSFER -> "T" }
            b.typeBadge.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(when (type) { TransactionType.EXPENSE -> 0xFFB3261E.toInt(); TransactionType.INCOME -> 0xFF1F6F43.toInt(); TransactionType.TRANSFER -> 0xFF315D8C.toInt() })
            }
            b.title.text = tx.payee.ifBlank { item.categoryName ?: type.label }
            b.meta.text = buildString {
                append(tx.transactionDate)
                append(" • ${item.accountName}")
                item.destinationAccountName?.let { append(" → $it") }
                item.categoryName?.let { append(" • $it") }
                if (item.tags.isNotEmpty()) append(" • #${item.tags.joinToString(" #")}")
                if (item.splits.isNotEmpty()) append(" • ${item.splits.size} splits")
                if (item.attachments.isNotEmpty()) append(" • ${item.attachments.size} attachment${if (item.attachments.size == 1) "" else "s"}")
                if (!tx.isCleared) append(" • Pending")
                item.runningBalanceMinor?.let {
                    append(if (hideAmounts) " • Balance ••••••" else " • Balance ${Utils.money(it, item.runningBalanceCurrency ?: tx.currencyCode)}")
                }
            }
            val prefix = when (type) { TransactionType.EXPENSE -> "−"; TransactionType.INCOME -> "+"; TransactionType.TRANSFER -> "↔" }
            val destinationCurrency = tx.destinationCurrencyCode
            b.amount.text = if (hideAmounts) {
                "••••••"
            } else if (type == TransactionType.TRANSFER && destinationCurrency != null && destinationCurrency != tx.currencyCode) {
                "$prefix ${Utils.money(tx.amountMinor, tx.currencyCode)} → ${Utils.money(tx.destinationAmountMinor, destinationCurrency)}" +
                    if (tx.transferFeeMinor > 0) "\nFee ${Utils.money(tx.transferFeeMinor, tx.currencyCode)}" else ""
            } else {
                "$prefix ${Utils.money(tx.amountMinor, tx.currencyCode)}" + if (tx.transferFeeMinor > 0) "\nFee ${Utils.money(tx.transferFeeMinor, tx.currencyCode)}" else ""
            }
            b.root.setOnClickListener { action(item, "edit") }
            b.more.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    menuInflater.inflate(R.menu.transaction_actions, menu)
                    menu.findItem(R.id.attachments).isVisible = item.attachments.isNotEmpty()
                    setOnMenuItemClickListener {
                        action(item, when (it.itemId) {
                            R.id.delete -> "delete"
                            R.id.attachments -> "attachments"
                            R.id.duplicate -> "duplicate"
                            R.id.history -> "history"
                            else -> "edit"
                        })
                        true
                    }
                    show()
                }
            }
        }
    }
}

class RecurringAdapter(private val action: (RecurringUiModel, String) -> Unit) :
    ListAdapter<RecurringUiModel, RecurringAdapter.Holder>(object : DiffUtil.ItemCallback<RecurringUiModel>() {
        override fun areItemsTheSame(a: RecurringUiModel, b: RecurringUiModel) = a.entity.id == b.entity.id
        override fun areContentsTheSame(a: RecurringUiModel, b: RecurringUiModel) = a == b
    }) {
    private var hideAmounts = false

    fun setHideAmounts(value: Boolean) {
        if (hideAmounts == value) return
        hideAmounts = value
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemRecurringBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    inner class Holder(private val b: ItemRecurringBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: RecurringUiModel) {
            val rule = item.entity
            b.name.text = rule.name
            b.meta.text = buildString {
                append("${RecurrenceFrequency.from(rule.frequency).label} • Next ${rule.nextDueDate}")
                append("\n${item.accountName}")
                item.destinationAccountName?.let { append(" → $it") }
                item.categoryName?.let { append(" • $it") }
                append(" • ${RecurringPostingMode.from(rule.postingMode).label}")
                rule.occurrencesRemaining?.let { append(" • $it left") }
                if (!rule.isActive) append(" • Paused")
            }
            b.icon.text = when {
                !rule.isActive -> "Ⅱ"
                rule.nextDueDate <= java.time.LocalDate.now().toString() -> "!"
                else -> "↻"
            }
            b.amount.text = if (hideAmounts) "••••••" else Utils.money(rule.amountMinor, rule.currencyCode)
            b.root.alpha = if (rule.isActive) 1f else 0.68f
            b.root.setOnClickListener { action(item, "edit") }
            b.more.setOnClickListener { anchor ->
                PopupMenu(anchor.context, anchor).apply {
                    menuInflater.inflate(R.menu.recurring_actions, menu)
                    menu.findItem(R.id.pauseRecurring).isVisible = rule.isActive
                    menu.findItem(R.id.resumeRecurring).isVisible = !rule.isActive
                    menu.findItem(R.id.skipRecurring).isVisible = rule.isActive
                    setOnMenuItemClickListener {
                        action(item, when (it.itemId) {
                            R.id.deleteRecurring -> "delete"
                            R.id.runRecurringNow -> "run"
                            R.id.skipRecurring -> "skip"
                            R.id.pauseRecurring -> "pause"
                            R.id.resumeRecurring -> "resume"
                            else -> "edit"
                        })
                        true
                    }
                    show()
                }
            }
        }
    }
}
