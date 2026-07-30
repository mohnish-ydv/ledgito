package com.mohnishraj.goldmineledger

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountType(val label: String) {
    CASH("Cash"), BANK("Bank account"), WALLET("Wallet"), CREDIT_CARD("Credit card"), CUSTOM("Custom");
    companion object { fun from(value: String) = entries.firstOrNull { it.name == value } ?: CUSTOM }
}

enum class CategoryKind(val label: String) {
    EXPENSE("Expense"), INCOME("Income");
    companion object { fun from(value: String) = entries.firstOrNull { it.name == value } ?: EXPENSE }
}

enum class TransactionType(val label: String) {
    EXPENSE("Expense"), INCOME("Income"), TRANSFER("Transfer");
    companion object { fun from(value: String) = entries.firstOrNull { it.name == value } ?: EXPENSE }
}

enum class RecurrenceFrequency(val label: String) {
    DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly");
    companion object { fun from(value: String) = entries.firstOrNull { it.name == value } ?: MONTHLY }
}

enum class RecurringPostingMode(val label: String) {
    AUTO("Create automatically"), REMIND("Remind me first");
    companion object { fun from(value: String) = entries.firstOrNull { it.name == value } ?: AUTO }
}

enum class MonthEndMode(val label: String) {
    LAST_VALID_DAY("Use last valid day"), SKIP_INVALID_MONTH("Skip invalid month");
    companion object { fun from(value: String) = entries.firstOrNull { it.name == value } ?: LAST_VALID_DAY }
}

enum class TransactionSort(val label: String) {
    DATE_NEWEST("Newest first"), DATE_OLDEST("Oldest first"), AMOUNT_HIGH("Highest amount"),
    AMOUNT_LOW("Lowest amount"), PAYEE_AZ("Payee A–Z");
    companion object { fun from(value: String) = entries.firstOrNull { it.name == value } ?: DATE_NEWEST }
}

enum class BudgetPeriodType(val label: String) {
    WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly"), CUSTOM("Custom period");
    companion object { fun from(value: String) = entries.firstOrNull { it.name == value } ?: MONTHLY }
}

enum class BudgetCarryover(val label: String) {
    OFF("No carry-over"), POSITIVE_ONLY("Carry unused only"), FULL("Carry surplus or overspend");
    companion object { fun from(value: String) = entries.firstOrNull { it.name == value } ?: OFF }
}

