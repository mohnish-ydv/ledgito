# Ledgito 1.2 QA Gate

## Automated source gates

- Parse every XML and JSON file.
- Validate ViewBinding/resource IDs and RecyclerView LayoutManagers.
- Preserve package ID, Room v3 migrations and encrypted-backup contracts.
- Reject INTERNET permission and destructive migration.
- Verify the fixed dock-constrained Quick Add implementation.
- Verify custom Home DataStore preferences, Quick Add bottom sheet, swipe edit/delete/undo, planning-health, debt strategy comparison, allocation donut and behaviour analytics.
- Run existing schema/finance invariants, unit tests, Android lint and `assembleRelease` in GitHub Actions.

## Device regression checklist

1. Open each root tab repeatedly and return to Home.
2. Confirm the centre + button is visible in light/dark mode, gesture/three-button navigation and after returning from secondary screens.
3. Open every Quick Add action and dismiss each editor.
4. Customise Home, restart the app and verify visibility persists.
5. Swipe Activity left to edit; swipe right to delete; use Undo.
6. Verify plan-health and debt comparison with zero, one and multiple obligations.
7. Verify allocation donut with zero, one and multiple positive asset buckets.
8. Verify reports for day, week, month, year, all-time and custom ranges.
9. Toggle Hide amounts and confirm every new metric respects it where monetary.
10. Import a v1.1 backup and verify all existing records and attachments.

## Build-fix regression gate

- Reject any reintroduction of the undefined `totalDays` reference in report insights.
- Require no-spend insights to use `elapsedDays`.
- Require an exhaustive volatility `when` expression inside its nullable `let` block.
