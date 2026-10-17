# GOLDEN 360 — Release QA Log

## Release policy

Do not merge PR #8 or publish a final release until the latest CI run passes and the manual device checks below are completed. A successful debug build or unsigned release APK is not a signed production release.

## Automated build history

| Run | Commit | Result | Finding / notes |
|---|---|---|---|
| [39076967842](https://github.com/morady-eg/GOLDEN365/actions/runs/39076967842) | `0ce19f18069e3300442c0d82313b8a441d81e78d` | Passed | Unit tests, debug APK, and unsigned release QA APK built successfully. |
| [39077382300](https://github.com/morady-eg/GOLDEN365/actions/runs/39077382300) | `b0be87208a5983b8440117df7967ed3a964f0328` | Failed | Kotlin compile error: `PayrollSummaryCalculator.kt:61:26 Unresolved reference 'calculate'`. The validator called the calculator without its object qualifier. |
| Pending latest run | `56de4bccf22285e691a980f9eb050acf80b138db` | Pending at log update | Fixed the unresolved reference by calling `PayrollSummaryCalculator.calculate(...)`. Re-run unit tests and both APK variants before proceeding. |

## Changes under verification

- Validate approved payroll snapshots in the data layer: valid employee/month, nonnegative components, and computed totals matching the ledger details.
- Block batch approval if the batch is empty, contains duplicate employees, mixes months, contains a negative net salary, or contains inconsistent snapshot totals.
- Preserve individual review for negative salary cases.
- Treat today's attendance as scheduled rather than automatically present when the saved shift-start time is invalid.
- Neutralize spreadsheet-formula text in CSV exports while preserving legitimate negative numeric amounts.
- Add unit tests for these guards and the CSV security behavior.

## Manual release gates — not yet verified

- [ ] Install and launch on a physical Android device.
- [ ] Test creating/editing an employee and confirm the default golden image and personal photo fallback on the employee list and ID card.
- [ ] Test attendance before/at/after shift start, weekly paid days off, manual absence, and approved-month lock.
- [ ] Test positive, zero, and negative payroll; individual approval; batch approval rejection; and immutability after approval.
- [ ] Export/open Arabic PDF and CSV reports; verify totals, formula-injection protection, logo, sharing, and Arabic text rendering.
- [ ] Print an employee ID card at 54 × 86 mm and scan the QR code with a real phone.
- [ ] Test upgrade/migration using a disposable copy of a real prior-version database.
- [ ] Create an encrypted backup, restore it on a test install, and verify both databases and representative records.
- [ ] Configure release signing secrets, build the signed release APK, and verify its signature.
- [ ] Review the final CI test report and confirm the latest commit—not an earlier commit—passed.

## Known limitations to disclose

- No physical-device, real-printer, real-database migration, or backup-restore test has been performed by CI.
- The release workflow requires GitHub Actions secrets for signing; CI's unsigned release APK is for QA only.
- The audit trail is local to the device and does not provide authenticated multi-user attribution or tamper resistance.
- Reports currently export Excel-compatible CSV, not native XLSX.
