package com.mohnishraj.goldmineledger

data class WorkspaceConfig(
    val type: WorkspaceType,
    val icon: String,
    val group: String,
    val title: String,
    val subtitle: String,
    val amountLabel: String,
    val currentLabel: String,
    val startLabel: String,
    val dueLabel: String,
    val metadataLabel: String,
    val eventKind: String?,
    val eventAction: String?,
    val eventAmountLabel: String,
    val supportsLedgerPost: Boolean = false,
    val showAccountAndCategory: Boolean = false,
    val usesRate: Boolean = false,
    val usesPoints: Boolean = false
)

object WorkspaceCatalog {
    val all: List<WorkspaceConfig> = listOf(
        WorkspaceConfig(
            WorkspaceType.PLANNED_PAYMENT, "◷", "Planning", "Planned payments",
            "Future purchases and one-off expenses with a clear due-date trail.",
            "Planned amount", "Posted", "Planned on", "Due date", "Reference / purpose",
            null, null, "Amount", supportsLedgerPost = true, showAccountAndCategory = true
        ),
        WorkspaceConfig(
            WorkspaceType.BILL, "▤", "Planning", "Bills",
            "Electricity, rent, insurance and other obligations in one calm queue.",
            "Bill amount", "Paid so far", "Added", "Due date", "Provider / billing cycle",
            "PAYMENT", "Record payment", "Payment", supportsLedgerPost = true, showAccountAndCategory = true
        ),
        WorkspaceConfig(
            WorkspaceType.EMI, "▥", "Planning", "EMIs & instalments",
            "Track total payable, instalments completed and the next payment date.",
            "Total payable", "Remaining", "Started", "Next instalment", "Instalment / lender / tenure",
            "PAYMENT", "Record instalment", "Payment", supportsLedgerPost = true, showAccountAndCategory = true
        ),
        WorkspaceConfig(
            WorkspaceType.GOAL, "◎", "Planning", "Savings goals",
            "Targets with contribution history, progress and completion states.",
            "Target amount", "Saved so far", "Started", "Target date", "Purpose",
            "CONTRIBUTION", "Add contribution", "Contribution"
        ),
        WorkspaceConfig(
            WorkspaceType.DEBT, "↘", "Planning", "Debt payoff",
            "Track balances and payments without pretending to give financial advice.",
            "Original balance", "Remaining balance", "Started", "Target payoff", "APR / lender note",
            "PAYMENT", "Record payment", "Payment", supportsLedgerPost = true, showAccountAndCategory = true
        ),
        WorkspaceConfig(
            WorkspaceType.LOAN, "▦", "Planning", "Loans",
            "Keep principal, outstanding balance, lender details and repayments together.",
            "Original principal", "Outstanding", "Started", "Next payment", "Lender / APR / tenure",
            "PAYMENT", "Record repayment", "Repayment", supportsLedgerPost = true, showAccountAndCategory = true
        ),
        WorkspaceConfig(
            WorkspaceType.SUBSCRIPTION, "↻", "Planning", "Subscriptions",
            "See recurring services, renewal dates and the monthly cost stack.",
            "Renewal amount", "Last posted", "Started", "Next renewal", "Billing cycle / cancellation note",
            null, null, "Amount", supportsLedgerPost = true, showAccountAndCategory = true
        ),
        WorkspaceConfig(
            WorkspaceType.INVESTMENT, "↗", "Wealth", "Investments",
            "Manual-first holdings with cost basis, current value and update history.",
            "Cost basis", "Current value", "Acquired", "Review date", "Asset class / institution",
            "VALUE", "Update value", "New value"
        ),
        WorkspaceConfig(
            WorkspaceType.MUTUAL_FUND, "⌁", "Wealth", "Mutual funds",
            "Track invested value, current value, scheme and units without live-market dependency.",
            "Invested amount", "Current value", "First investment", "Review date", "Scheme / folio / units",
            "VALUE", "Update NAV value", "Current value"
        ),
        WorkspaceConfig(
            WorkspaceType.GOLD, "◈", "Wealth", "Gold",
            "Physical or digital gold with weight, purity, cost and latest valuation.",
            "Purchase value", "Current value", "Purchased", "Valuation date", "Weight / purity / locker",
            "VALUE", "Update value", "Current value"
        ),
        WorkspaceConfig(
            WorkspaceType.FIXED_DEPOSIT, "▰", "Wealth", "Fixed deposits",
            "Principal, maturity value, bank, rate and maturity date in one place.",
            "Principal", "Current / maturity value", "Opened", "Maturity date", "Bank / rate / receipt",
            "VALUE", "Update value", "Current value"
        ),
        WorkspaceConfig(
            WorkspaceType.PPF, "P", "Wealth", "PPF",
            "Track contributions and current PPF balance with yearly review dates.",
            "Contribution target", "Current balance", "Opened", "Maturity / review", "Provider / account reference",
            "VALUE", "Update balance", "Current balance"
        ),
        WorkspaceConfig(
            WorkspaceType.EPF, "E", "Wealth", "EPF",
            "Track employee provident-fund balance and manual statement updates.",
            "Contributed amount", "Current balance", "Started", "Review date", "Employer / UAN hint",
            "VALUE", "Update balance", "Current balance"
        ),
        WorkspaceConfig(
            WorkspaceType.CRYPTO, "◇", "Wealth", "Crypto",
            "Manual token holdings with cost and current value; no prices or investment advice.",
            "Cost basis", "Current value", "Acquired", "Review date", "Token / quantity / wallet hint",
            "VALUE", "Update value", "Current value"
        ),
        WorkspaceConfig(
            WorkspaceType.ASSET, "◆", "Wealth", "Assets",
            "Property, vehicles and other owned value in your net-worth view.",
            "Purchase value", "Current value", "Acquired", "Next valuation", "Asset details",
            "VALUE", "Update value", "New value"
        ),
        WorkspaceConfig(
            WorkspaceType.LIABILITY, "▼", "Wealth", "Liabilities",
            "Track non-loan obligations and reduce them with a payment history.",
            "Original liability", "Outstanding", "Started", "Target clearance", "Counterparty / terms",
            "PAYMENT", "Record payment", "Payment", supportsLedgerPost = true, showAccountAndCategory = true
        ),
        WorkspaceConfig(
            WorkspaceType.CREDIT, "%", "Wealth", "Credit health",
            "Private credit-limit and utilisation tracking with no score claims.",
            "Credit limit", "Current usage", "Opened", "Payment due", "Issuer / billing note",
            "VALUE", "Update usage", "Current usage"
        ),
        WorkspaceConfig(
            WorkspaceType.SHOPPING_LIST, "✓", "Lifestyle", "Shopping lists",
            "Plan purchases, tick items and post checked items as one expense.",
            "Estimated total", "Checked total", "Created", "Shopping date", "Store / list note",
            "ITEM", "Add list item", "Estimated price", supportsLedgerPost = true, showAccountAndCategory = true
        ),
        WorkspaceConfig(
            WorkspaceType.WARRANTY, "◇", "Lifestyle", "Warranties",
            "Keep product, receipt reference and expiry in one searchable place.",
            "Purchase price", "Covered value", "Purchased", "Warranty expires", "Serial / service note",
            null, null, "Amount"
        ),
        WorkspaceConfig(
            WorkspaceType.LOYALTY, "★", "Lifestyle", "Loyalty cards",
            "Non-sensitive membership references and locally tracked reward points.",
            "Target points", "Current points", "Joined", "Points expire", "Programme / member reference",
            "POINTS", "Add points", "Points", usesPoints = true
        ),
        WorkspaceConfig(
            WorkspaceType.SHARED_EXPENSE, "♧", "Lifestyle", "Shared expenses",
            "Track what a person or group owes and record settlements cleanly.",
            "Amount owed", "Settled", "Created", "Settle by", "Person / group details",
            "SETTLEMENT", "Record settlement", "Settlement"
        ),
        WorkspaceConfig(
            WorkspaceType.CURRENCY_RATE, "⇄", "Utilities", "Currency rates",
            "Manual timestamped reference rates that never rewrite stored transactions.",
            "Base amount", "Rate", "Recorded", "Review date", "Source / note",
            null, null, "Rate", usesRate = true
        )
    )

    fun forType(type: WorkspaceType): WorkspaceConfig = all.first { it.type == type }
}
