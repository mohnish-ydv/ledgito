# Android Architecture

## Stack

- Language: Kotlin
- UI: XML layouts with Material 3 components and ViewBinding
- Architecture: single-activity navigation with feature fragments, MVVM, repositories and explicit domain use cases
- Database: Room/SQLite
- Background work: WorkManager
- Exact user reminders where justified: AlarmManager with graceful fallback
- Preferences: DataStore
- File access: Storage Access Framework and app-private vault
- Image loading: small maintained library only if necessary; otherwise platform APIs
- Dependency injection: manual constructor-based DI initially to reduce build complexity
- Build: Gradle wrapper and GitHub Actions
- Minimum Android: API 26
- Initial target: API 36, adjusted only when the build ecosystem requires a later stable target

## Modules

For phone-only maintenance, begin with one Android application module organised by strict packages rather than a complex multi-module Gradle graph:

- `core/database`
- `core/files`
- `core/backup`
- `core/time`
- `core/ui`
- `feature/onboarding`
- `feature/accounts`
- `feature/transactions`
- `feature/budgets`
- `feature/recurring`
- `feature/reports`
- `feature/settings`

Extract reusable library modules only after the first app is stable.

## Layers

- UI: renders state and emits user events
- ViewModel: orchestration and screen state
- Domain: money rules, transfers, periods, recurrence and backup contracts
- Data: Room DAOs, repositories, file vault and system providers

UI code must not calculate account balances or budget periods directly.

## Offline and network posture

Core v1 requires no internet. Avoid declaring `INTERNET` unless a later optional component genuinely needs it. This improves privacy clarity and removes ad/network temptation.

## Reliability patterns

- Database transactions for multi-table writes
- Idempotency keys for recurrence
- Temporary files plus checksum before final write
- Structured error codes
- Crash-safe work resumption
- No destructive Room migration fallback
- Time abstraction for deterministic tests

## GitHub Actions gates

- Debug compile
- Release compile
- Unit tests
- Android lint
- Database migration tests
- Backup round-trip tests
- APK artefact upload
- SHA-256 output