enum class ReportPreset(val label: String) {
    DAY("Day"), WEEK("Week"), MONTH("Month"), QUARTER("Quarter"), SIX_MONTHS("6 months"),
    YEAR("Year"), ALL_TIME("All time"), CUSTOM("Custom");
    companion object { fun from(value: String) = entries.firstOrNull { it.name == value } ?: MONTH }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseCurrency: String,
    val localeTag: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "accounts",
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("profileId"), Index(value = ["profileId", "name"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val type: String,
    val currencyCode: String,
    val openingBalanceMinor: Long,
    val openingDate: String,
    val includeInTotal: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int,
    val colourArgb: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("profileId"), Index("parentId"), Index(value = ["profileId", "kind", "name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val parentId: String?,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val kind: String,
    val iconKey: String,
    val colourArgb: Int,
    val isSystem: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["destinationAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [
        Index(value = ["profileId", "transactionDate"]), Index("accountId"),
        Index("destinationAccountId"), Index("categoryId"), Index("createdAt"),
        Index(value = ["recurringRuleId", "transactionDate"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val type: String,
    val accountId: String,
    val destinationAccountId: String?,
    val categoryId: String?,
    val amountMinor: Long,
    val currencyCode: String,
    val destinationAmountMinor: Long,
    val destinationCurrencyCode: String?,
    val transferFeeMinor: Long,
    val transactionDate: String,
    val payee: String,
    val note: String,
    val isCleared: Boolean,
    val recurringRuleId: String?,
    val isDeleted: Boolean,
    val deletedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "transaction_splits",
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("transactionId"), Index("categoryId")]
)
data class TransactionSplitEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val categoryId: String?,
    val amountMinor: Long,
    val memo: String,
    val sortOrder: Int
)

@Entity(
    tableName = "transaction_revisions",
    foreignKeys = [ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["transactionId", "timestamp"])]
)
data class TransactionRevisionEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val action: String,
    val summary: String,
    val timestamp: Long
)

@Entity(
    tableName = "tags",
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId"), Index(value = ["profileId", "name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "transaction_tag_cross_ref",
    primaryKeys = ["transactionId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tagId")]
)
data class TransactionTagCrossRef(
    val transactionId: String,
    val tagId: String
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("transactionId")]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val transactionId: String,
    val displayName: String,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long,
    val createdAt: Long
)

@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["destinationAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["profileId", "nextDueDate"]), Index("accountId"), Index("destinationAccountId"), Index("categoryId")]
)
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val type: String,
    val accountId: String,
    val destinationAccountId: String?,
    val categoryId: String?,
    val amountMinor: Long,
    val currencyCode: String,
    val destinationAmountMinor: Long,
    val destinationCurrencyCode: String?,
    val transferFeeMinor: Long,
    val payee: String,
    val note: String,
    val tagsCsv: String,
    val frequency: String,
    val intervalCount: Int,
    val anchorDay: Int,
    val monthEndMode: String,
    val postingMode: String,
    val occurrencesRemaining: Int?,
    val nextDueDate: String,
    val endDate: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("profileId"), Index("categoryId"), Index(value = ["profileId", "name"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val categoryId: String?,
    val amountMinor: Long,
    val currencyCode: String,
    val periodType: String,
    val anchorDate: String,
    val customEndDate: String?,
    val repeatInterval: Int,
    val repeatUntil: String?,
    val carryoverMode: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "budget_periods",
    foreignKeys = [ForeignKey(entity = BudgetEntity::class, parentColumns = ["id"], childColumns = ["budgetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("budgetId"), Index(value = ["budgetId", "periodStart"], unique = true)]
)
data class BudgetPeriodEntity(
    @PrimaryKey val id: String,
    val budgetId: String,
    val periodStart: String,
    val periodEnd: String,
    val allocatedMinor: Long,
    val carryInMinor: Long,
    val createdAt: Long
)

@Entity(
    tableName = "audit_events",
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["profileId", "timestamp"])]
)
data class AuditEventEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val timestamp: Long
)

data class AccountDraft(
    val id: String?, val name: String, val type: AccountType, val currency: String,
    val openingMinor: Long, val openingDate: String, val includeInTotal: Boolean,
    val archived: Boolean, val colourArgb: Int
)

data class CategoryDraft(
    val id: String?, val name: String, val kind: CategoryKind, val parentId: String?,
    val iconKey: String, val colourArgb: Int, val archived: Boolean
)

data class TransactionSplitDraft(
    val categoryId: String,
    val amountMinor: Long,
    val memo: String = ""
)

data class TransactionDraft(
    val id: String?,
    val type: TransactionType,
    val accountId: String,
    val destinationAccountId: String?,
    val categoryId: String?,
    val amountMinor: Long,
    val destinationAmountMinor: Long?,
    val transferFeeMinor: Long,
    val splits: List<TransactionSplitDraft>,
    val date: String,
    val payee: String,
    val note: String,
    val tags: List<String>,
    val cleared: Boolean,
    val attachmentUris: List<String>,
    val removeExistingAttachments: Boolean
)

data class RecurringRuleDraft(
    val id: String?,
    val name: String,
    val type: TransactionType,
    val accountId: String,
    val destinationAccountId: String?,
    val categoryId: String?,
    val amountMinor: Long,
    val destinationAmountMinor: Long?,
    val transferFeeMinor: Long,
    val payee: String,
    val note: String,
    val tags: List<String>,
    val frequency: RecurrenceFrequency,
    val intervalCount: Int,
    val monthEndMode: MonthEndMode,
    val postingMode: RecurringPostingMode,
    val occurrencesRemaining: Int?,
    val nextDueDate: String,
    val endDate: String?,
    val active: Boolean
)

data class BudgetDraft(
    val id: String?,
    val name: String,
    val categoryId: String?,
    val amountMinor: Long,
    val currencyCode: String,
    val periodType: BudgetPeriodType,
    val anchorDate: String,
    val customEndDate: String?,
    val repeatInterval: Int,
    val repeatUntil: String?,
    val carryover: BudgetCarryover,
    val active: Boolean
)

data class BudgetUiModel(
    val entity: BudgetEntity,
    val categoryName: String?,
    val period: BudgetPeriodEntity?,
    val spentMinor: Long,
    val availableMinor: Long,
    val remainingMinor: Long
)

data class ReportRow(val label: String, val amountMinor: Long)

data class ReportState(
    val preset: ReportPreset = ReportPreset.MONTH,
    val referenceDate: String = "",
    val fromDate: String = "",
    val toDate: String = "",
    val currencyCode: String = "INR",
    val incomeMinor: Long = 0,
    val expenseMinor: Long = 0,
    val netMinor: Long = 0,
    val transferVolumeMinor: Long = 0,
    val previousIncomeMinor: Long = 0,
    val previousExpenseMinor: Long = 0,
    val savingsRatePercent: Int? = null,
    val expenseChangePercent: Int? = null,
    val financialScore: Int = 50,
    val scoreLabel: String = "Building",
    val budgetUsedPercent: Int? = null,
    val overBudgetCount: Int = 0,
    val transactionCount: Int = 0,
    val averageDailySpendMinor: Long = 0,
    val noSpendDays: Int = 0,
    val spendingVolatilityPercent: Int? = null,
    val incomeStabilityLabel: String = "Need more history",
    val insightMessages: List<String> = emptyList(),
    val topPayeeRows: List<ReportRow> = emptyList(),
    val categoryRows: List<ReportRow> = emptyList(),
    val accountRows: List<ReportRow> = emptyList(),
    val dailyRows: List<ReportRow> = emptyList(),
    val spendingDailyRows: List<ReportRow> = emptyList(),
    val weekdayRows: List<ReportRow> = emptyList()
)

data class AccountUiModel(val entity: AccountEntity, val currentBalanceMinor: Long)

data class TransactionUiModel(
    val entity: TransactionEntity,
    val accountName: String,
    val destinationAccountName: String?,
    val categoryName: String?,
    val tags: List<String>,
    val attachments: List<AttachmentEntity>,
    val splits: List<TransactionSplitEntity> = emptyList(),
    val revisions: List<TransactionRevisionEntity> = emptyList(),
    val runningBalanceMinor: Long? = null,
    val runningBalanceCurrency: String? = null
)

data class RecurringUiModel(
    val entity: RecurringRuleEntity,
    val accountName: String,
    val destinationAccountName: String?,
    val categoryName: String?
)

enum class WorkspaceType(val label: String) {
    PLANNED_PAYMENT("Planned payments"),
    BILL("Bills"),
    EMI("EMIs & instalments"),
    GOAL("Savings goals"),
    DEBT("Debt payoff"),
    LOAN("Loans"),
    SUBSCRIPTION("Subscriptions"),
    INVESTMENT("Investments"),
    MUTUAL_FUND("Mutual funds"),
    GOLD("Gold"),
    FIXED_DEPOSIT("Fixed deposits"),
    PPF("PPF"),
    EPF("EPF"),
    CRYPTO("Crypto"),
    ASSET("Assets"),
    LIABILITY("Liabilities"),
    CREDIT("Credit health"),
    SHOPPING_LIST("Shopping lists"),
    WARRANTY("Warranties"),
    LOYALTY("Loyalty cards"),
    SHARED_EXPENSE("Shared expenses"),
    CURRENCY_RATE("Currency rates");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.name == value } ?: PLANNED_PAYMENT
    }
}

