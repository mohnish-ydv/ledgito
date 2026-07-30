# Milestone 1 Acceptance Record

## Goal

Deliver the real Android foundation, persistent local database, onboarding and usable account/category CRUD without placeholders pretending to be finance features.

## Delivered

1. Kotlin + XML Material 3 project
2. Single-activity Navigation shell
3. Room v1 schema with foreign keys
4. DataStore settings
5. Repository boundary and transactional writes
6. Profile and default-category seeding
7. Account CRUD, archive/restore, opening balances
8. Category and subcategory CRUD
9. Audit-event foundation
10. Currency and appearance settings
11. Case-insensitive duplicate-name protection
12. Mixed-currency-safe dashboard totals
13. One-level category hierarchy guards
14. Unit-test, schema-invariant, lint and APK CI gates

## Honest scope boundary

Transactions, transfers, splits, tags, merchants, filters and the receipt/recurring systems are M2. M1 labels dashboard values as opening balances so it never presents fake transaction totals.

## Signing

The M1 personal/testing APK is signed with Android's debug certificate so GitHub can produce an installable file without storing a private key. Final release signing is M5.
