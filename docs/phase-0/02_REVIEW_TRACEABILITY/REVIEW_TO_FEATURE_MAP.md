# Review-to-Feature Traceability

| Review ID | Problem | Evidence | Feature IDs | Resolution |
|---|---|---|---|---|
| REV-EXP-001 | Receipt upload missing | “still lacks of uploading receipts for each expenditure” | ATT-001, ATT-002 | Fully addressed in v1 |
| REV-EXP-002 | Export crashes and no backup | “Not being able to export or back-up the data... crashing... csv” | EXP-001, BKP-001, RST-001 | Fully addressed in v1 |
| REV-EXP-003 | Basic yearly/custom views, custom budgets and filters restricted | “yearly view and custom view... custom time period budgets or good filters” | REP-001, REP-002, BUD-002, SEA-001, MON-002 | Fully addressed in free v1 |
| REV-EXP-004 | Transfer removed after update | “removed the TRANSFER option” | TRF-001, UI-001 | Fully addressed; regression gate added |
| REV-EXP-005 | Three years of data lost after uninstall; wants account | “uninstalled app and my 3 year data... there should be atleast online account” | BKP-001, BKP-002, BKP-003, RST-001 | Root cause fully addressed without mandatory account/server |
| REV-EXP-006 | Recurring payments and slow date navigation | “recurring payment feature... toggle between days using calendar” | REC-001, REC-002, CAL-001 | Fully addressed in v1 |
| REV-EXP-007 | Six-month and one-year views removed/paywalled | “removed the option to see 6 months and 1 year’s expenses” | REP-001, MON-002, UI-001 | Fully addressed in free v1 |
| REV-EXP-008 | Beginner cannot configure app | “I can’t figure out how to set this up the way I need to” | ONB-001, ONB-002 | Fully addressed through guided setup |
| REV-EXP-009 | Budget becomes difficult after second month | “only good for a month record... after second... difficulty to record budget” | BUD-001, BUD-003 | Period engine and regression tests address it |
| REV-CROSS-001 | Apps become unusable after ads/subscription changes | Study-planner reviews describe free limits, subscription pressure and ad bloat | MON-001, MON-002, UI-001 | Applied as portfolio-level monetisation rule |

## Traceability rule

A review is not considered addressed merely because a similarly named button exists. Its acceptance tests must pass. For example, “backup” is addressed only when an attachment-inclusive backup can be restored after app data is cleared and the restored totals match the source.
