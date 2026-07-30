# Phase 0 Completion Certificate

## Status

**COMPLETE — READY FOR IMPLEMENTATION**

Phase 0 freezes the complete direction of Goldmine Ledger before source-code development begins.

## Frozen decisions

- One product is built first: Goldmine Ledger.
- Android is the first platform.
- The app is fully usable offline and without an account.
- Core records, accounts, budgets, reports, filters, transfers, recurring transactions, receipts, backup and restore are free.
- No forced ads, weekly credits or subscription wall may block core workflows.
- User-controlled backup can target local storage or any Android document provider exposed through the system picker, including supported cloud-drive providers.
- Monetary values are stored as integer minor units, never floating-point values.
- Destructive changes must support undo or explicit confirmation.
- Database migrations and backup round-trip tests are release blockers.
- Every directly requested expense-review feature is included in v1.

## Phase 0 deliverables

- Product identity and principles
- Target audience and jobs to be done
- Full v1 feature catalogue
- Review-to-feature traceability
- Information architecture
- Screen-by-screen specifications
- Critical UX flows
- Visual design system
- Database and file schema
- Backup, restore, import and export design
- Android architecture and dependencies
- Privacy and security model
- Monetisation rules
- Release scope and non-goals
- QA acceptance matrix
- Build milestones
- Risks and mitigations
- Machine-readable product data

## Development entry gate

Implementation may begin only from the decisions in this pack. A Phase 0 item can change later only through a documented decision-log entry explaining the reason, user impact, migration impact and test impact.
