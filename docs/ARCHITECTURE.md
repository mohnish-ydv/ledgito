# Ledgerly M2 Architecture

## Layers

- **Room/SQLite:** durable financial state in signed 64-bit minor units
- **DataStore:** onboarding, theme and default currency preferences
- **LedgerRepository:** only write gateway; enforces invariants inside Room transactions
- **LedgerViewModel:** combines database flows into screen-ready state
- **Fragments/dialogs:** rendering and user input only; no direct DAO writes
- **AttachmentStorage:** copies selected documents into app-private files and exposes them through FileProvider

## Database version 2

Tables:
1. profiles
2. accounts
3. categories
4. transactions
5. transaction_splits
6. transaction_revisions
7. tags
8. transaction_tag_cross_ref
9. attachments
10. recurring_rules
11. budgets
12. budget_periods
13. audit_events

## Financial invariants

- Floating-point values are never stored.
- Pending or soft-deleted transactions do not affect balances, budgets or reports.
- A transfer debits the source and credits the destination atomically.
- Cross-currency transfer credit uses the explicit destination amount/currency.
- Transfer fee is charged once to the source and treated as spending.
- Split totals must exactly equal the parent transaction amount.
- Transfers cannot carry categories or splits.
- Existing referenced accounts/categories cannot be hard-deleted.
- Archived entities remain valid for editing existing entries and existing recurring rules.
- Recurring rule/date pairs are unique at database level.
- Historical budget periods are materialised and unique by budget/start date.
- Mixed currencies are never silently added into one dashboard number.

## Migration policy

`MIGRATION_1_2` only adds M2 tables. It does not rewrite or delete M1 profile/account/category/audit rows. `fallbackToDestructiveMigration` is forbidden.

## Privacy posture

- No `INTERNET` permission
- No login or analytics SDK
- No broad external-storage permission
- Platform automatic backup disabled until the user-controlled M3 backup format exists
