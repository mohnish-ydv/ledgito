# Goldmine Ledger — Five-Milestone Delivery Plan

This plan supersedes the earlier fine-grained Phase 0 draft. The five-milestone structure is designed for a phone-only Termux + GitHub Actions workflow.

## M1 — Foundation and core setup (this ZIP)

- Android project and GitHub Actions
- Material 3 navigation shell
- Room database and DataStore
- Onboarding
- Profile, account and category/subcategory CRUD
- Opening balances, default currency and themes
- Audit foundation and data invariants

## M2 — Complete finance engine

- Income and expense transactions
- Account-to-account transfers with linked atomic entries
- Splits, merchants/payees, notes and tags
- Search, sorting and advanced filters
- Recurring transactions
- Receipt/photo/PDF attachments
- Calendar navigation
- Budgets and all mandatory report ranges
- Dashboard and insights based on real ledger data

## M3 — Data ownership and complete product UX

- Versioned ZIP backup and validated restore
- Automatic local backups
- CSV export/import and human-readable export
- Reminder engine
- Settings, security controls and diagnostics
- Complete onboarding/help, empty/error states and accessibility
- No review-requested P0 feature left incomplete

## M4 — Production hardening

- Full regression and migration testing
- Large-data performance
- Notification reliability
- Lifecycle/process-death recovery
- Android 8–16 compatibility
- Battery, storage and APK optimisation
- Security/privacy audit and bug fixing

## M5 — Commercial release

- Final non-intrusive monetisation framework
- About, credits, licences and privacy policy
- Release signing workflow and reproducible release package
- Final UX polish
- Store assets/checklists and GitHub release
- Production v1.0 APK

## Acceptance rule

A milestone is complete only when its data behaviour, failure states and build workflow are delivered—not merely its screens.
