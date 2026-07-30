# Root-Cause Analysis

## Data loss

**Surface complaint:** data disappeared after uninstall.  
**Root cause:** records existed only in app-private storage and the user had no verified external backup.  
**Solution:** complete backup package, user-selected external destination, backup reminders, integrity checks and restore preview. An online account is not required to solve recovery.

## Export failure

**Surface complaint:** CSV export crashes.  
**Root cause:** likely memory-heavy export, weak error handling or unsafe file writing.  
**Solution:** streaming export, temporary file, checksum, atomic rename, visible progress, error log and automated round-trip tests.

## Broken long-range reports

**Surface complaint:** six-month/year/custom history removed or paid.  
**Root cause:** monetisation placed around an essential review workflow.  
**Solution:** all fundamental date ranges remain free and rely on one tested period-query engine.

## Multi-month budget failure

**Surface complaint:** app works for one month and becomes difficult later.  
**Root cause:** budget definition and budget-period instances were probably mixed.  
**Solution:** immutable budget definition plus generated period records with independent totals and carry-over state.

## Transfer regression

**Surface complaint:** transfer option removed.  
**Root cause:** product redesign ignored an accounting invariant.  
**Solution:** transfer is a first-class atomic domain operation and a release-blocking regression suite.

## Recurring duplicate risk

**Surface complaint:** recurring payment missing.  
**Root cause:** adding recurrence without idempotency can create duplicate money records.  
**Solution:** every generated occurrence has a deterministic unique key and transaction creation is idempotent.

## Beginner confusion

**Surface complaint:** cannot set up the app correctly.  
**Root cause:** blank-state UX assumes financial-app knowledge.  
**Solution:** guided first account, first transaction and first budget, with examples and reversible sample data.
