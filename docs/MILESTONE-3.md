# Ledgerly M3 - Visual Intelligence

M3 is a deliberate visual and information-architecture milestone. It does not pretend that every reference feature is production-ready.

## Primary result

Ledgerly now uses the original **Obsidian Aurora** design system:

- violet depth for primary actions and financial focus
- mint for healthy inflow and positive progress
- rose for spending and risk
- warm amber for attention states
- calm layered surfaces instead of flat default Material screens
- a consistent 12/18/22/30dp radius hierarchy
- a central quick-add action with four stable root destinations

## Redesigned flows

- onboarding
- home command centre
- activity and transaction rows
- transaction create/edit dialog
- accounts
- categories
- insights and reports
- budget studio
- recurring money rhythm
- money calendar
- settings and privacy controls
- bottom navigation
- feature discovery / roadmap centre

## New live UI behaviour

- global hide-amounts preference backed by DataStore
- privacy masking on Home, Activity, Accounts, Insights, Budgets, Recurring and Calendar
- custom cash-flow ring and sparkline views without chart dependencies
- grouped feature discovery with search and filters
- root navigation reduced to Home, Activity, Insights and More
- existing tools remain reachable through the More command centre

## Reference features represented safely

The roadmap centre exposes frontend previews for:

- planned payments
- savings goals
- debt payoff
- subscriptions
- investments
- assets and net worth
- cash-flow outlook
- credit health
- shopping lists
- warranties
- loyalty cards
- shared expenses
- bank sync
- currency rates
- secure cloud backup
- family space
- Ledgerly Plus

Each unfinished item is visibly labelled as a visual preview and opens a dedicated full-height concept sheet with its intended purpose, product principles and data-safety boundary. No fake persistence, silent networking or half-built finance logic was added.

## Data safety

- package ID remains `com.mohnishraj.goldmineledger`
- Room schema remains version 2
- existing M1/M2 data remains compatible
- no INTERNET permission
- platform auto-backup remains disabled
