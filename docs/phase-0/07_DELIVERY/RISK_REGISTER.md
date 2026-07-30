# Risk Register

| Risk | Severity | Mitigation | Release gate |
|---|---|---|---|
| Data loss during update | Critical | Explicit migrations, pre-migration backup, migration tests | Yes |
| Corrupt/incomplete backup | Critical | Checksums, temp files, reopen verification, restore preview | Yes |
| Duplicate recurring entries | Critical | Deterministic idempotency key and unique constraint | Yes |
| Transfer counted as spending/income | Critical | Domain use case and report invariant tests | Yes |
| Budget breaks after month one | High | Separate definition and period tables; 14-period test | Yes |
| Cloud provider revokes folder access | High | Detect permission loss, notify, choose new destination | Yes |
| Large receipts inflate backup | Medium | Show size, compress images optionally, stream files | No |
| Feature creep delays first release | High | P0 scope-control rule | Yes |
| Public name conflict | Medium | Later availability/trademark check; listing name replaceable | Before store listing |
| Phone-only development hides device issues | High | CI, emulator tests where available, staged tester APKs | Yes |
| Monetisation harms trust | High | Free-core contract and no ads SDK in initial v1 | Yes |
