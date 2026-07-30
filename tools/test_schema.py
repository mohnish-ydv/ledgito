#!/usr/bin/env python3
"""SQLite migration and finance-invariant tests for Ledgito 1.1.

The suite starts from the M1 schema, applies structural mirrors of
MIGRATION_1_2 and MIGRATION_2_3, then exercises the key persistence invariants
used by the final offline-first app.
"""
from __future__ import annotations

import sqlite3


def expect_integrity_error(action, message: str) -> None:
    try:
        action()
    except sqlite3.IntegrityError:
        return
    raise AssertionError(message)


con = sqlite3.connect(":memory:")
con.execute("PRAGMA foreign_keys=ON")

# M1 source schema: profiles, accounts, categories and audit events already exist.
con.executescript(
    """
CREATE TABLE profiles(
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    baseCurrency TEXT NOT NULL,
    localeTag TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);
CREATE TABLE accounts(
    id TEXT PRIMARY KEY NOT NULL,
    profileId TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name TEXT COLLATE NOCASE NOT NULL,
    type TEXT NOT NULL,
    currencyCode TEXT NOT NULL,
    openingBalanceMinor INTEGER NOT NULL,
    openingDate TEXT NOT NULL,
    includeInTotal INTEGER NOT NULL,
    isArchived INTEGER NOT NULL,
    sortOrder INTEGER NOT NULL,
    colourArgb INTEGER NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);
CREATE INDEX index_accounts_profileId ON accounts(profileId);
CREATE UNIQUE INDEX index_accounts_profileId_name ON accounts(profileId,name);
CREATE TABLE categories(
    id TEXT PRIMARY KEY NOT NULL,
    profileId TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    parentId TEXT REFERENCES categories(id) ON DELETE SET NULL,
    name TEXT COLLATE NOCASE NOT NULL,
    kind TEXT NOT NULL,
    iconKey TEXT NOT NULL,
    colourArgb INTEGER NOT NULL,
    isSystem INTEGER NOT NULL,
    isArchived INTEGER NOT NULL,
    sortOrder INTEGER NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);
CREATE INDEX index_categories_profileId ON categories(profileId);
CREATE INDEX index_categories_parentId ON categories(parentId);
CREATE UNIQUE INDEX index_categories_profileId_kind_name ON categories(profileId,kind,name);
CREATE TABLE audit_events(
    id TEXT PRIMARY KEY NOT NULL,
    profileId TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    entityType TEXT NOT NULL,
    entityId TEXT NOT NULL,
    action TEXT NOT NULL,
    timestamp INTEGER NOT NULL
);
CREATE INDEX index_audit_events_profileId_timestamp ON audit_events(profileId,timestamp);
"""
)

