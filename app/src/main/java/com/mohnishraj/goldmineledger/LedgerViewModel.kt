package com.mohnishraj.goldmineledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt


enum class AccountFilter { ACTIVE, ALL, ARCHIVED }

data class TransactionFilter(
    val query: String = "",
    val type: TransactionType? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val tagId: String? = null,
    val currencyCode: String? = null,
    val minMinor: Long? = null,
    val maxMinor: Long? = null,
    val hasAttachment: Boolean? = null,
    val cleared: Boolean? = null,
    val recurringOnly: Boolean? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
    val sort: TransactionSort = TransactionSort.DATE_NEWEST
) {
    fun advancedCount(): Int = listOfNotNull(
        accountId, categoryId, tagId, currencyCode, minMinor, maxMinor,
        hasAttachment, cleared, recurringOnly, fromDate, toDate
    ).size + if (sort != TransactionSort.DATE_NEWEST) 1 else 0
}

data class DashboardState(
    val profile: ProfileEntity? = null,
    val currentTotal: Long = 0,
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
    val accountCount: Int = 0,
    val categoryCount: Int = 0,
    val transactionCount: Int = 0,
    val dueRecurringCount: Int = 0,
    val activeBudgetCount: Int = 0,
    val overBudgetCount: Int = 0,
    val audit: List<AuditEventEntity> = emptyList()
)

data class SettingsState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val currency: String = "INR",
    val hideAmounts: Boolean = false,
    val appLock: Boolean = false,
    val reminders: Boolean = false,
    val lastBackupAt: Long = 0L,
    val dashboardSections: DashboardSections = DashboardSections()
)

private data class TransactionLookups(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val refs: List<TransactionTagCrossRef> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val splits: List<TransactionSplitEntity> = emptyList(),
    val revisions: List<TransactionRevisionEntity> = emptyList()
)

private data class BudgetInputs(
    val budgets: List<BudgetEntity>,
    val periods: List<BudgetPeriodEntity>,
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val splits: List<TransactionSplitEntity>
)

