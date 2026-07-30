# Ledgito 1.2 Professional Experience

Ledgito is a private, offline-first Android personal-finance manager. Version 1.2 builds a professional daily experience on top of the complete accounts, planning, wealth, analytics and backup foundation. Financial records stay on the device: the app requests no internet permission and uses no ads or analytics.

## Build-fix revision

This package fixes the GitHub Actions Kotlin compilation blockers reported in `LedgerViewModel.kt`:

- the no-spend insight now uses the existing `elapsedDays` metric instead of the undefined `totalDays` symbol;
- the volatility insight `when` expression now has an explicit neutral branch, making it exhaustive inside the nullable `let` block;
- deterministic validation now rejects both regressions before Gradle runs.

The product version remains `1.2.0-professional-experience` because the previous source package did not produce a successful APK.

## What changed in 1.2

### Professional UX
- Definitive centre Quick Add fix: the button is constrained inside the dock, protected from OEM clipping/elevation quirks and restored whenever a root tab is visible.
- Material Quick Add bottom sheet with Expense, Income, Transfer, Bill, Goal and Investment actions.
- Customisable Home sections persisted locally in DataStore.
- Swipe right to delete with Undo and swipe left to edit in Activity.
- Haptic confirmation, screen motion and accessibility descriptions for custom charts.

### Dashboard 2.0
- Priority-aware private insight card.
- 30-day cash-flow rhythm.
- User-controlled Monthly pulse, Budget pulse, Planning & wealth, Recent activity and Planned money sections.

### Planning Centre
- Plan-health score based on recorded dates, commitments, goals and balances.
- Due-soon and overdue explanations.
- Snowball and recorded-APR Avalanche debt-order comparison, clearly labelled as organisational rather than financial advice.

### Wealth Centre
- Accessible allocation donut.
- Concentration/diversification notes derived from locally recorded value.
- Existing net-worth, manual performance and valuation-history tools retained.

### Analytics Pro
- Average daily spending, transaction count, no-spend days, spending variation and income-stability pattern.
- Top-payee analysis alongside category, account and daily breakdowns.
- Expanded private insights for spending volatility and no-spend days.

## Complete finance foundation
- Accounts, opening balances, transfers, splits, tags, notes, attachments and transaction revisions.
- Search, advanced filters, saved views, duplicate, soft delete and restore.
- Recurring money, review-before-post, budgets, calendar and reports.
- Bills, EMIs, loans, debt, goals, subscriptions, planned payments and 90-day forecast.
- Investments, mutual funds, gold, FD, PPF, EPF, crypto, assets, liabilities and credit utilisation.
- Standard and AES-256-GCM encrypted full backup, staged restore, CSV/JSON portability, attachment integrity scan and diagnostics.
- Optional device-lock authentication, hide-amounts mode and local reminders.

## Privacy and compatibility
- Room is the source of truth.
- `android.permission.INTERNET` is absent.
- Android platform auto-backup is disabled; Ledgito provides explicit portable backups.
- Package ID remains `com.mohnishraj.goldmineledger` to preserve installed data.
- Room remains v3 with explicit 1→2 and 2→3 migrations.
- The internal backup-format identifier remains `ledgerly-backup` so older backups continue to restore.
- Google OAuth, Drive backup and Supabase are intentionally deferred to the cloud/release milestone.

## Build

GitHub Actions runs deterministic source/schema validation, unit tests, Android lint and `assembleRelease`.

```text
Artifact: Ledgito-v1.2.0-Professional-Experience-APK
APK:      Ledgito-v1.2.0-Professional-Experience.apk
Package:  com.mohnishraj.goldmineledger
Version:  10 / 1.2.0-professional-experience
Room:     v3 with explicit 1→2 and 2→3 migrations
```

Local preflight:

```bash
python3 tools/validate_project.py
python3 tools/test_schema.py
```

The release APK still uses the existing debug signing configuration for installable testing. Permanent release signing belongs to the final deployment milestone.