# Exact structural mirror of LedgerDatabase.MIGRATION_1_2.
con.executescript(
    """
CREATE TABLE transactions(
    id TEXT PRIMARY KEY NOT NULL,
    profileId TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    accountId TEXT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    destinationAccountId TEXT REFERENCES accounts(id) ON DELETE RESTRICT,
    categoryId TEXT REFERENCES categories(id) ON DELETE SET NULL,
    amountMinor INTEGER NOT NULL,
    currencyCode TEXT NOT NULL,
    destinationAmountMinor INTEGER NOT NULL,
    destinationCurrencyCode TEXT,
    transferFeeMinor INTEGER NOT NULL,
    transactionDate TEXT NOT NULL,
    payee TEXT NOT NULL,
    note TEXT NOT NULL,
    isCleared INTEGER NOT NULL,
    recurringRuleId TEXT,
    isDeleted INTEGER NOT NULL,
    deletedAt INTEGER,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);
CREATE INDEX index_transactions_profileId_transactionDate ON transactions(profileId,transactionDate);
CREATE INDEX index_transactions_accountId ON transactions(accountId);
CREATE INDEX index_transactions_destinationAccountId ON transactions(destinationAccountId);
CREATE INDEX index_transactions_categoryId ON transactions(categoryId);
CREATE INDEX index_transactions_createdAt ON transactions(createdAt);
CREATE UNIQUE INDEX index_transactions_recurringRuleId_transactionDate ON transactions(recurringRuleId,transactionDate);

CREATE TABLE transaction_splits(
    id TEXT PRIMARY KEY NOT NULL,
    transactionId TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    categoryId TEXT REFERENCES categories(id) ON DELETE SET NULL,
    amountMinor INTEGER NOT NULL,
    memo TEXT NOT NULL,
    sortOrder INTEGER NOT NULL
);
CREATE INDEX index_transaction_splits_transactionId ON transaction_splits(transactionId);
CREATE INDEX index_transaction_splits_categoryId ON transaction_splits(categoryId);

CREATE TABLE transaction_revisions(
    id TEXT PRIMARY KEY NOT NULL,
    transactionId TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    action TEXT NOT NULL,
    summary TEXT NOT NULL,
    timestamp INTEGER NOT NULL
);
CREATE INDEX index_transaction_revisions_transactionId_timestamp ON transaction_revisions(transactionId,timestamp);

CREATE TABLE tags(
    id TEXT PRIMARY KEY NOT NULL,
    profileId TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name TEXT COLLATE NOCASE NOT NULL,
    createdAt INTEGER NOT NULL
);
CREATE INDEX index_tags_profileId ON tags(profileId);
CREATE UNIQUE INDEX index_tags_profileId_name ON tags(profileId,name);

CREATE TABLE transaction_tag_cross_ref(
    transactionId TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    tagId TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY(transactionId,tagId)
);
CREATE INDEX index_transaction_tag_cross_ref_tagId ON transaction_tag_cross_ref(tagId);

CREATE TABLE attachments(
    id TEXT PRIMARY KEY NOT NULL,
    transactionId TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    displayName TEXT NOT NULL,
    mimeType TEXT NOT NULL,
    localPath TEXT NOT NULL,
    sizeBytes INTEGER NOT NULL,
    createdAt INTEGER NOT NULL
);
CREATE INDEX index_attachments_transactionId ON attachments(transactionId);

CREATE TABLE recurring_rules(
    id TEXT PRIMARY KEY NOT NULL,
    profileId TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    accountId TEXT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    destinationAccountId TEXT REFERENCES accounts(id) ON DELETE RESTRICT,
    categoryId TEXT REFERENCES categories(id) ON DELETE SET NULL,
    amountMinor INTEGER NOT NULL,
    currencyCode TEXT NOT NULL,
    destinationAmountMinor INTEGER NOT NULL,
    destinationCurrencyCode TEXT,
    transferFeeMinor INTEGER NOT NULL,
    payee TEXT NOT NULL,
    note TEXT NOT NULL,
    tagsCsv TEXT NOT NULL,
    frequency TEXT NOT NULL,
    intervalCount INTEGER NOT NULL,
    anchorDay INTEGER NOT NULL,
    monthEndMode TEXT NOT NULL,
    postingMode TEXT NOT NULL,
    occurrencesRemaining INTEGER,
    nextDueDate TEXT NOT NULL,
    endDate TEXT,
    isActive INTEGER NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);
CREATE INDEX index_recurring_rules_profileId_nextDueDate ON recurring_rules(profileId,nextDueDate);
CREATE INDEX index_recurring_rules_accountId ON recurring_rules(accountId);
CREATE INDEX index_recurring_rules_destinationAccountId ON recurring_rules(destinationAccountId);
CREATE INDEX index_recurring_rules_categoryId ON recurring_rules(categoryId);

CREATE TABLE budgets(
    id TEXT PRIMARY KEY NOT NULL,
    profileId TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name TEXT COLLATE NOCASE NOT NULL,
    categoryId TEXT REFERENCES categories(id) ON DELETE SET NULL,
    amountMinor INTEGER NOT NULL,
    currencyCode TEXT NOT NULL,
    periodType TEXT NOT NULL,
    anchorDate TEXT NOT NULL,
    customEndDate TEXT,
    repeatInterval INTEGER NOT NULL,
    repeatUntil TEXT,
    carryoverMode TEXT NOT NULL,
    isActive INTEGER NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);
CREATE INDEX index_budgets_profileId ON budgets(profileId);
CREATE INDEX index_budgets_categoryId ON budgets(categoryId);
CREATE UNIQUE INDEX index_budgets_profileId_name ON budgets(profileId,name);

CREATE TABLE budget_periods(
    id TEXT PRIMARY KEY NOT NULL,
    budgetId TEXT NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    periodStart TEXT NOT NULL,
    periodEnd TEXT NOT NULL,
    allocatedMinor INTEGER NOT NULL,
    carryInMinor INTEGER NOT NULL,
    createdAt INTEGER NOT NULL
);
CREATE INDEX index_budget_periods_budgetId ON budget_periods(budgetId);
CREATE UNIQUE INDEX index_budget_periods_budgetId_periodStart ON budget_periods(budgetId,periodStart);
"""
)

