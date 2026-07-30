# Ledgerly M3.3 Lint Final — QA Report

## M3.3 remaining lint blocker

- Parsed the complete uploaded GitHub Actions log: exactly one unique lint error.
- Added explicit orientation to both hidden dashboard LinearLayouts.
- Added repository-wide missing-orientation regression validation.
- Kept strict Android lint gates enabled.

## M3.2 lint regression fix

- GitHub M3.1 evidence: Kotlin compilation completed and `lintDebug` found 3 errors / 416 warnings.
- Corrected the three API-level source annotations rather than disabling lint.
- Added validator guards for API 27 navigation-bar attributes and API 31 data-extraction rules.
- `abortOnError = true` remains the authoritative CI gate.

## M3.1 compiler regression fix

- reproduced the reported `LedgerViewModel.kt:618` unresolved-reference failure from the uploaded source
- changed the injected `SettingsRepository` argument to `private val settings`
- added a source validator assertion that rejects any future constructor regression
- kept database schema and application package unchanged

## Delivery-environment checks passed

- 74 Android/manifest XML files parsed successfully
- 35 production Kotlin files and one JVM test source audited
- 28 ViewBinding-to-layout contracts checked
- application `R.id` references cross-checked against generated resource IDs
- duplicate layout IDs rejected
- no `INTERNET` permission
- FileProvider configuration retained for local receipt/PDF attachments
- no destructive Room migration fallback
- package ID remains `com.mohnishraj.goldmineledger`
- Room database remains version 2
- no `android.app.AlertDialog`, TODO, FIXME or `NotImplementedError` in production source
- custom `FlowRingView` and `SparklineView` compiled against generated Android graphics stubs
- roadmap command centre and full-height feature-preview sheet compiled against generated Fragment/RecyclerView/Material stubs
- redesigned budget adapter compiled against generated Android/UI/domain stubs
- SQLite v1→v2 migration and finance-invariant suite passed across 13 tables
- M3 JSON metadata parsed and validated
- no mixed-currency totals are silently combined in Home, Reports, Calendar or Budget overview

## Visual/UX coverage

1. Onboarding and first-account setup
2. Home balance command centre
3. Global amount privacy mode
4. Activity search, filters and redesigned transaction rows
5. Transaction create/edit surface with split, tags and attachments
6. Insights with custom ring and sparkline visuals
7. Accounts and categories
8. Budget studio and period navigation
9. Recurring money rhythm and due review
10. Money calendar and day activity
11. Settings, appearance and privacy
12. More command centre with search/group filters
13. Seventeen safe frontend roadmap previews in a dedicated full-height sheet
14. Light, dark and system appearance variants

## Data-safety regression coverage

- M1 profile/account/category data survives v1→v2 migration.
- Same-currency transfer charges the source once and credits the destination once.
- Cross-currency transfer preserves an explicit received amount.
- Pending/deleted entries never alter balances.
- Split totals remain tied to the parent transaction.
- Referenced accounts remain protected.
- Recurring rule/date duplicates remain rejected.
- Budget period history remains unique and stable.
- Existing working M2 features were not replaced by frontend-only mocks.

## GitHub Actions authoritative Android gates

The included workflow runs:

- repository validator
- SQLite schema/migration suite
- `testDebugUnitTest`
- `lintDebug`
- `assembleRelease`
- APK SHA-256 generation

## Manual APK smoke checklist

1. Install M2, create data, then install M3 over it and confirm all old data remains.
2. Switch System → Light → Dark and reopen the app.
3. Toggle Hide amounts and verify Home, Activity, Accounts, Insights, Budgets, Recurring and Calendar.
4. Add income, expense, same-currency transfer and cross-currency transfer.
5. Open split, tags, receipt/PDF and advanced filters.
6. Move between budget periods and inspect on-track/attention states.
7. Pause, resume, skip, run-now and post-due recurring rules.
8. Select several calendar dates and add entries to those exact dates.
9. Open every live tool from More.
10. Search/filter roadmap tools and open every visual preview.
11. Verify unfinished previews cannot write to the ledger.
12. Confirm Android app permissions show no network access.

## Environment limitation

This delivery container does not contain Android SDK/AGP dependency caches, so it cannot perform the real Android Gradle build locally. The source, XML, binding, schema and targeted Kotlin stub-compilation gates above passed; GitHub Actions remains the authoritative Android compiler/lint/APK gate. The reported Kotlin source blocker itself is fixed and now protected by the repository validator.
