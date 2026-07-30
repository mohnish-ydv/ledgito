# Changelog

## 1.2.0 Professional Experience — Build Fix

- Fixed `LedgerViewModel.kt` compilation failure caused by the undefined `totalDays` reference.
- Reused the already calculated `elapsedDays` value so current-period no-spend analytics continue to exclude future dates.
- Made the volatility insight `when` expression exhaustive inside its nullable `let` block.
- Added deterministic source guards for both compiler regressions.
- Preserved package ID, Room v3, backup compatibility and app version code 10.

## 1.1.0 Full Suite

- Renamed the visible product to Ledgito while preserving package ID, Room v3 and old-backup compatibility.
- Restored and dock-integrated Quick Add with haptic feedback.
- Added Planning centre, Wealth centre and dashboard shortcuts.
- Added bills, EMIs, loans, liabilities, mutual funds, gold, fixed deposits, PPF, EPF and crypto workspaces.
- Added payment validation, optional ledger posting, goal progress and monthly repayment due-date advancement.
- Added structured weekly, monthly, quarterly and yearly subscription billing cycles across posting, agenda and forecast.
- Connected planning obligations to Calendar and the 90-day forecast.
- Added wealth allocation and recorded cost-basis performance.
- Added spending heatmap, savings rate, budget health, period comparison and a transparent on-device financial-planning score.
- Added accessibility descriptions and expanded deterministic release validation.
- Bumped app version to code 9 / `1.1.0-full-suite`.

## 1.0.1 UX Stability

- fixed blank Activity, Accounts, Categories, Budgets, Recurring, Calendar, Workspace and More lists by adding explicit RecyclerView layout managers
- replaced fragile tab restoration with a predictable root-navigation stack so Home always opens
- added Android 15 status bar, display cut-out and gesture/navigation bar inset handling
- integrated the central Quick Add button into the bottom dock
- fixed dashboard cash-flow ring clipping with a taller hero and square-safe drawing bounds
- redesigned onboarding step 1 as a calm introduction inspired by the supplied research screenshots without copying their branding or layout
- replaced typed opening-balance date entry with a calendar-only picker
- retained Room schema v3, package ID, existing user data and all 1.0 feature systems
- bumped app version to code 8 / `1.0.1-ux-stability`

## 0.3.3-m3.3-lint-final

- fixed the remaining Android lint blocker in `fragment_dashboard.xml`
- added explicit vertical orientation to the hidden legacy stats and dynamic audit containers
- added a repository-wide validator that rejects any `LinearLayout` missing `android:orientation`
- retained strict `abortOnError = true` lint enforcement
- preserved package ID, Room database version 2, M2 data compatibility, and the complete M3 UI
- bumped app version to code 6 / `0.3.3-m3.3-lint-final`

## 0.3.2-m3.2-lint-fix

- fixed all three API-level lint blockers reported by GitHub Actions
- corrected `windowLightNavigationBar` API targeting from API 26 to API 27 in day theme
- added the API 27 target annotation to the night theme navigation-bar icon mode
- explicitly marked the API 31 `dataExtractionRules` manifest attribute for lint compatibility
- retained strict `abortOnError = true`; lint is not bypassed or disabled
- preserved package ID, Room database version 2, M2 finance data and the complete M3 UI
- bumped app version to code 5 / `0.3.2-m3.2-lint-fix`

## 0.3.1-m3.1-stability

- fixed the GitHub `compileDebugKotlin` blocker in `LedgerViewModel` by retaining `SettingsRepository` as a private constructor property
- added a repository validation guard for the privacy-settings dependency
- preserved the complete M3 Obsidian Aurora UI, M2 finance engine, package ID and Room v2 database
- renamed the GitHub Actions APK/artifact for the M3.1 stability release

## 0.3.0-m3-ui

- introduced the original Obsidian Aurora light/dark design system
- rebuilt onboarding, home, activity, accounts, categories, insights, budgets, recurring, calendar and settings
- added central quick-add navigation
- added global hide-amounts privacy preference
- added custom dependency-free cash-flow ring and sparkline views
- added searchable Money Command Centre
- added 17 clearly labelled frontend roadmap previews with full-height concept sheets based on the supplied research pack
- redesigned transaction rows and transaction editor
- retained Room database version 2 and existing M1/M2 data compatibility
- retained zero INTERNET permission

## 0.2.0-m2

- complete finance engine, recurring rules, budgets, reports, calendar, attachments and migration 1 to 2

## 1.0.0 Final

- Replaced the advanced-tool roadmap with live planning, wealth, lifestyle and utility workspaces.
- Added planned payments, goals, debts, subscriptions, investments, assets, credit tracking, shopping lists, warranties, loyalty points, shared expenses and manual currency rates.
- Added net worth, 90-day cash-flow outlook and saved Activity views.
- Added auditable account balance adjustments, category merge and transaction duplication.
- Added Data Vault with standard/encrypted full backup, validated staged restore, CSV import/export/template, readable JSON export, attachment scan and diagnostics.
- Added optional phone-screen app lock and local daily due reminders.
- Migrated Room schema 2→3 without destructive fallback.
- Preserved the focused Home / Activity / Insights / More navigation and Obsidian Aurora theme.

## 1.2.0 — Professional Experience
- Fixed the centre Quick Add regression with a dock-constrained, OEM-safe implementation.
- Added Quick Add and Home customisation bottom sheets.
- Added swipe edit/delete with Undo to Activity.
- Added Dashboard 2.0 offline priority insight and cash-flow rhythm.
- Added Planning health and Snowball/Avalanche comparison.
- Added Wealth allocation donut and concentration notes.
- Added behaviour analytics, top payees and expanded offline insights.