# Exact structural mirror of LedgerDatabase.MIGRATION_2_3.
con.executescript(
    """
CREATE TABLE workspace_items(
    id TEXT PRIMARY KEY NOT NULL,
    profileId TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    title TEXT COLLATE NOCASE NOT NULL,
    amountMinor INTEGER NOT NULL,
    currentMinor INTEGER NOT NULL,
    secondaryMinor INTEGER NOT NULL,
    currencyCode TEXT NOT NULL,
    secondaryCode TEXT NOT NULL,
    startDate TEXT,
    dueDate TEXT,
    accountId TEXT REFERENCES accounts(id) ON DELETE SET NULL,
    categoryId TEXT REFERENCES categories(id) ON DELETE SET NULL,
    linkedTransactionId TEXT REFERENCES transactions(id) ON DELETE SET NULL,
    status TEXT NOT NULL,
    note TEXT NOT NULL,
    metadata TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);
CREATE INDEX index_workspace_items_profileId ON workspace_items(profileId);
CREATE INDEX index_workspace_items_profileId_type ON workspace_items(profileId,type);
CREATE INDEX index_workspace_items_accountId ON workspace_items(accountId);
CREATE INDEX index_workspace_items_categoryId ON workspace_items(categoryId);
CREATE INDEX index_workspace_items_linkedTransactionId ON workspace_items(linkedTransactionId);

CREATE TABLE workspace_events(
    id TEXT PRIMARY KEY NOT NULL,
    itemId TEXT NOT NULL REFERENCES workspace_items(id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    label TEXT NOT NULL,
    amountMinor INTEGER NOT NULL,
    eventDate TEXT NOT NULL,
    isCompleted INTEGER NOT NULL,
    note TEXT NOT NULL,
    createdAt INTEGER NOT NULL
);
CREATE INDEX index_workspace_events_itemId ON workspace_events(itemId);
CREATE INDEX index_workspace_events_itemId_eventDate ON workspace_events(itemId,eventDate);

CREATE TABLE saved_filters(
    id TEXT PRIMARY KEY NOT NULL,
    profileId TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    name TEXT COLLATE NOCASE NOT NULL,
    query TEXT NOT NULL,
    type TEXT,
    accountId TEXT,
    categoryId TEXT,
    tagId TEXT,
    currencyCode TEXT,
    minMinor INTEGER,
    maxMinor INTEGER,
    hasAttachment INTEGER,
    cleared INTEGER,
    recurringOnly INTEGER,
    fromDate TEXT,
    toDate TEXT,
    sort TEXT NOT NULL,
    createdAt INTEGER NOT NULL
);
CREATE INDEX index_saved_filters_profileId ON saved_filters(profileId);
CREATE UNIQUE INDEX index_saved_filters_profileId_name ON saved_filters(profileId,name);
"""
)

