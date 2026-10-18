# GOLDEN 360 — Release QA Log

## Current automated verification

- UX PR [#13](https://github.com/morady-eg/GOLDEN365/pull/13) was merged into `main` as commit `af601ea3897818352e3c9815c17bfd283f7ffe91`.
- Android CI run [39080013929](https://github.com/morady-eg/GOLDEN365/actions/runs/39080013929) completed successfully for the UX changes.
- The run passed unit tests, built the debug and release variants, and uploaded the installable debug APK plus an unsigned release QA APK.
- PR [#18](https://github.com/morady-eg/GOLDEN365/pull/18) was merged as commit `5bcefec464beb072ceab561c63483d9f005c8dc0`. It fixes approved-payroll absence display to prefer the saved snapshot and aligns the Room `phone` default with its migration.
- Android CI runs [39083109690](https://github.com/morady-eg/GOLDEN365/actions/runs/39083109690) and [39083106197](https://github.com/morady-eg/GOLDEN365/actions/runs/39083106197) both completed successfully for commit `6fd06c622eee1929ffc87bfd282a34bad87814b0`, including unit tests and Debug/Release builds.
- The `golden1.png` asset is present at `android-app/app/src/main/res/drawable-nodpi/golden1.png` and is referenced by the app's main header. This corrects an outdated statement in the manual checklist below.
- Automated builds are not proof of successful physical-device testing or a production-ready signed release.

## Follow-up fixes merged and verified in CI

- PR [#19](https://github.com/morady-eg/GOLDEN365/pull/19) was merged into `main` as commit `59cf0b9562797db657685f9b4d4a824ad94f6b2b`.
- Android CI run [39083789897](https://github.com/morady-eg/GOLDEN365/actions/runs/39083789897) succeeded for the PR commit: unit tests, Debug/Release builds, and QA artifact uploads all passed.
- Backup creation now streams database files into a temporary ZIP and streams encryption to a temporary file; restore streams decryption and ZIP entries to bounded staging files to reduce intermediate heap usage.
- Streaming encryption/decryption tests cover large-payload round-trip and tampered ciphertext rejection. Device-level backup/restore validation remains required.

## Previously observed build issue

Run [39077382300](https://github.com/morady-eg/GOLDEN365/actions/runs/39077382300) failed on an unresolved `calculate` reference in payroll validation. That historical issue was corrected in a subsequent commit. Always verify the latest commit's CI before publishing.

## Manual release gates — still required

- [ ] Install and launch the debug APK on a physical Android device.
- [ ] Test employee creation/editing, employee list states, and ID card fallback imagery.
- [ ] Visually confirm `golden1.png` renders correctly on the target Android device, including transparent edges and scaling.
- [ ] Test attendance before, at, and after shift start; weekly paid days off; manual absence; suspension/resumption; termination; and approved-month lock.
- [ ] Test positive, zero, and negative payroll; individual approval; batch approval rejection; and immutability after approval.
- [ ] Export/open Arabic PDF and CSV reports; verify totals, formula-injection protection, logo, sharing, and Arabic text rendering.
- [ ] Print an employee ID card at 54 × 86 mm and scan the QR code with a real phone.
- [ ] Test upgrade/migration using a disposable copy of a real prior-version database.
- [ ] Create an encrypted backup, restore it on a test install, and verify both databases and representative records.
- [ ] Configure release-signing secrets, build a signed release APK, and verify its signature.
- [ ] Confirm the final commit—not an earlier commit—has passing CI and review the uploaded test report.

## Known limitations

- CI has not performed physical-device, real-printer, real-database migration, or backup/restore testing.
- The release workflow's unsigned APK is for QA only; production distribution requires signing configuration.
- The audit trail is local to the device and does not provide authenticated multi-user attribution or tamper resistance.
- Reports currently export Excel-compatible CSV, not native XLSX.