enum class WorkspaceStatus(val label: String) {
    ACTIVE("Active"), PAUSED("Paused"), COMPLETED("Completed"), ARCHIVED("Archived");
    companion object { fun from(value: String) = entries.firstOrNull { it.name == value } ?: ACTIVE }
}

enum class BillingCadence(val label: String) {
    WEEKLY("Weekly"), MONTHLY("Monthly"), QUARTERLY("Quarterly"), YEARLY("Yearly");

    companion object {
        fun from(value: String?): BillingCadence = entries.firstOrNull {
            it.name.equals(value?.trim(), ignoreCase = true)
        } ?: MONTHLY
    }
}

@Entity(
    tableName = "workspace_items",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["linkedTransactionId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("profileId"), Index(value = ["profileId", "type"]), Index("accountId"), Index("categoryId"), Index("linkedTransactionId")]
)
data class WorkspaceItemEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val type: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val title: String,
    val amountMinor: Long,
    val currentMinor: Long,
    val secondaryMinor: Long,
    val currencyCode: String,
    val secondaryCode: String,
    val startDate: String?,
    val dueDate: String?,
    val accountId: String?,
    val categoryId: String?,
    val linkedTransactionId: String?,
    val status: String,
    val note: String,
    val metadata: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "workspace_events",
    foreignKeys = [ForeignKey(entity = WorkspaceItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId"), Index(value = ["itemId", "eventDate"])]
)
data class WorkspaceEventEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val kind: String,
    val label: String,
    val amountMinor: Long,
    val eventDate: String,
    val isCompleted: Boolean,
    val note: String,
    val createdAt: Long
)

@Entity(
    tableName = "saved_filters",
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId"), Index(value = ["profileId", "name"], unique = true)]
)
data class SavedFilterEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val query: String,
    val type: String?,
    val accountId: String?,
    val categoryId: String?,
    val tagId: String?,
    val currencyCode: String?,
    val minMinor: Long?,
    val maxMinor: Long?,
    val hasAttachment: Boolean?,
    val cleared: Boolean?,
    val recurringOnly: Boolean?,
    val fromDate: String?,
    val toDate: String?,
    val sort: String,
    val createdAt: Long
)