# M1 records must still be available after migration.
con.execute("INSERT INTO profiles VALUES ('p','Mine','INR','en-IN',1,1)")
account_sql = "INSERT INTO accounts VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"
con.execute(account_sql, ('cash','p','Cash','CASH','INR',10000,'2026-07-01',1,0,0,-1,1,1))
con.execute(account_sql, ('bank','p','Bank','BANK','INR',50000,'2026-07-01',1,0,1,-1,1,1))
con.execute(account_sql, ('travel','p','Travel','WALLET','GBP',2500,'2026-07-01',1,0,2,-1,1,1))
category_sql = "INSERT INTO categories VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"
con.execute(category_sql, ('food','p',None,'Food','EXPENSE','food',-1,1,0,0,1,1))
con.execute(category_sql, ('dining','p','food','Dining out','EXPENSE','food',-1,0,0,1,1,1))
con.execute(category_sql, ('salary','p',None,'Salary','INCOME','salary',-1,1,0,0,1,1))
assert con.execute("SELECT COUNT(*) FROM accounts").fetchone()[0] == 3
assert con.execute("SELECT COUNT(*) FROM categories").fetchone()[0] == 3

# New transaction rows include destination amount/currency, fee and soft-delete state.
tx_sql = "INSERT INTO transactions VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
con.execute(tx_sql, ('expense','p','EXPENSE','cash',None,'food',2500,'INR',0,None,0,'2026-07-28','Groceries','',1,None,0,None,2,2))
con.execute(tx_sql, ('income','p','INCOME','bank',None,'salary',100000,'INR',0,None,0,'2026-07-28','Salary','',1,None,0,None,3,3))
con.execute(tx_sql, ('transfer','p','TRANSFER','bank','cash',None,10000,'INR',10000,'INR',250,'2026-07-28','','',1,None,0,None,4,4))
con.execute(tx_sql, ('fx','p','TRANSFER','bank','travel',None,11000,'INR',10000,'GBP',100,'2026-07-29','FX transfer','',1,None,0,None,5,5))
con.execute(tx_sql, ('pending','p','EXPENSE','cash',None,'food',9999,'INR',0,None,0,'2026-07-29','Pending','',0,None,0,None,6,6))
con.execute(tx_sql, ('deleted','p','INCOME','cash',None,'salary',90000,'INR',0,None,0,'2026-07-29','Deleted','',1,None,1,7,7,7))

# Mirror LedgerMath: pending/deleted rows never affect balances; fees charge source once.
balances = {'cash': 10000, 'bank': 50000, 'travel': 2500}
rows = con.execute("""
SELECT type,accountId,destinationAccountId,amountMinor,destinationAmountMinor,transferFeeMinor,isCleared,isDeleted
FROM transactions ORDER BY createdAt
""")
for typ, source, destination, amount, received, fee, cleared, deleted in rows:
    if not cleared or deleted:
        continue
    if typ == 'EXPENSE':
        balances[source] -= amount
    elif typ == 'INCOME':
        balances[source] += amount
    else:
        balances[source] -= amount + fee
        balances[destination] += received
assert balances == {'cash': 17500, 'bank': 128650, 'travel': 12500}, balances

# Split details must sum exactly to the transaction total (repository invariant).
con.execute(tx_sql, ('split','p','EXPENSE','cash',None,None,3000,'INR',0,None,0,'2026-07-30','Supermarket','',1,None,0,None,8,8))
split_sql = "INSERT INTO transaction_splits VALUES (?,?,?,?,?,?)"
con.execute(split_sql, ('s1','split','food',2000,'Food',0))
con.execute(split_sql, ('s2','split','dining',1000,'Cafe',1))
assert con.execute("SELECT SUM(amountMinor) FROM transaction_splits WHERE transactionId='split'").fetchone()[0] == 3000

# Revision history and recoverable delete state are retained with the transaction.
con.execute("INSERT INTO transaction_revisions VALUES ('rev1','split','CREATED','Created split expense',8)")
con.execute("UPDATE transactions SET isDeleted=1, deletedAt=9, updatedAt=9 WHERE id='split'")
con.execute("INSERT INTO transaction_revisions VALUES ('rev2','split','DELETED','Moved to recently deleted',9)")
assert con.execute("SELECT COUNT(*) FROM transaction_revisions WHERE transactionId='split'").fetchone()[0] == 2
assert con.execute("SELECT isDeleted,deletedAt FROM transactions WHERE id='split'").fetchone() == (1, 9)

