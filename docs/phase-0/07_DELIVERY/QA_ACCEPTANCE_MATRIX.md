# QA Acceptance Matrix

## Review-mapped release blockers

### Receipts
- Attach image and PDF to a transaction.
- Reopen after process death.
- Include both in backup.
- Restore and verify SHA-256.

### Export
- Export 1, 1,000 and 20,000 transactions.
- Verify row counts and escaped notes.
- Cancel safely.
- Simulate destination write failure without corrupting prior export.

### Backup/restore
- Back up records, settings and attachments.
- Clear app data.
- Restore.
- Compare counts, balances, budget periods and checksums.
- Reject tampered package without changing current data.

### Reports
- Verify six-month, year and custom ranges across year boundaries and leap year.
- Transfers excluded from income/expense.
- Filtered totals equal visible rows.

### Transfers
- Create, edit and delete.
- Add fee.
- Cross-currency received amount.
- Crash simulation between writes leaves no half-transfer.

### Recurring
- Monthly dates 28, 29, 30 and 31.
- Leap-year February.
- Device restart.
- Missed run.
- Run-now twice does not duplicate.

### Budgets
- Continue through at least 14 periods.
- Custom period crossing month/year.
- Carry-over modes.
- Editing future definition leaves closed periods stable.

### Onboarding
- New user completes setup.
- Every step can be skipped.
- Sample data can be removed entirely.
- Restore path visible before setup.

## General tests

- Airplane-mode full workflow
- Rotation/process recreation on every editor
- 200% text scaling
- Dark mode contrast
- Notification denied/allowed flows
- Storage destination revoked
- Low disk space
- Database migration from every released version
- Time-zone change
- Locale decimal/date formats
- App update with 20,000 transactions and 500 attachments
