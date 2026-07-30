# Ledgito 1.1 Full Suite — QA Record

## Deterministic gates

- All Android resource XML and the manifest parse successfully.
- Every RecyclerView declares a layout manager.
- Kotlin source brace balance and syntax-parser checks pass.
- Every referenced `R.id` exists and each imported ViewBinding has a matching layout.
- Navigation contains all four roots plus Planning, Wealth, Forecast, Net Worth and Data tools.
- Room remains v3 with explicit 1→2 and 2→3 migrations and no destructive fallback.
- Schema/invariant test covers all 16 tables.
- Manifest contains no INTERNET permission and disables platform auto-backup.
- Backup encryption contracts retain PBKDF2-HMAC-SHA256 and AES-GCM.
- GitHub Actions runs source validation, schema checks, unit tests, lint and release assembly.

## Regression coverage

- Quick Add is visible and dock-integrated.
- Home root navigation remains recoverable after secondary screens.
- Dashboard cash-flow ring uses square-safe bounds.
- Android 15 top/bottom insets remain applied.
- Onboarding and workspace dates are picker-driven.
- Shopping lists expose both item entry and checked-item ledger posting.
- Payment events reject values above the remaining balance.
- Hide Amounts propagates through Planning, Wealth, Calendar and Insights.
- Forecast respects recurring end/occurrence limits and planning obligations.

## Build note

This source package was validated without a local Android SDK/Gradle distribution. The included GitHub Actions workflow is the authoritative compile, unit-test, lint and APK assembly gate.
