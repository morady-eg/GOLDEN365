# Security and data handling

## Local database encryption
- Employee and payroll Room databases use SQLCipher.
- Each database has a random passphrase; the passphrase is wrapped using AES-GCM with an Android Keystore key.
- Legacy plaintext databases are exported to a temporary encrypted database and the encrypted copy is verified before replacing the original.
- Key or migration errors fail closed; do not delete app data or reinstall during first launch after upgrading until the migration has been verified.
- `android:allowBackup="false"` remains enabled. This reduces backup exposure but does not replace a tested, authorized backup and recovery process.

## Audit trail limitations
The payroll database appends audit events for transaction creation, attendance changes, and payroll approval. Events are displayed by employee/month and do not include transaction notes or national IDs. The current app has no authenticated user identity, server-side audit copy, or hardware-backed anti-tamper log; the actor is recorded as `LOCAL_DEVICE`. Do not treat it as a legally tamper-proof audit trail.

## Negative net salary policy
Net salary is calculated without silently clamping a negative result to zero. A negative amount is retained and flagged for manual review before payment. The application does not automatically carry the balance into a later month or decide whether a deduction is lawful.

## Release checks
- Test SQLCipher migration and recovery using a copy of a real prior-version database.
- Confirm Android Keystore key availability after device restart and supported OS upgrades.
- Verify the audit trail and approved snapshots after app restart.
- Test QR scanning and actual printer scaling on target hardware.
- Keep dependencies and Gradle distribution checksums under review.
