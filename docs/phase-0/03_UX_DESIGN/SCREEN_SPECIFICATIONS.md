# Screen Specifications

## S01 — Welcome

Purpose: explain offline ownership in under 20 seconds.  
Actions: Get started, Restore backup, Privacy details.  
Acceptance: restore is visible before creating a new profile.

## S02 — Setup wizard

Steps: currency/locale, first account, optional opening balance, first budget, backup folder, sample data.  
Acceptance: every step can be skipped and revisited.

## S03 — Home

Components:
- Total available balance
- Current-period income, expense and net flow
- Budget status strip
- Upcoming recurring items
- Recent transactions
- Quick-add actions

Rules:
- Hidden accounts excluded according to user preference.
- Transfer amounts excluded from income/expense cards.

## S04 — Add transaction chooser

Large choices: Expense, Income, Transfer.  
Long-press Add may use the last transaction type.

## S05 — Expense/Income editor

Fields: amount, account, category, date/time, merchant, note, tags, status, recurrence, attachments, splits.  
Rules: amount and account required; category can be “Uncategorised”; draft allowed.

## S06 — Transfer editor

Fields: from account, to account, sent amount, received amount if currencies differ, date/time, fee, note, attachments.  
Rules: source and destination cannot be identical; insufficient balance warns but does not block cash-style accounts unless user enables strict mode.

## S07 — Transaction detail

Shows full record, linked transfer, recurrence source, attachments and edit history.  
Actions: Edit, Duplicate, Share/export row, Delete with undo.

## S08 — Transactions ledger

Chronological groups, running balance option, search, filter chips and calendar jump.  
Acceptance: 20,000 transactions remain usable without loading all rows into memory.

## S09 — Filter builder

Date, account, type, category, tags, amount, status, attachments, merchant and recurrence.  
Actions: Apply, Save filter, Reset.

## S10 — Accounts

Cards show balance, type and latest activity.  
Actions: Add, reorder, hide, archive, adjust balance, open history.

## S11 — Budget list

Current budget cards grouped by period.  
Shows spent, remaining, projected pace and carry-over.

## S12 — Budget editor

Fields: name, scope, limit, period, repeat, start date, carry-over and alerts.  
Acceptance: custom periods support arbitrary start/end and repeated equivalents.

## S13 — Budget detail/history

Previous/current/next period navigation with transaction contributors.  
Historical periods do not recalculate incorrectly when the definition changes; changes apply from a selected effective period.

## S14 — Recurring list

Upcoming, paused and completed rules.  
Actions: create, pause, resume, skip next, run now.

## S15 — Recurring editor

Template transaction plus schedule, end rule and posting mode.  
Acceptance: monthly day 29–31 handling is explicitly selectable: last valid day or skip invalid month.

## S16 — Insights overview

Period selector: day, week, month, quarter, six months, year, all time, custom.  
Cards: income, expense, net flow, savings rate, category trend.

## S17 — Report detail

Chart plus accessible table.  
Actions: drill down, compare, export.  
No essential period range is paid.

## S18 — Calendar

Monthly calendar with daily net totals and activity dots.  
Tap date opens that day; long-press starts a transaction dated that day.

## S19 — Attachment viewer

Swipe between files, zoom images, open PDFs safely, show linked transaction.  
Actions: rename, share, detach, delete.

## S20 — Categories/tags

Manage defaults, create, merge, reorder and archive.  
Merge previews affected records.

## S21 — Backup centre

Shows last successful backup, destination, attachment count, package size and integrity state.  
Actions: Back up now, Change destination, Schedule, Verify, Restore.

## S22 — Restore preview

Shows package version, creation date, counts, currencies, attachments, conflicts and integrity result.  
Options: Replace current data or import as separate profile only where supported.  
Default is cancel-safe.

## S23 — Export centre

Choose CSV, JSON or full backup; scope; date range; included fields.  
Displays final location and share action.

## S24 — Settings

Sections: General, Appearance, Notifications, Security, Data, Diagnostics, About.

## S25 — Diagnostics

Database version, record counts, attachment scan, latest backup, notification permission and export logs.  
Contains Copy diagnostic report with no financial values by default.
