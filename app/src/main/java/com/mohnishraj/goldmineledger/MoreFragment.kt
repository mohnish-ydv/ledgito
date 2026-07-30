package com.mohnishraj.goldmineledger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mohnishraj.goldmineledger.databinding.FragmentMoreBinding
import com.mohnishraj.goldmineledger.databinding.ItemMoreFeatureBinding

private data class CommandTool(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val group: String,
    val workspaceType: WorkspaceType? = null,
    val destinationId: Int? = null
)

class MoreFragment : Fragment() {
    private var _b: FragmentMoreBinding? = null
    private val b get() = _b!!
    private val adapter = CommandToolAdapter(::openTool)
    private var query = ""
    private var group = "All"

    private val tools: List<CommandTool> by lazy {
        val workspaceTools = WorkspaceCatalog.all.map { config ->
            CommandTool(
                id = config.type.name,
                icon = config.icon,
                title = config.title,
                subtitle = config.subtitle,
                group = config.group,
                workspaceType = config.type
            )
        }
        listOf(
            CommandTool("planning-centre", "◎", "Planning centre", "Bills, EMIs, loans, goals, debt and subscriptions in one calm agenda.", "Planning", destinationId = R.id.planningHubFragment),
            CommandTool("wealth-centre", "◈", "Wealth centre", "Investments, deposits, gold, assets and liabilities with a live net-worth view.", "Wealth", destinationId = R.id.wealthHubFragment),
            CommandTool("net-worth", "◇", "Net worth", "Accounts, investments, assets and liabilities combined transparently.", "Wealth", destinationId = R.id.netWorthFragment),
            CommandTool("forecast", "≈", "Cash-flow outlook", "A 90-day projection using recurring rules and planned commitments.", "Planning", destinationId = R.id.forecastFragment)
        ) + workspaceTools + listOf(
            CommandTool("vault", "▣", "Data vault", "Encrypted backups, verified restore, CSV import and portable exports.", "Utilities", destinationId = R.id.dataToolsFragment)
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentMoreBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.featureList.adapter = adapter
        b.featureList.isNestedScrollingEnabled = false
        b.accountsCard.setOnClickListener { go(R.id.accountsFragment) }
        b.categoriesCard.setOnClickListener { go(R.id.categoriesFragment) }
        b.budgetsCard.setOnClickListener { go(R.id.budgetsFragment) }
        b.recurringCard.setOnClickListener { go(R.id.recurringFragment) }
        b.calendarCard.setOnClickListener { go(R.id.calendarFragment) }
        b.settingsCard.setOnClickListener { go(R.id.settingsFragment) }
        b.searchInput.doAfterTextChanged { query = it?.toString().orEmpty(); filter() }
        b.filterChips.setOnCheckedStateChangeListener { _, checked ->
            group = when (checked.firstOrNull()) {
                R.id.chipPlan -> "Planning"
                R.id.chipWealth -> "Wealth"
                R.id.chipLife -> "Lifestyle"
                R.id.chipConnect -> "Utilities"
                else -> "All"
            }
            filter()
        }
        filter()
    }

    private fun filter() {
        val clean = query.trim()
        val result = tools.filter { tool ->
            (group == "All" || tool.group == group) &&
                (clean.isBlank() || listOf(tool.title, tool.subtitle, tool.group).any { it.contains(clean, true) })
        }
        adapter.submitList(result)
        b.resultCount.text = "${result.size} live tool${if (result.size == 1) "" else "s"}"
        b.empty.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
        b.featureList.visibility = if (result.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun go(id: Int) = findNavController().navigate(id)

    private fun openTool(tool: CommandTool) {
        tool.workspaceType?.let { type ->
            findNavController().navigate(
                R.id.workspaceFragment,
                Bundle().apply { putString("workspace_type", type.name) }
            )
            return
        }
        tool.destinationId?.let(::go)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

private class CommandToolAdapter(private val click: (CommandTool) -> Unit) :
    ListAdapter<CommandTool, CommandToolAdapter.Holder>(object : DiffUtil.ItemCallback<CommandTool>() {
        override fun areItemsTheSame(oldItem: CommandTool, newItem: CommandTool) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CommandTool, newItem: CommandTool) = oldItem == newItem
    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemMoreFeatureBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemMoreFeatureBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CommandTool) {
            binding.icon.text = item.icon
            binding.title.text = item.title
            binding.subtitle.text = "${item.group} • ${item.subtitle}"
            binding.root.setOnClickListener { click(item) }
        }
    }
}
