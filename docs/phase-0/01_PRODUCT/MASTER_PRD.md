# Master Product Requirements Document

## 1. Product objective

Deliver a dependable Android personal-finance tracker that works offline, stores data locally, includes every essential workflow in the free core, and gives users verifiable control over backup and export.

## 2. Core product principles

1. **Offline first:** all normal tracking and reporting works without internet.
2. **Data ownership:** export and restore are core functions, not premium features.
3. **No feature ransom:** updates cannot silently remove or paywall existing core workflows.
4. **Financial correctness:** transfers, split transactions, recurring rules and budget periods must not distort totals.
5. **Progressive disclosure:** beginners receive guidance; experienced users get fast entry and advanced filters.
6. **Safe change:** migrations, imports and restore operations are transactional and reversible where practical.
7. **No server dependency in v1:** the app remains viable at £0 recurring infrastructure cost.

## 3. v1 modules

### Onboarding and setup
- Welcome and privacy promise
- Currency/locale selection
- Optional sample data
- Guided creation of first account and first budget
- Explanation of income, expense and transfer
- Optional backup-folder setup
- Skip and revisit onboarding

### Accounts
- Cash, bank, wallet, credit card and custom account types
- Opening balance and as-of date
- Account colour/icon
- Active, hidden and archived states
- Manual balance adjustment with audit note
- Account-specific transaction history
- Included/excluded from net worth

### Transactions
- Expense, income and transfer
- Draft, scheduled, cleared and void states
- Date and time
- Amount, currency, account, category, merchant/payee, note and tags
- Split categories
- Receipt or document attachments
- Duplicate transaction
- Edit history and undo for deletion
- Fast-add presets

### Transfers
- Source and destination accounts
- Transfer fee as optional expense
- Linked atomic record so edits stay consistent
- No effect on income/expense totals
- Cross-currency transfer with manually entered received amount

### Categories, tags and merchants
- Editable default categories
- User categories and subcategories
- Category icons and colours
- Unlimited tags
- Merchant suggestions from local history
- Merge categories without data loss

### Budgets
- Monthly, weekly, yearly and custom-period budgets
- Category, group or total-spending scope
- Recurring period generation
- Carry-over off, positive only, or full balance
- Progress independent across periods
- Previous and next period navigation
- Overspend alerts
- Budget history remains stable after later edits

### Recurring transactions
- Daily, weekly, monthly, yearly and custom intervals
- Start/end conditions and occurrence count
- Month-end handling
- “Create automatically” or “remind me first” modes
- Skip, edit one occurrence, or edit future occurrences
- Duplicate-prevention token
- Missed-run recovery

### Search and filters
- Search note, merchant, category, tag and amount
- Filter by date range, account, type, category, tag, status, amount range, attachment and recurrence
- Combine multiple filters
- Save named filters
- Clear-all control and visible active-filter chips

### Reports and insights
- Day, week, month, quarter, six-month, year, all-time and custom range
- Income, expenses, transfers and net flow
- Category and account breakdowns
- Trend comparison with previous equivalent period
- Calendar heat map and daily totals
- Export current report
- All basic ranges free

### Receipts and attachment vault
- Attach camera photo, image, PDF or document
- Multiple attachments per transaction
- Full-screen preview
- Rename, replace, detach and delete
- Local private storage by default
- Included in complete backup
- Missing-file detection and repair report
- OCR is not required for v1

### Backup, restore, import and export
- One-tap complete backup
- User-selected local or document-provider destination
- Optional scheduled backup to a persisted user-selected folder
- Encrypted `.gledger` backup option
- Restore preview before replacement
- Integrity validation and rollback on failure
- CSV export for transactions, accounts, categories and budgets
- JSON export for complete structured data
- Attachment-inclusive full backup
- Import from Goldmine Ledger CSV template
- Round-trip verification report

### Notifications
- Recurring transaction reminders
- Budget thresholds
- Backup overdue reminder
- Notification channels and quiet hours
- No promotional notifications by default

### Security and privacy
- No account required
- No network permission required for core v1 build
- Optional app lock using device authentication
- Private app storage for records and attachments
- Encrypted backup using user passphrase
- Clear-data and delete-all flow with typed confirmation

### Settings
- Currency and number format
- First day of week and budget month
- Theme and text size
- Default account/category
- Backup destination and schedule
- Notification controls
- Export, restore and diagnostics
- About, changelog, licences, privacy and developer credit

## 4. v1 exclusions

- Bank account aggregation
- Automatic bank SMS parsing
- Investment or tax advice
- Loans marketplace
- Crypto tracking
- AI categorisation
- Shared real-time household editing
- Web dashboard
- Server-side accounts
- Receipt OCR

These are exclusions to protect correctness and zero-cost operation, not missing requirements from the uploaded reviews.

## 5. Release acceptance

No v1 release while any of the following is broken:
- Transfer totals
- Recurring duplicate prevention
- Multi-month budget progression
- Complete backup/restore
- CSV export
- Six-month/year/custom-range reports
- Receipt retention
- Upgrade migration
