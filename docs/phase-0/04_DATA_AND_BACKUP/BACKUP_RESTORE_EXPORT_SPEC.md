# Backup, Restore, Import and Export Specification

## Complete backup format

Extension: `.gledger`

Package contents before optional encryption:

- `manifest.json`
- `data.json`
- `attachments/<sha256>.<ext>`
- `checksums.json`
- `README.txt`

Manifest contains format version, app version, schema version, creation timestamp, profile metadata, record counts, currencies, attachment count and encryption status.

## Writing algorithm

1. Snapshot database in a consistent transaction.
2. Stream JSON and attachments to a temporary package.
3. Calculate SHA-256 for every component.
4. Write checksums and final manifest.
5. Verify the temporary package.
6. Copy to destination temporary name.
7. Flush and atomically rename where provider supports it.
8. Record success only after reopening and validating the destination file.

## Encryption

- AES-256-GCM
- Key derived from passphrase using a memory-hard or platform-approved KDF available in the implementation environment
- Random salt and nonce
- No password recovery
- Metadata exposure limited to format identifier and cryptographic parameters

## Restore algorithm

1. Open package read-only.
2. Detect format and encryption.
3. Validate checksums and supported schema.
4. Show preview and conflict summary.
5. Create safety backup of current profile when possible.
6. Restore into a temporary database/vault.
7. Run foreign-key, count and balance checks.
8. Swap temporary and active storage atomically.
9. Keep rollback package until successful launch.

## External destination

Use Android Storage Access Framework. The user grants a persisted URI permission to a chosen folder. This can be local storage, SD card or a compatible cloud provider shown by Android. Goldmine Ledger does not need the provider password or a proprietary server.

## CSV export

Transactions CSV fields:
`id,type,date,time,account,to_account,amount,currency,category,merchant,tags,status,note,attachment_count,recurrence_id`

Rules:
- UTF-8 with BOM optional setting for spreadsheet compatibility
- RFC 4180 quoting
- Locale-independent decimal point in machine exports
- Streaming row writer
- Transfer rows include both account columns
- Export summary includes successful row count and skipped/error count

## CSV import

- Preview first 50 rows
- Map columns
- Choose date and decimal formats
- Resolve accounts/categories before commit
- Duplicate strategies: skip exact, import anyway, or review
- Entire commit transactional

## Backup reminder logic

Set data-dirty flag after any record or attachment change. Remind only when dirty and the last verified backup exceeds the chosen interval. Never use fear-based wording.
