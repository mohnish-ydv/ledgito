# Decision Log

## D-001 — Build expense tracker first

Reason: strongest direct review evidence and highest reusable-core value.

## D-002 — Development name Goldmine Ledger

Reason: consistent portfolio family. Public name availability must be checked before store listing.

## D-003 — No mandatory account

Reason: offline reliability, privacy and zero recurring infrastructure cost.

## D-004 — User-controlled external backup instead of v1 cloud account

Reason: solves uninstall/device-recovery root cause through Android document providers without holding user data on developer servers.

## D-005 — All review-requested expense features are P0

Reason: the product exists specifically to solve those complaints; moving them to future scope would invalidate the research.

## D-006 — Core features remain free

Reason: cross-app reviews identify paywall betrayal and ad bloat as major pain.

## D-007 — Kotlin + XML + Room

Reason: native reliability, mature tooling and manageable GitHub Actions builds for phone-only development.

## D-008 — No OCR, bank sync or AI in v1

Reason: not required by direct expense reviews and increases cost, permission scope and failure modes.

## D-009 — One app module initially

Reason: reduces Gradle complexity. Code boundaries preserve later extraction into Goldmine Core.

## Change procedure

Any change needs: decision ID, reason, affected feature IDs, data migration impact, UX impact, tests added/updated and rollback plan.