data class WorkspaceItemDraft(
    val id: String?,
    val type: WorkspaceType,
    val title: String,
    val amountMinor: Long,
    val currentMinor: Long,
    val secondaryMinor: Long,
    val currencyCode: String,
    val secondaryCode: String,
    val startDate: String?,
    val dueDate: String?,
    val accountId: String?,
    val categoryId: String?,
    val linkedTransactionId: String?,
    val status: WorkspaceStatus,
    val note: String,
    val metadata: String
)

data class WorkspaceEventDraft(
    val itemId: String,
    val kind: String,
    val label: String,
    val amountMinor: Long,
    val eventDate: String,
    val isCompleted: Boolean,
    val note: String,
    val postToLedger: Boolean = false
)

data class WorkspaceItemUiModel(
    val entity: WorkspaceItemEntity,
    val accountName: String?,
    val categoryName: String?,
    val events: List<WorkspaceEventEntity>
)

data class NetWorthState(
    val currencyCode: String = "INR",
    val accountValueMinor: Long = 0,
    val investmentValueMinor: Long = 0,
    val assetValueMinor: Long = 0,
    val debtMinor: Long = 0,
    val netWorthMinor: Long = 0,
    val rows: List<ReportRow> = emptyList()
)

data class ForecastPoint(val date: String, val balanceMinor: Long)

data class ForecastState(
    val currencyCode: String = "INR",
    val startingMinor: Long = 0,
    val endingMinor: Long = 0,
    val lowestMinor: Long = 0,
    val lowestDate: String = "",
    val points: List<ForecastPoint> = emptyList(),
    val assumptions: List<String> = emptyList()
)

data class IntegrityReport(
    val totalAttachments: Int,
    val healthyAttachments: Int,
    val missingAttachments: List<String>
)

data class ImportReport(
    val imported: Int,
    val skipped: Int,
    val errors: List<String>
)
