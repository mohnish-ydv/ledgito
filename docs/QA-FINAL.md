# Ledgerly 1.0.1 UX Stability — QA Report

## Deterministic local gates

- XML parse for manifest and all resources
- JSON parse for completion metadata
- every `LinearLayout` has explicit orientation
- Kotlin brace/resource/ViewBinding/navigation cross-checks
- package/version/permission/lint configuration contracts
- Room v3 and both migration contracts
- feature implementation tokens and regression guards
- no destructive migration, unfinished token or internet permission
- SQLite migration/invariant execution across 16 tables
- ZIP integrity and source checksum verification
- Targeted Kotlin compiler checks for the core maths, Room declarations, repository, ViewModel, backup engine, reminder worker and newly added UI controllers


## UI/navigation regression gates

- every RecyclerView in production layouts declares a `LinearLayoutManager`
- all four bottom-navigation roots exist in both menu and navigation graph
- MainActivity contains the explicit Home/root-stack recovery path
- MainActivity and OnboardingActivity both apply status/navigation/display-cutout insets
- onboarding opening-date input is non-typable and connected to a calendar icon/picker
- dashboard hero has enough measured height for the cash-flow ring and the custom view draws in a centred square
- central Quick Add remains inside the bottom dock safe area

## SQLite scenarios exercised

- M1 records survive the M1→M2 schema expansion
- balances exclude pending/deleted records and apply transfer fees once
- split totals, revision history, tag uniqueness and cascades
- recurring occurrence uniqueness and budget-period uniqueness
- category `SET NULL`, account `RESTRICT`, transaction child cascades
- M2→M3 workspace tables, event cascades and nullable links
- case-insensitive saved-view uniqueness
- complete 16-table inventory and `PRAGMA foreign_key_check`

## Feature-lifecycle scenarios audited

- planned/subscription/shopping items cannot be posted twice after completion
- event-driven goals, debts, shopping lists, shared balances and loyalty targets recalculate after edits
- a completed event-driven item returns to Active when its target is no longer met
- archived workspace records remain archived during roll-up
- reminders are opt-in and remain off until the user enables them
- attachment restore is staged and checksum-verified before replacement

## Android authoritative gates

GitHub Actions runs:

```text
testDebugUnitTest
lintDebug
assembleRelease
```

The workflow prints the full text lint report on failure and only uploads the APK after all gates pass.

## Environment limitation

The local artifact environment did not provide the Android SDK/AGP dependency graph, so a local Android Gradle compile/lint/APK run was not claimed. The included GitHub Actions run is the authoritative Android build result.