# Tags are case-insensitively unique and links/attachments cascade only on purge.
con.execute("INSERT INTO tags VALUES ('tag','p','work',1)")
expect_integrity_error(
    lambda: con.execute("INSERT INTO tags VALUES ('tag2','p','WORK',2)"),
    "case-insensitive duplicate tag was accepted",
)
con.execute("INSERT INTO transaction_tag_cross_ref VALUES ('expense','tag')")
con.execute("INSERT INTO attachments VALUES ('att','expense','receipt.pdf','application/pdf','/private/receipt.pdf',12,1)")
con.execute("DELETE FROM transactions WHERE id='expense'")
assert con.execute("SELECT COUNT(*) FROM attachments").fetchone()[0] == 0
assert con.execute("SELECT COUNT(*) FROM transaction_tag_cross_ref").fetchone()[0] == 0

# A referenced account remains protected by RESTRICT.
expect_integrity_error(
    lambda: con.execute("DELETE FROM accounts WHERE id='bank'"),
    "referenced account deletion was accepted",
)

# Recurring rules preserve month-end behaviour, posting mode and occurrence limits.
recurring_sql = "INSERT INTO recurring_rules VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
con.execute(recurring_sql, (
    'rule','p','Rent','EXPENSE','bank',None,'food',25000,'INR',0,None,0,
    'Landlord','','home','MONTHLY',1,0,'LAST_VALID_DAY','AUTO',12,
    '2026-08-31',None,1,1,1
))
assert con.execute(
    "SELECT anchorDay,monthEndMode,postingMode,occurrencesRemaining,nextDueDate FROM recurring_rules WHERE id='rule'"
).fetchone() == (0, 'LAST_VALID_DAY', 'AUTO', 12, '2026-08-31')

# A recurring occurrence may exist only once per rule/date, even after soft deletion.
con.execute(tx_sql, ('occ1','p','EXPENSE','bank',None,'food',25000,'INR',0,None,0,'2026-08-31','Rent','',1,'rule',0,None,10,10))
expect_integrity_error(
    lambda: con.execute(tx_sql, ('occ2','p','EXPENSE','bank',None,'food',25000,'INR',0,None,0,'2026-08-31','Rent','',1,'rule',0,None,11,11)),
    "duplicate recurring occurrence was accepted",
)

# Budgets are case-insensitively unique; historical period snapshots are immutable rows.
budget_sql = "INSERT INTO budgets VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
con.execute(budget_sql, ('budget','p','Food plan','food',10000,'INR','MONTHLY','2026-07-01',None,1,None,'POSITIVE_ONLY',1,1,1))
expect_integrity_error(
    lambda: con.execute(budget_sql, ('budget2','p','food PLAN','food',12000,'INR','MONTHLY','2026-07-01',None,1,None,'OFF',1,1,1)),
    "case-insensitive duplicate budget name was accepted",
)
con.execute("INSERT INTO budget_periods VALUES ('bp1','budget','2026-07-01','2026-07-31',10000,0,1)")
con.execute("INSERT INTO budget_periods VALUES ('bp2','budget','2026-08-01','2026-08-31',10000,7500,2)")
expect_integrity_error(
    lambda: con.execute("INSERT INTO budget_periods VALUES ('bp3','budget','2026-08-01','2026-08-31',99999,0,3)"),
    "duplicate budget period was accepted",
)
assert con.execute("SELECT allocatedMinor,carryInMinor FROM budget_periods WHERE id='bp2'").fetchone() == (10000, 7500)

# Final workspaces keep manual planning/wealth/lifestyle records relational and local.
con.execute(account_sql, ('vault','p','Workspace vault','CUSTOM','INR',0,'2026-07-01',0,0,9,-1,1,1))
workspace_sql = "INSERT INTO workspace_items VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
con.execute(workspace_sql, (
    'goal','p','GOAL','Emergency fund',100000,25000,0,'INR','',
    '2026-07-01','2026-12-31','bank','food',None,'ACTIVE','Safety buffer','Personal',20,20
))
con.execute("INSERT INTO workspace_events VALUES ('goal-e1','goal','CONTRIBUTION','July contribution',25000,'2026-07-29',1,'',21)")
assert con.execute("SELECT SUM(amountMinor) FROM workspace_events WHERE itemId='goal'").fetchone()[0] == 25000

