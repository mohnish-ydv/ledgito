# Ledgerly 1.0 Final — Release Specification

## Product goal

Ship a complete, private finance manager without turning the app into a wall of features. Four root destinations remain: **Home, Activity, Insights and More**, with a central quick-add action. Advanced tools are searchable and grouped behind More.

## Final architecture

- Kotlin / Android Views / ViewBinding
- Single-activity Navigation Component shell
- Room v3, explicit migrations, WAL journal mode
- DataStore for privacy/theme/reminder settings
- WorkManager for local daily reminders
- App-private attachment vault exposed only through FileProvider
- No internet permission, account system, ads or tracking

## Live capability map

### Ledger engine
Accounts, categories, expense/income/transfer entry, destination amount for FX transfers, fees, splits, tags, local receipt attachments, notes, cleared state, revision history, duplicate, soft delete and restore.

### Control and analysis
Recurring rules, budget periods/carry-over, calendar, advanced filters, saved views, reports, balance summaries, custom chart views and privacy masking.

### Planning
Planned payments, savings goals, debt payoff, subscriptions and a 90-day cash-flow outlook. Event-driven targets recalculate from their history and automatically reopen when an edited target is no longer complete.

### Wealth
Manual investments, manual assets, credit utilisation and a transparent net-worth summary. Only values in the profile base currency are included; no hidden conversion is performed.

### Lifestyle
Shopping lists, warranties, loyalty points and shared-expense settlements.

### Utilities
Manual currency references, encrypted/standard backup, validated restore, CSV import/export, JSON export, attachment health scan and diagnostics.

## Standout additions

- **Auditable balance correction:** a balance adjustment creates a real cleared income/expense record with a reason rather than rewriting history.
- **Category merge:** references are moved safely and the source category is archived.
- **Saved views:** reusable Activity filters avoid repeated setup.
- **Restore staging:** a backup is decrypted, path-checked, checksum-verified and inspected before replacement is scheduled.
- **Manual-first honesty:** investment, credit and currency tools avoid unsupported live-data or score claims.
- **Lifecycle-safe workspaces:** posting is status-guarded, activity roll-ups are recalculated after edits, and archived records are never silently reactivated.

## Deliberate exclusions

Bank sync, cloud family spaces and live exchange rates are not faked. They require network services, authentication, compliance and operating costs, conflicting with the current zero-cost/offline-first scope. Ledgerly provides working manual and portable alternatives instead.

## Upgrade safety

Package ID remains `com.mohnishraj.goldmineledger`. Room v3 adds `workspace_items`, `workspace_events` and `saved_filters`; prior 13 tables are preserved through `MIGRATION_2_3`.
