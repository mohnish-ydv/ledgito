# Privacy, Backup and Restore

## Local by default

Ledgerly requests no internet permission. Due reminders are opt-in and use local WorkManager scheduling only. Room data, DataStore preferences and copied attachments remain in app-private storage. Android platform auto-backup is disabled to prevent unclear restore semantics.

## App lock

The optional lock delegates authentication to Android's existing secure screen lock. Ledgerly does not store a PIN, pattern or biometric template.

## Full backup

A backup contains:

- `goldmine_ledger.db`
- local attachment files
- Ledgerly DataStore preferences when present
- a JSON manifest with format/database versions and SHA-256 checksums

Standard backups are ZIP-compatible. Encrypted backups use PBKDF2-HMAC-SHA256 with a random salt and 120,000 iterations, then AES-256-GCM with a random IV. The passphrase is never persisted and is cleared from temporary character arrays after use.

## Restore safety

Before a restore can be committed, Ledgerly:

1. detects whether the file is encrypted;
2. decrypts to cache when required;
3. blocks ZIP path traversal;
4. validates each manifest checksum;
5. opens the database read-only and confirms a supported schema;
6. shows profile/account/transaction/attachment counts;
7. stages replacement for the next cold launch;
8. keeps a local rollback copy during replacement.

## Portable exports

CSV is intended for transaction interchange and validates rows independently. Readable JSON is a broad export for auditing and future migration. Neither format is presented as an encrypted full backup.
