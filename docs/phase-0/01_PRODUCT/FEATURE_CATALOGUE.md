# Feature Catalogue

| ID | Feature | Module | Release | Access | Basis | Definition |
|---|---|---|---|---|---|---|
| ONB-001 | Guided first-run setup | Onboarding | P0 | Free | Review request | Explains accounts, first transaction and first budget with optional sample data. |
| ONB-002 | Setup checklist | Onboarding | P0 | Free | Derived | Dismissible checklist remains until essential setup is complete. |
| ACC-001 | Unlimited local accounts | Accounts | P0 | Free | Core | Cash, bank, wallet, credit card and custom types. |
| ACC-002 | Opening balance and adjustment history | Accounts | P0 | Free | Derived | Adjustments require reason and preserve audit trail. |
| TXN-001 | Income and expense records | Transactions | P0 | Free | Core | Fast entry with category, merchant, tags and note. |
| TXN-002 | Split transactions | Transactions | P0 | Free | Derived | One payment can be divided across categories. |
| TXN-003 | Undo deletion and edit history | Transactions | P0 | Free | Derived | Protects against accidental data loss. |
| TRF-001 | Account transfers | Transfers | P0 | Free | Review request | Atomic linked transfer that never counts as income or expense. |
| TRF-002 | Transfer fees and cross-currency received amount | Transfers | P0 | Free | Derived | Fee is recorded separately; received amount can differ. |
| REC-001 | Recurring transactions | Recurring | P0 | Free | Review request | Automatic or reminder-first schedules. |
| REC-002 | Month-end and missed-run recovery | Recurring | P0 | Free | Derived | Prevents skipped or duplicate monthly entries. |
| BUD-001 | Monthly budgets that persist across months | Budgets | P0 | Free | Review request | Each period is generated and calculated independently. |
| BUD-002 | Weekly, yearly and custom-period budgets | Budgets | P0 | Free | Review request | Custom start/end dates and repeat rules. |
| BUD-003 | Carry-over modes | Budgets | P0 | Free | Derived | Off, positive only, or full carry-over. |
| REP-001 | Six-month and yearly views | Reports | P0 | Free | Review request | Included in free core. |
| REP-002 | Custom date-range reports | Reports | P0 | Free | Review request | User chooses any inclusive range. |
| REP-003 | Day/week/month/quarter/all-time views | Reports | P0 | Free | Derived | Consistent period selector. |
| CAL-001 | Calendar date navigation | Navigation | P0 | Free | Review request | Jump directly to a date instead of tapping through every day. |
| SEA-001 | Advanced combined filters | Search | P0 | Free | Review request | Account, category, tag, amount, date, attachment and status. |
| SEA-002 | Saved filters | Search | P1 | Free | Derived | Store frequently used queries. |
| ATT-001 | Receipt and document attachments | Attachments | P0 | Free | Review request | Images, PDFs and documents attached to each transaction. |
| ATT-002 | Attachment integrity scan | Attachments | P0 | Free | Derived | Detects missing/corrupt files before backup. |
| BKP-001 | Complete local backup | Backup | P0 | Free | Review request | Records, settings and attachments in one package. |
| BKP-002 | User-selected cloud-drive folder via Android picker | Backup | P0 | Free | Review root cause | Allows recovery after uninstall without an app account or paid server. |
| BKP-003 | Encrypted backup | Backup | P0 | Free | Derived | AES-GCM package protected by user passphrase. |
| RST-001 | Validated restore with preview and rollback | Restore | P0 | Free | Review request | Never replaces current data until integrity checks pass. |
| EXP-001 | Reliable CSV export | Export | P0 | Free | Review request | Streaming export with completion report and failure recovery. |
| EXP-002 | Structured JSON export | Export | P0 | Free | Derived | Complete portable representation excluding binary files. |
| IMP-001 | CSV import template | Import | P0 | Free | Derived | Preview, field mapping and duplicate handling. |
| UI-001 | Simple stable interface | UX | P0 | Free | Review request | No feature regression or forced redesign without compatibility path. |
| UI-002 | Dark and light themes | UX | P0 | Free | Derived | Accessible contrast and system mode. |
| SEC-001 | Optional device-auth app lock | Security | P0 | Free | Derived | Biometric/PIN delegated to Android. |
| MON-001 | No forced ads in critical flows | Monetisation | P0 | Free | Cross-review lesson | No ads in add, edit, budget, backup or restore. |
| MON-002 | Core features never paywalled | Monetisation | P0 | Free | Cross-review lesson | History, filters, reports, backup and restore remain free. |
| PRO-001 | Advanced dashboard layouts | Pro | P1 | Lifetime Pro | Business decision | Convenience/customisation only. |
| PRO-002 | Advanced forecasting and comparison packs | Pro | P1 | Lifetime Pro | Business decision | Optional analytical depth, not basic history. |
| PRO-003 | Premium report themes and extra home widgets | Pro | P1 | Lifetime Pro | Business decision | Cosmetic/convenience value. |
| SYN-001 | Real-time multi-device sync | Cloud | P2 | Optional subscription | Future | Requires recurring infrastructure and privacy review. |
| FAM-001 | Shared household ledger | Cloud | P2 | Optional subscription | Future | Built only after sync foundation is sustainable. |

**P0:** mandatory v1. **P1:** post-launch enhancement. **P2:** cloud-era feature.