private data class ReportConfig(
    val preset: ReportPreset = ReportPreset.MONTH,
    val referenceDate: String = LocalDate.now().toString(),
    val customFrom: String = LocalDate.now().withDayOfMonth(1).toString(),
    val customTo: String = LocalDate.now().toString(),
    val currencyCode: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(
    private val repo: LedgerRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    private val accountFilter = MutableStateFlow(AccountFilter.ACTIVE)
    private val transactionFilter = MutableStateFlow(TransactionFilter())
    private val budgetReferenceDate = MutableStateFlow(LocalDate.now().toString())
    private val reportConfig = MutableStateFlow(ReportConfig())

    val profile: StateFlow<ProfileEntity?> = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val allAccounts: StateFlow<List<AccountEntity>> = repo.observeProfile().flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else repo.observeAccounts(p.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repo.observeProfile().flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else repo.observeCategories(p.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rawTransactions: StateFlow<List<TransactionEntity>> = repo.observeProfile().flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else repo.observeTransactions(p.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<TagEntity>> = repo.observeProfile().flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else repo.observeTags(p.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rawTagRefs: StateFlow<List<TransactionTagCrossRef>> = repo.observeTagRefs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rawAttachments: StateFlow<List<AttachmentEntity>> = repo.observeAttachments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rawSplits: StateFlow<List<TransactionSplitEntity>> = repo.observeSplits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rawRevisions: StateFlow<List<TransactionRevisionEntity>> = repo.observeRevisions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rawRecurring: StateFlow<List<RecurringRuleEntity>> = repo.observeProfile().flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else repo.observeRecurring(p.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rawBudgets: StateFlow<List<BudgetEntity>> = repo.observeProfile().flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else repo.observeBudgets(p.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rawBudgetPeriods: StateFlow<List<BudgetPeriodEntity>> = repo.observeBudgetPeriods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rawAudit: StateFlow<List<AuditEventEntity>> = repo.observeProfile().flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else repo.observeAudit(p.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rawWorkspaceItems: StateFlow<List<WorkspaceItemEntity>> = repo.observeProfile().flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else repo.observeWorkspaceItems(p.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val rawWorkspaceEvents: StateFlow<List<WorkspaceEventEntity>> = repo.observeWorkspaceEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedFilters: StateFlow<List<SavedFilterEntity>> = repo.observeProfile().flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else repo.observeSavedFilters(p.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val lookups: StateFlow<TransactionLookups> = combine(
        combine(allAccounts, categories) { accounts, categoryList -> accounts to categoryList },
        combine(tags, rawTagRefs) { tagList, refs -> tagList to refs },
        combine(rawAttachments, rawSplits) { attachments, splits -> attachments to splits },
        rawRevisions
    ) { accountCategory, tagRef, attachmentSplit, revisions ->
        TransactionLookups(
            accountCategory.first, accountCategory.second, tagRef.first, tagRef.second,
            attachmentSplit.first, attachmentSplit.second, revisions
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionLookups())

    val transactionFilterState: StateFlow<TransactionFilter> = transactionFilter
    val budgetReference: StateFlow<String> = budgetReferenceDate

    val calendarTransactions: StateFlow<List<TransactionUiModel>> = combine(rawTransactions, lookups) { list, lookup ->
        val accountMap = lookup.accounts.associateBy { it.id }
        val categoryMap = lookup.categories.associateBy { it.id }
        val tagMap = lookup.tags.associateBy { it.id }
        val tagIdsByTransaction = lookup.refs.groupBy { it.transactionId }
        val attachmentsByTransaction = lookup.attachments.groupBy { it.transactionId }
        val splitsByTransaction = lookup.splits.groupBy { it.transactionId }
        val revisionsByTransaction = lookup.revisions.groupBy { it.transactionId }
        list.map { tx ->
            val splitRows = splitsByTransaction[tx.id].orEmpty()
            TransactionUiModel(
                entity = tx,
                accountName = accountMap[tx.accountId]?.name ?: "Deleted account",
                destinationAccountName = tx.destinationAccountId?.let { accountMap[it]?.name },
                categoryName = if (splitRows.isNotEmpty()) "Split across ${splitRows.size} categories" else tx.categoryId?.let { categoryMap[it]?.name },
                tags = tagIdsByTransaction[tx.id].orEmpty().mapNotNull { tagMap[it.tagId]?.name },
                attachments = attachmentsByTransaction[tx.id].orEmpty(),
                splits = splitRows,
                revisions = revisionsByTransaction[tx.id].orEmpty()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transactions: StateFlow<List<TransactionUiModel>> = combine(
        rawTransactions, lookups, transactionFilter
    ) { list, lookup, filter ->
        val accountMap = lookup.accounts.associateBy { it.id }
        val categoryMap = lookup.categories.associateBy { it.id }
        val tagMap = lookup.tags.associateBy { it.id }
        val tagIdsByTransaction = lookup.refs.groupBy { it.transactionId }
        val attachmentsByTransaction = lookup.attachments.groupBy { it.transactionId }
        val splitsByTransaction = lookup.splits.groupBy { it.transactionId }
        val revisionsByTransaction = lookup.revisions.groupBy { it.transactionId }
        val runningById = filter.accountId?.let { accountId ->
            val account = accountMap[accountId]
            var balance = account?.openingBalanceMinor ?: 0L
            buildMap {
                list.sortedWith(compareBy<TransactionEntity> { it.transactionDate }.thenBy { it.createdAt }).forEach { tx ->
                    balance += LedgerMath.accountImpact(tx, accountId)
                    put(tx.id, balance)
                }
            }
        }.orEmpty()
        val mapped = list.map { tx ->
            val splitRows = splitsByTransaction[tx.id].orEmpty()
            TransactionUiModel(
                entity = tx,
                accountName = accountMap[tx.accountId]?.name ?: "Deleted account",
                destinationAccountName = tx.destinationAccountId?.let { accountMap[it]?.name },
                categoryName = if (splitRows.isNotEmpty()) "Split across ${splitRows.size} categories" else tx.categoryId?.let { categoryMap[it]?.name },
                tags = tagIdsByTransaction[tx.id].orEmpty().mapNotNull { tagMap[it.tagId]?.name },
                attachments = attachmentsByTransaction[tx.id].orEmpty(),
                splits = splitRows,
                revisions = revisionsByTransaction[tx.id].orEmpty(),
                runningBalanceMinor = runningById[tx.id],
                runningBalanceCurrency = filter.accountId?.let { accountMap[it]?.currencyCode }
            )
        }.filter { item -> matchesFilter(item, filter, tagIdsByTransaction[item.entity.id].orEmpty()) }
        when (filter.sort) {
            TransactionSort.DATE_NEWEST -> mapped.sortedWith(compareByDescending<TransactionUiModel> { it.entity.transactionDate }.thenByDescending { it.entity.createdAt })
            TransactionSort.DATE_OLDEST -> mapped.sortedWith(compareBy<TransactionUiModel> { it.entity.transactionDate }.thenBy { it.entity.createdAt })
            TransactionSort.AMOUNT_HIGH -> mapped.sortedByDescending { it.entity.amountMinor }
            TransactionSort.AMOUNT_LOW -> mapped.sortedBy { it.entity.amountMinor }
            TransactionSort.PAYEE_AZ -> mapped.sortedBy { it.entity.payee.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accountModels: StateFlow<List<AccountUiModel>> = combine(
        allAccounts, rawTransactions, accountFilter
    ) { accounts, transactionList, filter ->
        val balances = LedgerMath.balances(accounts, transactionList)
        accounts.filter {
            when (filter) {
                AccountFilter.ACTIVE -> !it.isArchived
                AccountFilter.ARCHIVED -> it.isArchived
                AccountFilter.ALL -> true
            }
        }.map { AccountUiModel(it, balances[it.id] ?: it.openingBalanceMinor) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val workspaceItems: StateFlow<List<WorkspaceItemUiModel>> = combine(
        rawWorkspaceItems, rawWorkspaceEvents, allAccounts, categories
    ) { items, events, accounts, categoryList ->
        val accountNames = accounts.associate { it.id to it.name }
        val categoryNames = categoryList.associate { it.id to it.name }
        val groupedEvents = events.groupBy { it.itemId }
        items.map { item ->
            WorkspaceItemUiModel(
                entity = item,
                accountName = item.accountId?.let(accountNames::get),
                categoryName = item.categoryId?.let(categoryNames::get),
                events = groupedEvents[item.id].orEmpty()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val netWorth: StateFlow<NetWorthState> = combine(
        profile, allAccounts, rawTransactions, rawWorkspaceItems
    ) { p, accounts, transactions, items ->
        val currency = p?.baseCurrency ?: "INR"
        val balances = LedgerMath.balances(accounts, transactions)
        val accountValue = accounts.filter { !it.isArchived && it.includeInTotal && it.currencyCode == currency }
            .sumOf { balances[it.id] ?: it.openingBalanceMinor }
        val investmentTypes = setOf(
            WorkspaceType.INVESTMENT.name, WorkspaceType.MUTUAL_FUND.name, WorkspaceType.GOLD.name,
            WorkspaceType.FIXED_DEPOSIT.name, WorkspaceType.PPF.name, WorkspaceType.EPF.name,
            WorkspaceType.CRYPTO.name
        )
        val debtTypes = setOf(
            WorkspaceType.DEBT.name, WorkspaceType.LOAN.name, WorkspaceType.LIABILITY.name,
            WorkspaceType.EMI.name, WorkspaceType.CREDIT.name
        )
        val investments = items.filter { it.type in investmentTypes && it.status != WorkspaceStatus.ARCHIVED.name && it.currencyCode == currency }.sumOf { it.currentMinor }
        val assets = items.filter { it.type == WorkspaceType.ASSET.name && it.status != WorkspaceStatus.ARCHIVED.name && it.currencyCode == currency }.sumOf { it.currentMinor }
        val debts = items.filter { it.type in debtTypes && it.status != WorkspaceStatus.ARCHIVED.name && it.currencyCode == currency }.sumOf { it.currentMinor }
        NetWorthState(
            currencyCode = currency,
            accountValueMinor = accountValue,
            investmentValueMinor = investments,
            assetValueMinor = assets,
            debtMinor = debts,
            netWorthMinor = accountValue + investments + assets - debts,
            rows = listOf(
                ReportRow("Accounts", accountValue), ReportRow("Investments", investments),
                ReportRow("Assets", assets), ReportRow("Debts & credit", -debts)
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetWorthState())

    val forecast: StateFlow<ForecastState> = combine(
        profile, allAccounts, rawTransactions, rawRecurring, rawWorkspaceItems
    ) { p, accounts, transactions, recurringRules, items ->
        buildForecast(p, accounts, transactions, recurringRules, items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ForecastState())

    val recurring: StateFlow<List<RecurringUiModel>> = combine(
        rawRecurring, allAccounts, categories
    ) { rules, accounts, categoryList ->
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categoryList.associateBy { it.id }
        rules.map { rule ->
            RecurringUiModel(
                rule,
                accountMap[rule.accountId]?.name ?: "Deleted account",
                rule.destinationAccountId?.let { accountMap[it]?.name },
                rule.categoryId?.let { categoryMap[it]?.name }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val budgetInputs: StateFlow<BudgetInputs> = combine(
        combine(rawBudgets, rawBudgetPeriods) { budgets, periods -> budgets to periods },
        combine(rawTransactions, categories, rawSplits) { transactionList, categoryList, splits -> Triple(transactionList, categoryList, splits) }
    ) { budgetPair, transactionTriple ->
        BudgetInputs(budgetPair.first, budgetPair.second, transactionTriple.first, transactionTriple.second, transactionTriple.third)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BudgetInputs(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    )

    val budgets: StateFlow<List<BudgetUiModel>> = combine(
        budgetInputs, budgetReferenceDate
    ) { input, reference -> buildBudgetModels(input, reference) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val report: StateFlow<ReportState> = combine(
        combine(rawTransactions, allAccounts) { transactionList, accountList -> transactionList to accountList },
        combine(categories, profile) { categoryList, p -> categoryList to p },
        combine(reportConfig, rawSplits, budgets) { config, splits, budgetModels -> Triple(config, splits, budgetModels) }
    ) { transactionAccounts, categoryProfile, reportInputs ->
        buildReport(
            transactionAccounts.first,
            transactionAccounts.second,
            categoryProfile.first,
            categoryProfile.second,
            reportInputs.first,
            reportInputs.second,
            reportInputs.third
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportState())

    val dashboard: StateFlow<DashboardState> = combine(
        combine(profile, allAccounts) { p, accounts -> p to accounts },
        combine(categories, rawTransactions) { categoryList, transactionList -> categoryList to transactionList },
        combine(rawRecurring, rawAudit) { recurringList, audit -> recurringList to audit },
        budgets
    ) { profileAccounts, categoryTransactions, recurringAudit, budgetModels ->
        val p = profileAccounts.first ?: return@combine DashboardState()
        val accounts = profileAccounts.second
        val categoryList = categoryTransactions.first
        val transactionList = categoryTransactions.second
        val recurringList = recurringAudit.first
        val audit = recurringAudit.second
        val balances = LedgerMath.balances(accounts, transactionList)
        val currentTotal = accounts.filter {
            !it.isArchived && it.includeInTotal && it.currencyCode == p.baseCurrency
        }.sumOf { balances[it.id] ?: it.openingBalanceMinor }
        val month = YearMonth.now().toString()
        val monthTransactions = transactionList.filter { it.isCleared && it.transactionDate.startsWith(month) }
        val income = monthTransactions.filter {
            it.type == TransactionType.INCOME.name && it.currencyCode == p.baseCurrency
        }.sumOf { it.amountMinor }
        val expense = monthTransactions.filter {
            it.type == TransactionType.EXPENSE.name && it.currencyCode == p.baseCurrency
        }.sumOf { it.amountMinor } + monthTransactions.filter {
            it.type == TransactionType.TRANSFER.name && it.currencyCode == p.baseCurrency
        }.sumOf { it.transferFeeMinor }
        val today = LocalDate.now().toString()
        DashboardState(
            profile = p,
            currentTotal = currentTotal,
            monthIncome = income,
            monthExpense = expense,
            accountCount = accounts.count { !it.isArchived },
            categoryCount = categoryList.count { !it.isArchived },
            transactionCount = transactionList.size,
            dueRecurringCount = recurringList.count { it.isActive && it.nextDueDate <= today },
            activeBudgetCount = budgetModels.count { it.entity.isActive },
            overBudgetCount = budgetModels.count { it.entity.isActive && it.period != null && it.remainingMinor < 0 },
            audit = audit
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    val settingsState: StateFlow<SettingsState> = combine(
        combine(settings.theme, repo.observeProfile()) { theme, p -> theme to p },
        combine(settings.hideAmounts, settings.appLock) { hide, lock -> hide to lock },
        combine(settings.reminders, settings.lastBackupAt) { reminders, backupAt -> reminders to backupAt },
        settings.dashboardSections
    ) { themeProfile, hideLock, reminderBackup, dashboardSections ->
        SettingsState(
            theme = themeProfile.first,
            currency = themeProfile.second?.baseCurrency ?: "INR",
            hideAmounts = hideLock.first,
            appLock = hideLock.second,
            reminders = reminderBackup.first,
            lastBackupAt = reminderBackup.second,
            dashboardSections = dashboardSections
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    init {
        viewModelScope.launch { runCatching { repo.ensureBudgetPeriods() } }
    }

    private fun matchesFilter(
        item: TransactionUiModel,
        filter: TransactionFilter,
        refs: List<TransactionTagCrossRef>
    ): Boolean {
        val tx = item.entity
        val query = filter.query.trim()
        return (filter.type == null || tx.type == filter.type.name) &&
            (filter.accountId == null || tx.accountId == filter.accountId || tx.destinationAccountId == filter.accountId) &&
            (filter.categoryId == null || tx.categoryId == filter.categoryId || item.splits.any { it.categoryId == filter.categoryId }) &&
            (filter.tagId == null || refs.any { it.tagId == filter.tagId }) &&
            (filter.currencyCode == null || tx.currencyCode == filter.currencyCode || tx.destinationCurrencyCode == filter.currencyCode) &&
            (filter.minMinor == null || tx.amountMinor >= filter.minMinor) &&
            (filter.maxMinor == null || tx.amountMinor <= filter.maxMinor) &&
            (filter.hasAttachment == null || item.attachments.isNotEmpty() == filter.hasAttachment) &&
            (filter.cleared == null || tx.isCleared == filter.cleared) &&
            (filter.recurringOnly == null || (tx.recurringRuleId != null) == filter.recurringOnly) &&
            (filter.fromDate.isNullOrBlank() || tx.transactionDate >= filter.fromDate) &&
            (filter.toDate.isNullOrBlank() || tx.transactionDate <= filter.toDate) &&
            (query.isBlank() || listOf(
                item.accountName,
                item.destinationAccountName.orEmpty(),
                item.categoryName.orEmpty(),
                tx.payee,
                tx.note,
                item.tags.joinToString(" "),
                item.splits.joinToString(" ") { it.memo },
                Utils.plain(tx.amountMinor, tx.currencyCode)
            ).any { it.contains(query, ignoreCase = true) })
    }

    private fun buildBudgetModels(input: BudgetInputs, reference: String): List<BudgetUiModel> {
        val categoryMap = input.categories.associateBy { it.id }
        val splitsByTransaction = input.splits.groupBy { it.transactionId }
        return input.budgets.map { budget ->
            val window = BudgetPeriodMath.windowFor(budget, reference)
            val period = window?.let { w -> input.periods.firstOrNull { it.budgetId == budget.id && it.periodStart == w.start } }
            val categoryIds = budget.categoryId?.let { id -> input.categories.filter { it.id == id || it.parentId == id }.map { it.id }.toSet() }
            val spent = if (period == null) 0L else input.transactions.asSequence()
                .filter { it.isCleared && it.transactionDate in period.periodStart..period.periodEnd }
                .sumOf { tx ->
                    when {
                        tx.type == TransactionType.EXPENSE.name && tx.currencyCode == budget.currencyCode -> {
                            val splits = splitsByTransaction[tx.id].orEmpty()
                            if (splits.isEmpty()) {
                                if (categoryIds == null || tx.categoryId in categoryIds) tx.amountMinor else 0L
                            } else {
                                splits.filter { categoryIds == null || it.categoryId in categoryIds }.sumOf { it.amountMinor }
                            }
                        }
                        tx.type == TransactionType.TRANSFER.name && tx.currencyCode == budget.currencyCode && categoryIds == null -> tx.transferFeeMinor
                        else -> 0L
                    }
                }
            val available = period?.let { it.allocatedMinor + it.carryInMinor } ?: budget.amountMinor
            BudgetUiModel(
                entity = budget,
                categoryName = budget.categoryId?.let { categoryMap[it]?.name },
                period = period,
                spentMinor = spent,
                availableMinor = available,
                remainingMinor = available - spent
            )
        }
    }

    private fun buildReport(
        transactionList: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        categoryList: List<CategoryEntity>,
        p: ProfileEntity?,
        config: ReportConfig,
        splits: List<TransactionSplitEntity>,
        budgetModels: List<BudgetUiModel>
    ): ReportState {
        val currency = config.currencyCode.ifBlank { p?.baseCurrency ?: "INR" }
        val relevant = transactionList.filter { it.currencyCode == currency || it.destinationCurrencyCode == currency }
        val earliest = relevant.minOfOrNull { it.transactionDate }
        val window = ReportPeriodMath.window(config.preset, config.referenceDate, config.customFrom, config.customTo, earliest)
        val previousWindow = ReportPeriodMath.previous(window)
        val current = relevant.filter { it.isCleared && it.transactionDate in window.start..window.end }
        val previous = relevant.filter { it.isCleared && it.transactionDate in previousWindow.start..previousWindow.end }
        fun income(rows: List<TransactionEntity>) = rows.filter { it.type == TransactionType.INCOME.name && it.currencyCode == currency }.sumOf { it.amountMinor }
        fun expense(rows: List<TransactionEntity>) = rows.filter { it.type == TransactionType.EXPENSE.name && it.currencyCode == currency }.sumOf { it.amountMinor } +
            rows.filter { it.type == TransactionType.TRANSFER.name && it.currencyCode == currency }.sumOf { it.transferFeeMinor }
        fun transferVolume(rows: List<TransactionEntity>) = rows.filter { it.type == TransactionType.TRANSFER.name }.sumOf {
            when {
                it.currencyCode == currency -> it.amountMinor
                it.destinationCurrencyCode == currency -> it.destinationAmountMinor
                else -> 0L
            }
        }
        val currentIncome = income(current)
        val currentExpense = expense(current)
        val previousIncome = income(previous)
        val previousExpense = expense(previous)
        val categoryNames = categoryList.associate { it.id to it.name }
        val accountNames = accounts.associate { it.id to it.name }
        val splitsByTransaction = splits.groupBy { it.transactionId }
        val categoryAmounts = linkedMapOf<String, Long>()
        current.filter { it.type == TransactionType.EXPENSE.name && it.currencyCode == currency }.forEach { tx ->
            val txSplits = splitsByTransaction[tx.id].orEmpty()
            if (txSplits.isEmpty()) {
                val label = tx.categoryId?.let { categoryNames[it] } ?: "Uncategorised"
                categoryAmounts[label] = (categoryAmounts[label] ?: 0L) + tx.amountMinor
            } else {
                txSplits.forEach { split ->
                    val label = split.categoryId?.let { categoryNames[it] } ?: "Uncategorised split"
                    categoryAmounts[label] = (categoryAmounts[label] ?: 0L) + split.amountMinor
                }
            }
        }
        val feeTotal = current.filter { it.type == TransactionType.TRANSFER.name && it.currencyCode == currency }.sumOf { it.transferFeeMinor }
        if (feeTotal > 0) categoryAmounts["Transfer fees"] = feeTotal
        val categoryRows = categoryAmounts.map { ReportRow(it.key, it.value) }.sortedByDescending { it.amountMinor }

        val accountAmounts = linkedMapOf<String, Long>()
        current.forEach { tx ->
            if (tx.currencyCode == currency) accountAmounts[tx.accountId] = (accountAmounts[tx.accountId] ?: 0L) + LedgerMath.accountImpact(tx, tx.accountId)
            if (tx.destinationCurrencyCode == currency) tx.destinationAccountId?.let { id ->
                accountAmounts[id] = (accountAmounts[id] ?: 0L) + LedgerMath.accountImpact(tx, id)
            }
        }
        val accountRows = accountAmounts.map { (id, amount) -> ReportRow(accountNames[id] ?: "Deleted account", amount) }
            .sortedByDescending { abs(it.amountMinor) }

        val dailyRows = current.groupBy { it.transactionDate }.map { (date, rows) ->
            val net = rows.sumOf { tx ->
                when {
                    tx.type == TransactionType.INCOME.name && tx.currencyCode == currency -> tx.amountMinor
                    tx.type == TransactionType.EXPENSE.name && tx.currencyCode == currency -> -tx.amountMinor
                    tx.type == TransactionType.TRANSFER.name && tx.currencyCode == currency -> -tx.transferFeeMinor
                    else -> 0L
                }
            }
            ReportRow(date, net)
        }.sortedByDescending { it.label }

        val expenseTransactions = current
            .filter { it.type == TransactionType.EXPENSE.name && it.currencyCode == currency }
        val spendingDailyRows = expenseTransactions
            .groupBy { it.transactionDate }
            .map { (date, rows) -> ReportRow(date, rows.sumOf { it.amountMinor }) }
            .sortedByDescending { it.label }
        val payeeRows = expenseTransactions
            .groupBy { tx ->
                tx.payee.trim().ifBlank { tx.categoryId?.let { categoryNames[it] } ?: "Unlabelled spending" }
            }
            .map { (payee, rows) -> ReportRow(payee, rows.sumOf { it.amountMinor }) }
            .sortedByDescending { it.amountMinor }
            .take(12)

        // Behaviour metrics only use elapsed days. A current month/year must never count future
        // calendar dates as "no-spend" days or dilute the daily average.
        val windowStart = LocalDate.parse(window.start)
        val windowEnd = LocalDate.parse(window.end)
        val observedEnd = minOf(windowEnd, LocalDate.now())
        val observedTransactions = if (observedEnd.isBefore(windowStart)) {
            emptyList()
        } else {
            current.filter { !LocalDate.parse(it.transactionDate).isAfter(observedEnd) }
        }
        val observedExpenseTransactions = observedTransactions
            .filter { it.type == TransactionType.EXPENSE.name && it.currencyCode == currency }
        val observedSpendingDays = observedExpenseTransactions
            .groupBy { it.transactionDate }
            .mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }
        val elapsedDays = if (observedEnd.isBefore(windowStart)) {
            0L
        } else {
            java.time.temporal.ChronoUnit.DAYS.between(windowStart, observedEnd) + 1L
        }
        val observedExpense = expense(observedTransactions)
        val averageDailySpend = if (elapsedDays > 0L) observedExpense / elapsedDays else 0L
        val noSpendDays = (elapsedDays - observedSpendingDays.size.toLong()).coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val activeDailyValues = observedSpendingDays.values.map { it.toDouble() }
        val volatilityPercent = if (activeDailyValues.size >= 2) {
            val mean = activeDailyValues.average()
            if (mean > 0.0) {
                val variance = activeDailyValues.sumOf { value ->
                    val delta = value - mean
                    delta * delta
                } / activeDailyValues.size.toDouble()
                (kotlin.math.sqrt(variance) / mean * 100.0).roundToInt().coerceAtLeast(0)
            } else null
        } else null

        val incomeByMonth = observedTransactions
            .filter { it.type == TransactionType.INCOME.name && it.currencyCode == currency }
            .groupBy { it.transactionDate.take(7) }
            .mapValues { (_, rows) -> rows.sumOf { it.amountMinor }.toDouble() }
        val observedMonths = buildList {
            if (!observedEnd.isBefore(windowStart)) {
                var month = windowStart.withDayOfMonth(1)
                val lastMonth = observedEnd.withDayOfMonth(1)
                while (!month.isAfter(lastMonth)) {
                    add(month.toString().take(7))
                    month = month.plusMonths(1)
                }
            }
        }
        val monthlyIncomeValues = observedMonths.map { incomeByMonth[it] ?: 0.0 }
        val incomeStability = if (monthlyIncomeValues.count { it > 0.0 } < 2) {
            "Need 2+ income months"
        } else {
            val mean = monthlyIncomeValues.average()
            val variation = if (mean > 0.0) {
                val variance = monthlyIncomeValues.sumOf { value ->
                    val delta = value - mean
                    delta * delta
                } / monthlyIncomeValues.size.toDouble()
                kotlin.math.sqrt(variance) / mean * 100.0
            } else 100.0
            when {
                variation <= 20.0 -> "Stable income pattern"
                variation <= 50.0 -> "Variable income pattern"
                else -> "Irregular income pattern"
            }
        }
        val expenseByWeekday = expenseTransactions
            .groupBy { LocalDate.parse(it.transactionDate).dayOfWeek }
            .mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }
        val weekdayRows = java.time.DayOfWeek.entries.map { day ->
            ReportRow(day.name.lowercase().replaceFirstChar { it.uppercase() }, expenseByWeekday[day] ?: 0L)
        }
        val savingsRate = if (currentIncome > 0L) {
            ((currentIncome - currentExpense).toDouble() / currentIncome.toDouble() * 100.0).roundToInt()
        } else null
        val expenseChange = if (previousExpense > 0L) {
            ((currentExpense - previousExpense).toDouble() / previousExpense.toDouble() * 100.0).roundToInt()
        } else null
        val activeBudgets = budgetModels.filter { it.entity.isActive && it.entity.currencyCode == currency && it.period != null }
        val budgetAvailable = activeBudgets.sumOf { it.availableMinor.coerceAtLeast(0L) }
        val budgetSpent = activeBudgets.sumOf { it.spentMinor.coerceAtLeast(0L) }
        val budgetUsed = if (budgetAvailable > 0L) {
            (budgetSpent.toDouble() / budgetAvailable.toDouble() * 100.0).roundToInt()
        } else null
        val overBudgetCount = activeBudgets.count { it.remainingMinor < 0L }

        var score = 50
        score += when {
            currentIncome == 0L && currentExpense == 0L -> 0
            currentIncome >= currentExpense -> 15
            else -> -15
        }
        score += (savingsRate?.div(2) ?: 0).coerceIn(-15, 20)
        score += when {
            activeBudgets.isEmpty() -> 0
            overBudgetCount == 0 -> 15
            else -> -10
        }
        score += when {
            expenseChange == null -> 0
            expenseChange <= 0 -> 10
            expenseChange > 20 -> -10
            else -> -5
        }
        score = score.coerceIn(0, 100)
        val scoreLabel = when {
            score >= 80 -> "Strong"
            score >= 65 -> "Stable"
            score >= 45 -> "Building"
            else -> "Needs attention"
        }

        val insightMessages = buildList {
            if (current.isEmpty()) {
                add("Add cleared transactions to unlock personalised patterns for this period.")
            } else {
                savingsRate?.let { rate ->
                    add(if (rate >= 0) "You retained $rate% of recorded income in this window." else "Recorded spending was ${-rate}% above recorded income in this window.")
                }
                expenseChange?.let { change ->
                    add(when {
                        change <= -5 -> "Spending fell ${-change}% compared with the previous matching period."
                        change >= 5 -> "Spending rose $change% compared with the previous matching period."
                        else -> "Spending stayed close to the previous matching period."
                    })
                }
                categoryRows.firstOrNull()?.let { top ->
                    val share = if (currentExpense > 0L) (top.amountMinor.toDouble() / currentExpense.toDouble() * 100.0).roundToInt() else 0
                    add("${top.label} was the largest spending category at $share% of recorded expenses.")
                }
                when {
                    overBudgetCount == 1 -> add("1 active budget is over its current limit.")
                    overBudgetCount > 1 -> add("$overBudgetCount active budgets are over their current limits.")
                }
                weekdayRows.maxByOrNull { it.amountMinor }?.takeIf { it.amountMinor > 0L }?.let { high ->
                    add("${high.label} carried the highest recorded spending in this period.")
                }
                if (elapsedDays <= 92L && noSpendDays > 0) {
                    add("You recorded $noSpendDays no-spend day${if (noSpendDays == 1) "" else "s"} in this window.")
                }
                volatilityPercent?.let { volatility ->
                    when {
                        volatility >= 90 -> add("Daily spending varied sharply; planning around the largest days may make budgets more realistic.")
                        volatility <= 35 -> add("Recorded daily spending was relatively consistent across active spending days.")
                        else -> Unit
                    }
                }
            }
        }.take(6)

        return ReportState(
            preset = config.preset, referenceDate = config.referenceDate, fromDate = window.start, toDate = window.end,
            currencyCode = currency, incomeMinor = currentIncome, expenseMinor = currentExpense,
            netMinor = currentIncome - currentExpense, transferVolumeMinor = transferVolume(current),
            previousIncomeMinor = previousIncome, previousExpenseMinor = previousExpense,
            savingsRatePercent = savingsRate, expenseChangePercent = expenseChange,
            financialScore = score, scoreLabel = scoreLabel, budgetUsedPercent = budgetUsed,
            overBudgetCount = overBudgetCount, transactionCount = current.size,
            averageDailySpendMinor = averageDailySpend, noSpendDays = noSpendDays,
            spendingVolatilityPercent = volatilityPercent, incomeStabilityLabel = incomeStability,
            insightMessages = insightMessages, topPayeeRows = payeeRows,
            categoryRows = categoryRows, accountRows = accountRows, dailyRows = dailyRows,
            spendingDailyRows = spendingDailyRows, weekdayRows = weekdayRows
        )
    }

    private fun buildForecast(
        p: ProfileEntity?,
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        recurringRules: List<RecurringRuleEntity>,
        items: List<WorkspaceItemEntity>
    ): ForecastState {
        val currency = p?.baseCurrency ?: "INR"
        val balances = LedgerMath.balances(accounts, transactions)
        val starting = accounts.filter { !it.isArchived && it.includeInTotal && it.currencyCode == currency }
            .sumOf { balances[it.id] ?: it.openingBalanceMinor }
        val start = LocalDate.now()
        val end = start.plusDays(89)
        val movements = mutableMapOf<LocalDate, Long>()
        val assumptions = mutableListOf<String>()

        recurringRules.filter { it.isActive }.forEach { original ->
            var date = runCatching { LocalDate.parse(original.nextDueDate) }.getOrNull() ?: return@forEach
            val endDate = original.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            var remaining = original.occurrencesRemaining
            var safety = 0
            while (!date.isAfter(end) && safety < 1000) {
                if (remaining != null && remaining <= 0) break
                if (endDate != null && date.isAfter(endDate)) break
                if (!date.isBefore(start)) {
                    val impact = when (TransactionType.from(original.type)) {
                        TransactionType.INCOME -> if (original.currencyCode == currency) original.amountMinor else 0L
                        TransactionType.EXPENSE -> if (original.currencyCode == currency) -original.amountMinor else 0L
                        TransactionType.TRANSFER -> if (original.currencyCode == currency) -original.transferFeeMinor else 0L
                    }
                    movements[date] = (movements[date] ?: 0L) + impact
                }
                remaining = remaining?.minus(1)
                val next = Utils.nextRecurrence(
                    date.toString(), RecurrenceFrequency.from(original.frequency), original.intervalCount,
                    original.anchorDay, MonthEndMode.from(original.monthEndMode)
                )
                date = LocalDate.parse(next)
                safety++
            }
        }
        if (recurringRules.any { it.isActive }) assumptions += "Active recurring rules follow their recorded end dates and remaining occurrence limits."

        val scheduledTypes = setOf(
            WorkspaceType.PLANNED_PAYMENT.name, WorkspaceType.BILL.name, WorkspaceType.EMI.name,
            WorkspaceType.LOAN.name, WorkspaceType.DEBT.name, WorkspaceType.LIABILITY.name,
            WorkspaceType.SUBSCRIPTION.name
        )
        items.filter {
            it.status == WorkspaceStatus.ACTIVE.name && it.currencyCode == currency && it.type in scheduledTypes
        }.forEach { item ->
            val dueDate = item.dueDate ?: return@forEach
            val type = WorkspaceType.from(item.type)
            val amount = when (type) {
                WorkspaceType.BILL -> (item.amountMinor - item.currentMinor).coerceAtLeast(0L)
                WorkspaceType.EMI, WorkspaceType.LOAN, WorkspaceType.DEBT, WorkspaceType.LIABILITY -> {
                    val remaining = item.currentMinor.coerceAtLeast(0L)
                    if (item.secondaryMinor > 0L) item.secondaryMinor.coerceAtMost(remaining) else remaining
                }
                else -> item.amountMinor.coerceAtLeast(0L)
            }
            if (amount <= 0L) return@forEach
            val dates = if (type == WorkspaceType.SUBSCRIPTION) {
                Utils.billingOccurrences(dueDate, BillingCadence.from(item.secondaryCode), start, end)
            } else {
                listOfNotNull(runCatching { LocalDate.parse(dueDate) }.getOrNull())
                    .filter { !it.isBefore(start) && !it.isAfter(end) }
            }
            dates.forEach { date -> movements[date] = (movements[date] ?: 0L) - amount }
        }
        if (items.any { it.status == WorkspaceStatus.ACTIVE.name && it.type in scheduledTypes }) {
            assumptions += "Bills use their unpaid amount; EMIs, loans and debt use the recorded scheduled payment where available. Subscriptions repeat using their saved billing cycle."
        }

        var balance = starting
        var lowest = starting
        var lowestDate = start
        val points = (0L..89L).map { offset ->
            val date = start.plusDays(offset)
            balance += movements[date] ?: 0L
            if (balance < lowest) { lowest = balance; lowestDate = date }
            ForecastPoint(date.toString(), balance)
        }
        if (assumptions.isEmpty()) assumptions += "No upcoming recurring or planned activity is currently recorded."
        return ForecastState(currency, starting, balance, lowest, lowestDate.toString(), points, assumptions)
    }

    fun setAccountFilter(value: AccountFilter) { accountFilter.value = value }
    fun setSearch(query: String) { transactionFilter.value = transactionFilter.value.copy(query = query) }
    fun setTransactionType(value: TransactionType?) { transactionFilter.value = transactionFilter.value.copy(type = value) }

    fun setAdvancedFilter(
        accountId: String?,
        categoryId: String?,
        tagId: String?,
        currencyCode: String?,
        minMinor: Long?,
        maxMinor: Long?,
        hasAttachment: Boolean?,
        cleared: Boolean?,
        recurringOnly: Boolean?,
        from: String?,
        to: String?,
        sort: TransactionSort
    ) {
        transactionFilter.value = transactionFilter.value.copy(
            accountId = accountId,
            categoryId = categoryId,
            tagId = tagId,
            currencyCode = currencyCode,
            minMinor = minMinor,
            maxMinor = maxMinor,
            hasAttachment = hasAttachment,
            cleared = cleared,
            recurringOnly = recurringOnly,
            fromDate = from?.trim()?.ifBlank { null },
            toDate = to?.trim()?.ifBlank { null },
            sort = sort
        )
    }

    fun clearTransactionFilters() { transactionFilter.value = TransactionFilter() }

    fun clearAdvancedFilters() {
        transactionFilter.value = transactionFilter.value.copy(
            accountId = null,
            categoryId = null,
            tagId = null,
            currencyCode = null,
            minMinor = null,
            maxMinor = null,
            hasAttachment = null,
            cleared = null,
            recurringOnly = null,
            fromDate = null,
            toDate = null,
            sort = TransactionSort.DATE_NEWEST
        )
    }

    fun setBudgetReference(date: String, done: (Result<Int>) -> Unit = {}) {
        if (Utils.validDate(date) != null) {
            done(Result.failure(IllegalArgumentException("Invalid date")))
            return
        }
        budgetReferenceDate.value = date
        viewModelScope.launch { done(runCatching { repo.ensureBudgetPeriods(date) }) }
    }

    fun stepBudgetReference(months: Long, done: (Result<Int>) -> Unit = {}) {
        val next = LocalDate.parse(budgetReferenceDate.value).plusMonths(months).toString()
        setBudgetReference(next, done)
    }

    fun setReportPreset(value: ReportPreset) {
        reportConfig.value = reportConfig.value.copy(preset = value)
    }

    fun setReportCurrency(value: String) {
        reportConfig.value = reportConfig.value.copy(currencyCode = value)
    }

    fun setReportReference(date: String) {
        if (Utils.validDate(date) == null) reportConfig.value = reportConfig.value.copy(referenceDate = date)
    }

    fun setReportCustomRange(from: String, to: String) {
        if (Utils.validDate(from) == null && Utils.validDate(to) == null && from <= to) {
            reportConfig.value = reportConfig.value.copy(
                preset = ReportPreset.CUSTOM,
                customFrom = from,
                customTo = to,
                referenceDate = to
            )
        }
    }

    fun stepReport(direction: Int) {
        val config = reportConfig.value
        val currentWindow = report.value.takeIf { it.fromDate.isNotBlank() }?.let { DateWindow(it.fromDate, it.toDate) }
            ?: return
        val nextReference = when (config.preset) {
            ReportPreset.DAY -> LocalDate.parse(config.referenceDate).plusDays(direction.toLong())
            ReportPreset.WEEK -> LocalDate.parse(config.referenceDate).plusWeeks(direction.toLong())
            ReportPreset.MONTH -> LocalDate.parse(config.referenceDate).plusMonths(direction.toLong())
            ReportPreset.QUARTER -> LocalDate.parse(config.referenceDate).plusMonths(3L * direction)
            ReportPreset.SIX_MONTHS -> LocalDate.parse(config.referenceDate).plusMonths(6L * direction)
            ReportPreset.YEAR -> LocalDate.parse(config.referenceDate).plusYears(direction.toLong())
            ReportPreset.ALL_TIME -> return
            ReportPreset.CUSTOM -> LocalDate.parse(ReportPeriodMath.shift(currentWindow, direction).end)
        }
        val shifted = if (config.preset == ReportPreset.CUSTOM) ReportPeriodMath.shift(currentWindow, direction) else null
        reportConfig.value = if (shifted == null) {
            config.copy(referenceDate = nextReference.toString())
        } else {
            config.copy(referenceDate = nextReference.toString(), customFrom = shifted.start, customTo = shifted.end)
        }
    }

    fun saveAccount(draft: AccountDraft, done: (Result<Unit>) -> Unit) = launch(done) { repo.saveAccount(draft) }
    fun archiveAccount(item: AccountEntity, value: Boolean, done: (Result<Unit>) -> Unit) = launch(done) { repo.archiveAccount(item, value) }
    fun deleteAccount(item: AccountEntity, done: (Result<Unit>) -> Unit) = launch(done) { repo.deleteAccount(item) }
    fun saveCategory(draft: CategoryDraft, done: (Result<Unit>) -> Unit) = launch(done) { repo.saveCategory(draft) }
    fun archiveCategory(item: CategoryEntity, value: Boolean, done: (Result<Unit>) -> Unit) = launch(done) { repo.archiveCategory(item, value) }
    fun deleteCategory(item: CategoryEntity, done: (Result<Unit>) -> Unit) = launch(done) { repo.deleteCategory(item) }
    fun saveTransaction(draft: TransactionDraft, done: (Result<Unit>) -> Unit) = launch(done) { repo.saveTransaction(draft) }
    fun deleteTransaction(item: TransactionEntity, done: (Result<Unit>) -> Unit) = launch(done) { repo.deleteTransaction(item) }
    fun undoDeleteTransaction(id: String, done: (Result<Unit>) -> Unit) = launch(done) { repo.undoDeleteTransaction(id) }
    fun saveRecurring(draft: RecurringRuleDraft, done: (Result<Unit>) -> Unit) = launch(done) { repo.saveRecurring(draft) }
    fun deleteRecurring(item: RecurringRuleEntity, done: (Result<Unit>) -> Unit) = launch(done) { repo.deleteRecurring(item) }
    fun setRecurringActive(item: RecurringRuleEntity, active: Boolean, done: (Result<Unit>) -> Unit) = launch(done) { repo.setRecurringActive(item, active) }
    fun skipRecurring(item: RecurringRuleEntity, done: (Result<Unit>) -> Unit) = launch(done) { repo.skipRecurring(item) }
    fun runRecurringNow(item: RecurringRuleEntity, done: (Result<String>) -> Unit) { viewModelScope.launch { done(runCatching { repo.runRecurringNow(item) }) } }
    fun saveBudget(draft: BudgetDraft, done: (Result<Unit>) -> Unit) = launch(done) { repo.saveBudget(draft) }
    fun deleteBudget(item: BudgetEntity, done: (Result<Unit>) -> Unit) = launch(done) { repo.deleteBudget(item) }
    fun postDueRecurring(done: (Result<Int>) -> Unit) {
        viewModelScope.launch { done(runCatching { repo.postDueRecurring() }) }
    }

    fun saveWorkspaceItem(draft: WorkspaceItemDraft, done: (Result<Unit>) -> Unit) = launch(done) { repo.saveWorkspaceItem(draft) }
    fun deleteWorkspaceItem(item: WorkspaceItemEntity, done: (Result<Unit>) -> Unit) = launch(done) { repo.deleteWorkspaceItem(item) }
    fun setWorkspaceStatus(item: WorkspaceItemEntity, status: WorkspaceStatus, done: (Result<Unit>) -> Unit) = launch(done) { repo.setWorkspaceStatus(item, status) }
    fun saveWorkspaceEvent(draft: WorkspaceEventDraft, done: (Result<Unit>) -> Unit) = launch(done) { repo.saveWorkspaceEvent(draft) }
    fun setWorkspaceEventCompleted(item: WorkspaceEventEntity, completed: Boolean, done: (Result<Unit>) -> Unit) = launch(done) { repo.setWorkspaceEventCompleted(item, completed) }
    fun deleteWorkspaceEvent(item: WorkspaceEventEntity, done: (Result<Unit>) -> Unit) = launch(done) { repo.deleteWorkspaceEvent(item) }
    fun postWorkspaceItem(id: String, done: (Result<String>) -> Unit) { viewModelScope.launch { done(runCatching { repo.postWorkspaceItem(id) }) } }
    fun saveCurrentFilter(name: String, done: (Result<Unit>) -> Unit) = launch(done) { repo.saveSavedFilter(name, transactionFilter.value) }
    fun applySavedFilter(item: SavedFilterEntity) {
        transactionFilter.value = TransactionFilter(
            query = item.query, type = item.type?.let(TransactionType::from), accountId = item.accountId,
            categoryId = item.categoryId, tagId = item.tagId, currencyCode = item.currencyCode,
            minMinor = item.minMinor, maxMinor = item.maxMinor, hasAttachment = item.hasAttachment,
            cleared = item.cleared, recurringOnly = item.recurringOnly, fromDate = item.fromDate,
            toDate = item.toDate, sort = TransactionSort.from(item.sort)
        )
    }
    fun deleteSavedFilter(item: SavedFilterEntity, done: (Result<Unit>) -> Unit) = launch(done) { repo.deleteSavedFilter(item) }
    fun duplicateTransaction(item: TransactionEntity, done: (Result<Unit>) -> Unit) = launch(done) { repo.duplicateTransaction(item) }
    fun adjustAccountBalance(item: AccountEntity, targetMinor: Long, reason: String, done: (Result<Unit>) -> Unit) = launch(done) { repo.adjustAccountBalance(item, targetMinor, reason) }
    fun mergeCategory(source: CategoryEntity, target: CategoryEntity, done: (Result<Unit>) -> Unit) = launch(done) { repo.mergeCategory(source, target) }
    fun scanAttachmentIntegrity(done: (Result<IntegrityReport>) -> Unit) { viewModelScope.launch { done(runCatching { repo.scanAttachmentIntegrity() }) } }

    fun setHideAmounts(value: Boolean, done: (Result<Unit>) -> Unit = {}) = launch(done) {
        settings.setHideAmounts(value)
    }

    fun setAppLock(value: Boolean, done: (Result<Unit>) -> Unit = {}) = launch(done) { settings.setAppLock(value) }
    fun setReminders(value: Boolean, done: (Result<Unit>) -> Unit = {}) = launch(done) { settings.setReminders(value) }
    fun setDashboardSections(value: DashboardSections, done: (Result<Unit>) -> Unit = {}) = launch(done) {
        settings.setDashboardSections(value)
    }
    fun markBackupComplete(done: (Result<Unit>) -> Unit = {}) = launch(done) { settings.setLastBackupAt(System.currentTimeMillis()) }

    fun updateSettings(theme: ThemeMode, currency: String, done: (Result<Unit>) -> Unit) = launch(done) {
        repo.updateSettings(theme, currency)
        reportConfig.value = reportConfig.value.copy(currencyCode = currency)
    }

    private fun launch(done: (Result<Unit>) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch { done(runCatching { block() }) }
    }

    companion object {
        fun factory(app: GoldmineApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LedgerViewModel(app.container.repository, app.container.settings) as T
        }
    }
}
