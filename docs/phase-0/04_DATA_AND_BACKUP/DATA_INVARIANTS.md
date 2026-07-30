# Financial and Data Invariants

1. A transfer changes account balances but not total income or total expense.
2. A transfer is saved or deleted atomically; half-transfers cannot exist.
3. Sum of transaction splits equals the parent transaction amount.
4. Every recurring occurrence key is unique.
5. Budget spending uses expenses only unless a user explicitly configures refund treatment.
6. Archived categories/accounts remain resolvable for historical records.
7. Editing a budget definition does not silently rewrite closed periods.
8. Restored record counts and attachment checksums match the backup manifest.
9. Export never mutates source data.
10. Failed import/restore leaves current data unchanged.
11. Monetary arithmetic uses integer minor units.
12. Report period boundaries use the user’s local calendar rules, not raw UTC days.
