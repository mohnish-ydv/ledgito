# Milestone 2 — Complete Finance Engine

## Delivered

M2 converts the M1 account/category foundation into a usable finance application.

### Transactions
- Create and edit income, expenses and transfers
- Atomic account impact; transfers are never counted as income or expense
- Explicit received amount for cross-currency transfers
- Transfer fee charged once to the source account
- Pending entries excluded from balances, budgets and reports
- Split income/expense entries with exact-total validation
- Payee, notes, tags and attachments
- Duplicate transaction action
- Soft deletion with immediate undo
- Immutable revision summaries for create/edit/delete/restore events

### Recurring engine
- Daily, weekly, monthly and yearly intervals
- Stable calendar anchors after short months
- Last-valid-day or skip-invalid-month behaviour
- Automatic posting or review-first mode
- Pause, resume, skip and run now
- End date and remaining-occurrence limits
- Database-level duplicate occurrence protection
- Due catch-up processing on app start

### Budgets
- Weekly, monthly, yearly and custom windows
- Whole-spending or expense-category budgets
- Parent categories include subcategory splits
- Historical period rows remain stable across later months
- No carry-over, positive-only carry-over or full carry-over
- Transfers excluded; transfer fees count as spending

### Reports and navigation
- Day, week, month, quarter, six months, year, all time and custom range
- Income, expense, net, transfer volume, category, account and daily summaries
- Previous equal-length period comparison
- Calendar day view and date-specific quick add
- Search, sort and advanced filters
- Running balance when one account is selected

### Attachments
- Images and PDFs copied into app-private storage
- FileProvider share/open access only
- Attachment rollback on failed transaction save
- Per-file and per-transaction limits

## Explicitly not part of M2

The following remain M3 scope:
- ZIP backup and validated restore
- CSV/JSON import/export
- automatic local backup rotation
- Android notification delivery for reminder-first recurring rules
- app lock/security controls
- full accessibility/help/diagnostics pass

## Acceptance condition

GitHub Actions must pass source validation, migration tests, JVM tests, Android lint and release APK assembly before M2 is considered build-verified.