# Workspace links are resilient: account/category/transaction deletion uses SET NULL,
# while deleting the workspace itself removes its private event history.
con.execute(workspace_sql, (
    'planned','p','PLANNED_PAYMENT','Annual service',12000,0,0,'INR','',
    '2026-07-29','2026-08-15','vault','food','income','ACTIVE','Renewal','Yearly',22,22
))
con.execute("DELETE FROM transactions WHERE id='income'")
assert con.execute("SELECT linkedTransactionId FROM workspace_items WHERE id='planned'").fetchone()[0] is None
con.execute("DELETE FROM accounts WHERE id='vault'")
assert con.execute("SELECT accountId FROM workspace_items WHERE id='planned'").fetchone()[0] is None

# Saved views are named case-insensitively per profile.
saved_filter_sql = "INSERT INTO saved_filters VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
con.execute(saved_filter_sql, ('sf1','p','Large expenses','', 'EXPENSE',None,None,None,'INR',50000,None,None,1,None,None,None,'AMOUNT_HIGH',23))
expect_integrity_error(
    lambda: con.execute(saved_filter_sql, ('sf2','p','large EXPENSES','',None,None,None,None,None,None,None,None,None,None,None,None,'DATE_NEWEST',24)),
    "case-insensitive duplicate saved view was accepted",
)

# Category references in workspaces are nullable by design for long-term resilience.
con.execute("DELETE FROM categories WHERE id='food'")
assert con.execute("SELECT categoryId FROM workspace_items WHERE id='planned'").fetchone()[0] is None
assert con.execute("SELECT categoryId FROM workspace_items WHERE id='goal'").fetchone()[0] is None

con.execute("DELETE FROM workspace_items WHERE id='goal'")
assert con.execute("SELECT COUNT(*) FROM workspace_events WHERE itemId='goal'").fetchone()[0] == 0

# Parent category deletion uses SET NULL for resilience; repository blocks it while referenced.
assert con.execute("SELECT categoryId FROM budgets WHERE id='budget'").fetchone()[0] is None
assert con.execute("SELECT categoryId FROM recurring_rules WHERE id='rule'").fetchone()[0] is None
assert con.execute("SELECT categoryId FROM transaction_splits WHERE id='s1'").fetchone()[0] is None
assert con.execute("SELECT parentId FROM categories WHERE id='dining'").fetchone()[0] is None

# Purging a transaction cascades its split and revision history.
con.execute("DELETE FROM transactions WHERE id='split'")
assert con.execute("SELECT COUNT(*) FROM transaction_splits WHERE transactionId='split'").fetchone()[0] == 0
assert con.execute("SELECT COUNT(*) FROM transaction_revisions WHERE transactionId='split'").fetchone()[0] == 0

# Deleting a budget cascades only its generated snapshots.
con.execute("DELETE FROM budgets WHERE id='budget'")
assert con.execute("SELECT COUNT(*) FROM budget_periods").fetchone()[0] == 0
assert con.execute("SELECT COUNT(*) FROM transactions").fetchone()[0] > 0

# Schema inventory guards against accidentally omitting a final v3 table from either migration mirror.
expected_tables = {
    'profiles','accounts','categories','audit_events','transactions','transaction_splits',
    'transaction_revisions','tags','transaction_tag_cross_ref','attachments','recurring_rules',
    'budgets','budget_periods','workspace_items','workspace_events','saved_filters'
}
actual_tables = {
    row[0] for row in con.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")
}
assert actual_tables == expected_tables, (actual_tables, expected_tables)
assert list(con.execute("PRAGMA foreign_key_check")) == []

print("Ledgito 1.2 migration and finance invariant checks passed (16 tables, Room v3)")
