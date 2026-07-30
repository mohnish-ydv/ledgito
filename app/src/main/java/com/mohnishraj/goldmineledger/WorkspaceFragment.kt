package com.mohnishraj.goldmineledger

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mohnishraj.goldmineledger.databinding.FragmentWorkspaceBinding
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class WorkspaceFragment : Fragment() {
    private var _b: FragmentWorkspaceBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private val type by lazy { WorkspaceType.from(requireArguments().getString(ARG_TYPE).orEmpty()) }
    private val config by lazy { WorkspaceCatalog.forType(type) }
    private var status = WorkspaceStatus.ACTIVE
    private var hideAmounts = false
    private val adapter by lazy {
        WorkspaceAdapter(
            config = config,
            edit = { WorkspaceItemDialog.newInstance(type, it.entity.id).show(parentFragmentManager, "workspace-edit") },
            primary = ::runPrimaryAction,
            history = ::showHistory,
            options = ::showOptions
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentWorkspaceBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.toolbar.title = config.title
        b.toolbar.subtitle = config.group
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.heroIcon.text = config.icon
        b.heroTitle.text = config.title
        b.heroSubtitle.text = config.subtitle
        b.emptyIcon.text = config.icon
        b.emptyText.text = "No ${config.title.lowercase()} here yet"
        b.list.adapter = adapter
        b.add.text = "Add ${singular(config.title)}"
        b.add.setOnClickListener { button ->
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            WorkspaceItemDialog.newInstance(type).show(parentFragmentManager, "workspace-add")
        }
        b.statusChips.setOnCheckedStateChangeListener { _, checked ->
            status = when (checked.firstOrNull()) {
                R.id.chipCompleted -> WorkspaceStatus.COMPLETED
                R.id.chipArchived -> WorkspaceStatus.ARCHIVED
                else -> WorkspaceStatus.ACTIVE
            }
            render(vm.workspaceItems.value)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.workspaceItems.collect(::render) }
                launch { vm.settingsState.collect { hideAmounts = it.hideAmounts; render(vm.workspaceItems.value) } }
            }
        }
    }

    private fun render(all: List<WorkspaceItemUiModel>) {
        if (_b == null) return
        val ofType = all.filter { it.entity.type == type.name }
        val visible = ofType.filter {
            when (status) {
                WorkspaceStatus.ACTIVE, WorkspaceStatus.PAUSED -> it.entity.status in setOf(WorkspaceStatus.ACTIVE.name, WorkspaceStatus.PAUSED.name)
                WorkspaceStatus.COMPLETED -> it.entity.status == WorkspaceStatus.COMPLETED.name
                WorkspaceStatus.ARCHIVED -> it.entity.status == WorkspaceStatus.ARCHIVED.name
            }
        }
        adapter.hideAmounts = hideAmounts
        adapter.submitList(visible)
        b.resultCount.text = "${visible.size} item${if (visible.size == 1) "" else "s"}"
        b.sectionTitle.text = when (status) {
            WorkspaceStatus.ACTIVE, WorkspaceStatus.PAUSED -> "Active items"
            WorkspaceStatus.COMPLETED -> "Completed items"
            WorkspaceStatus.ARCHIVED -> "Archived items"
        }
        b.emptyCard.isVisible = visible.isEmpty()
        b.list.isVisible = visible.isNotEmpty()
        renderHero(ofType)
    }

    private fun renderHero(items: List<WorkspaceItemUiModel>) {
        val active = items.filter { it.entity.status != WorkspaceStatus.ARCHIVED.name }
        val currency = active.firstOrNull()?.entity?.currencyCode ?: vm.profile.value?.baseCurrency ?: "INR"
        val balanceTypes = setOf(WorkspaceType.DEBT, WorkspaceType.LOAN, WorkspaceType.LIABILITY, WorkspaceType.EMI)
        val wealthTypes = setOf(
            WorkspaceType.INVESTMENT, WorkspaceType.MUTUAL_FUND, WorkspaceType.GOLD,
            WorkspaceType.FIXED_DEPOSIT, WorkspaceType.PPF, WorkspaceType.EPF,
            WorkspaceType.CRYPTO, WorkspaceType.ASSET, WorkspaceType.CREDIT
        )
        val progressTypes = setOf(
            WorkspaceType.GOAL, WorkspaceType.BILL, WorkspaceType.SHOPPING_LIST,
            WorkspaceType.SHARED_EXPENSE
        )
        val amountText = when {
            type == WorkspaceType.CURRENCY_RATE -> "${active.size} saved rate${if (active.size == 1) "" else "s"}"
            type == WorkspaceType.LOYALTY -> "${active.sumOf { it.entity.currentMinor }} points"
            type in balanceTypes -> money(active.sumOf { it.entity.currentMinor }, currency)
            type in wealthTypes -> money(active.sumOf { it.entity.currentMinor }, currency)
            type in progressTypes -> money(active.sumOf { it.entity.currentMinor }, currency)
            else -> money(active.filter { it.entity.status == WorkspaceStatus.ACTIVE.name }.sumOf { it.entity.amountMinor }, currency)
        }
        b.heroAmount.text = amountText
        b.heroMeta.text = when {
            type in balanceTypes -> "remaining across ${active.size} tracked balance${if (active.size == 1) "" else "s"}"
            type in wealthTypes -> "current recorded value across active items"
            type == WorkspaceType.GOAL -> "saved across ${active.size} goal${if (active.size == 1) "" else "s"}"
            type == WorkspaceType.BILL -> "paid across recorded bills"
            type == WorkspaceType.SHOPPING_LIST -> "checked items across your active lists"
            type == WorkspaceType.SHARED_EXPENSE -> "settled so far"
            type == WorkspaceType.SUBSCRIPTION -> "scheduled renewal value"
            else -> "${active.count { it.entity.status == WorkspaceStatus.ACTIVE.name }} active • ${items.count { it.entity.status == WorkspaceStatus.COMPLETED.name }} complete"
        }
    }

    private fun money(value: Long, currency: String) = if (hideAmounts) "••••" else Utils.money(value, currency)

    private fun runPrimaryAction(item: WorkspaceItemUiModel) {
        when {
            type == WorkspaceType.SHOPPING_LIST -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.entity.title)
                .setItems(arrayOf("Add list item", "Post checked items as expense")) { _, which ->
                    if (which == 0) {
                        WorkspaceEventDialog.newInstance(item.entity.id).show(parentFragmentManager, "workspace-event")
                    } else {
                        confirmLedgerPost(item)
                    }
                }
                .show()
            config.eventKind != null -> WorkspaceEventDialog.newInstance(item.entity.id).show(parentFragmentManager, "workspace-event")
            config.supportsLedgerPost -> confirmLedgerPost(item)
        }
    }

    private fun confirmLedgerPost(item: WorkspaceItemUiModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Post to ledger?")
            .setMessage("Ledgito will create one cleared expense using the account and category selected on this item.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Post") { _, _ ->
                vm.postWorkspaceItem(item.entity.id) { result ->
                    Snackbar.make(b.root, result.fold({ "Expense posted to Activity" }, { it.message ?: "Could not post" }), Snackbar.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun showHistory(item: WorkspaceItemUiModel) {
        if (item.events.isEmpty()) {
            Snackbar.make(b.root, "No activity recorded yet", Snackbar.LENGTH_SHORT).show()
            return
        }
        val rows = item.events.map { event ->
            val amount = if (config.usesPoints) "${event.amountMinor} pts" else money(event.amountMinor, item.entity.currencyCode)
            val mark = if (event.kind == "ITEM") if (event.isCompleted) "✓" else "○" else "•"
            "$mark ${event.label}  $amount\n${event.eventDate}${event.note.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Activity history")
            .setItems(rows) { _, which -> showEventOptions(item.events[which]) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showEventOptions(event: WorkspaceEventEntity) {
        val actions = buildList {
            if (event.kind == "ITEM") add(if (event.isCompleted) "Mark unchecked" else "Mark checked")
            add("Delete entry")
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(event.label)
            .setItems(actions) { _, which ->
                val toggleAvailable = event.kind == "ITEM"
                if (toggleAvailable && which == 0) {
                    vm.setWorkspaceEventCompleted(event, !event.isCompleted) { showResult(it, "Item updated") }
                } else {
                    vm.deleteWorkspaceEvent(event) { showResult(it, "Entry deleted") }
                }
            }
            .show()
    }

    private fun showOptions(item: WorkspaceItemUiModel) {
        val isArchived = item.entity.status == WorkspaceStatus.ARCHIVED.name
        val actions = arrayOf("Edit", if (isArchived) "Restore" else "Archive", "Delete permanently")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.entity.title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> WorkspaceItemDialog.newInstance(type, item.entity.id).show(parentFragmentManager, "workspace-edit")
                    1 -> vm.setWorkspaceStatus(item.entity, if (isArchived) WorkspaceStatus.ACTIVE else WorkspaceStatus.ARCHIVED) { showResult(it, if (isArchived) "Item restored" else "Item archived") }
                    2 -> MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Delete permanently?")
                        .setMessage("This also deletes its local activity history. Ledger transactions already posted are not deleted.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ -> vm.deleteWorkspaceItem(item.entity) { showResult(it, "Item deleted") } }
                        .show()
                }
            }.show()
    }

    private fun showResult(result: Result<Unit>, success: String) {
        Snackbar.make(b.root, result.fold({ success }, { it.message ?: "Action failed" }), Snackbar.LENGTH_LONG).show()
    }

    private fun singular(title: String): String = when (type) {
        WorkspaceType.PLANNED_PAYMENT -> "payment"
        WorkspaceType.BILL -> "bill"
        WorkspaceType.EMI -> "EMI"
        WorkspaceType.GOAL -> "goal"
        WorkspaceType.DEBT -> "debt"
        WorkspaceType.LOAN -> "loan"
        WorkspaceType.SUBSCRIPTION -> "subscription"
        WorkspaceType.INVESTMENT -> "holding"
        WorkspaceType.MUTUAL_FUND -> "fund"
        WorkspaceType.GOLD -> "gold holding"
        WorkspaceType.FIXED_DEPOSIT -> "deposit"
        WorkspaceType.PPF -> "PPF account"
        WorkspaceType.EPF -> "EPF account"
        WorkspaceType.CRYPTO -> "holding"
        WorkspaceType.ASSET -> "asset"
        WorkspaceType.LIABILITY -> "liability"
        WorkspaceType.CREDIT -> "card"
        WorkspaceType.SHOPPING_LIST -> "list"
        WorkspaceType.WARRANTY -> "warranty"
        WorkspaceType.LOYALTY -> "card"
        WorkspaceType.SHARED_EXPENSE -> "balance"
        WorkspaceType.CURRENCY_RATE -> "rate"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object { const val ARG_TYPE = "workspace_type" }
}

private class WorkspaceAdapter(
    private val config: WorkspaceConfig,
    private val edit: (WorkspaceItemUiModel) -> Unit,
    private val primary: (WorkspaceItemUiModel) -> Unit,
    private val history: (WorkspaceItemUiModel) -> Unit,
    private val options: (WorkspaceItemUiModel) -> Unit
) : androidx.recyclerview.widget.ListAdapter<WorkspaceItemUiModel, WorkspaceAdapter.Holder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<WorkspaceItemUiModel>() {
        override fun areItemsTheSame(oldItem: WorkspaceItemUiModel, newItem: WorkspaceItemUiModel) = oldItem.entity.id == newItem.entity.id
        override fun areContentsTheSame(oldItem: WorkspaceItemUiModel, newItem: WorkspaceItemUiModel) = oldItem == newItem
    }
) {
    var hideAmounts: Boolean = false
        set(value) { field = value; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        com.mohnishraj.goldmineledger.databinding.ItemWorkspaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val b: com.mohnishraj.goldmineledger.databinding.ItemWorkspaceBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(b.root) {
        fun bind(model: WorkspaceItemUiModel) {
            val item = model.entity
            val type = WorkspaceType.from(item.type)
            b.icon.text = config.icon
            b.title.text = item.title
            b.status.text = WorkspaceStatus.from(item.status).label.uppercase()
            val details = listOfNotNull(
                if (type == WorkspaceType.SUBSCRIPTION) "${BillingCadence.from(item.secondaryCode).label} billing" else null,
                item.metadata.takeIf { it.isNotBlank() },
                model.accountName?.let { account -> model.categoryName?.let { "$account • $it" } ?: account },
                model.events.takeIf { it.isNotEmpty() }?.let { "${it.size} activity entr${if (it.size == 1) "y" else "ies"}" }
            )
            b.subtitle.text = details.joinToString(" • ").ifBlank { config.subtitle }
            b.date.text = item.dueDate?.let {
                if (type == WorkspaceType.SUBSCRIPTION) "Renews $it" else "Due $it"
            } ?: item.startDate.orEmpty()
            b.amount.text = displayAmount(item, type)
            val progress = progress(item, type)
            b.progress.isVisible = progress != null
            b.progress.progress = progress ?: 0
            val canAct = item.status in setOf(WorkspaceStatus.ACTIVE.name, WorkspaceStatus.PAUSED.name)
            b.primaryAction.isVisible = canAct && (config.eventAction != null || config.supportsLedgerPost)
            b.primaryAction.text = config.eventAction ?: when (type) {
                WorkspaceType.SUBSCRIPTION -> "Post renewal"
                WorkspaceType.PLANNED_PAYMENT -> "Post expense"
                WorkspaceType.SHOPPING_LIST -> "Update list"
                else -> "Post expense"
            }
            b.secondaryAction.isVisible = model.events.isNotEmpty()
            b.secondaryAction.text = "History"
            b.primaryAction.setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                primary(model)
            }
            b.secondaryAction.setOnClickListener { history(model) }
            b.root.contentDescription = buildString {
                append(item.title)
                append(", ")
                append(WorkspaceStatus.from(item.status).label)
                append(", ")
                append(if (hideAmounts && type != WorkspaceType.LOYALTY) "amount hidden" else displayAmount(item, type))
                item.dueDate?.let { append(", due "); append(it) }
            }
            b.root.setOnClickListener { edit(model) }
            b.root.setOnLongClickListener { options(model); true }
        }

        private fun displayAmount(item: WorkspaceItemEntity, type: WorkspaceType): String {
            if (hideAmounts && type != WorkspaceType.LOYALTY) return "••••"
            return when (type) {
                WorkspaceType.CURRENCY_RATE -> "1 ${item.currencyCode} = ${java.math.BigDecimal.valueOf(item.currentMinor, 6).stripTrailingZeros().toPlainString()} ${item.secondaryCode}"
                WorkspaceType.LOYALTY -> "${item.currentMinor} / ${item.amountMinor} points"
                WorkspaceType.DEBT, WorkspaceType.LOAN, WorkspaceType.LIABILITY, WorkspaceType.EMI -> "${Utils.money(item.currentMinor, item.currencyCode)} remaining"
                WorkspaceType.CREDIT -> "${Utils.money(item.currentMinor, item.currencyCode)} of ${Utils.money(item.amountMinor, item.currencyCode)} used"
                WorkspaceType.GOAL, WorkspaceType.BILL, WorkspaceType.SHOPPING_LIST, WorkspaceType.SHARED_EXPENSE -> "${Utils.money(item.currentMinor, item.currencyCode)} of ${Utils.money(item.amountMinor, item.currencyCode)}"
                WorkspaceType.INVESTMENT, WorkspaceType.MUTUAL_FUND, WorkspaceType.GOLD,
                WorkspaceType.FIXED_DEPOSIT, WorkspaceType.PPF, WorkspaceType.EPF,
                WorkspaceType.CRYPTO, WorkspaceType.ASSET -> "${Utils.money(item.currentMinor, item.currencyCode)} current"
                else -> Utils.money(item.amountMinor, item.currencyCode)
            }
        }

        private fun progress(item: WorkspaceItemEntity, type: WorkspaceType): Int? {
            if (item.amountMinor <= 0) return null
            val value = when (type) {
                WorkspaceType.DEBT, WorkspaceType.LOAN, WorkspaceType.LIABILITY, WorkspaceType.EMI -> item.amountMinor - item.currentMinor
                WorkspaceType.GOAL, WorkspaceType.BILL, WorkspaceType.SHOPPING_LIST,
                WorkspaceType.SHARED_EXPENSE, WorkspaceType.CREDIT, WorkspaceType.LOYALTY -> item.currentMinor
                else -> return null
            }
            return ((value.toDouble() / item.amountMinor.toDouble()) * 100).roundToInt().coerceIn(0, 100)
        }
    }
}
