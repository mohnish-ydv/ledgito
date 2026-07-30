# Security and Privacy

## Default posture

- No registration
- No ads SDK
- No analytics SDK
- No finance-data upload
- No contact, SMS, call-log or location permissions
- Camera requested only when the user captures a receipt
- Notifications requested contextually

## Local protection

- Android app sandbox protects database and vault
- Optional app lock uses device authentication
- Sensitive values are excluded from normal logs
- Diagnostic export contains counts and error codes, not transaction content, unless the user explicitly includes it

## Backup protection

- Unencrypted export is clearly labelled
- Encrypted `.gledger` backup available to every user
- Passphrase never stored in plaintext
- Restore preview avoids showing sensitive notes until authentication succeeds

## Privacy screen copy

“Goldmine Ledger stores your records on this device. The app does not require an account and does not send your financial records to our servers. Backups go only to the location you choose.”

## Threats considered

- Lost phone: device lock plus optional app lock
- Malicious backup replacement: checksum/authentication failure
- Partial file copy: temporary package and reopen verification
- Corrupt migration: pre-migration safety backup and tested migration
- Shoulder surfing: optional balance hiding and app lock
- Screenshot exposure: optional secure-screen setting for sensitive pages
