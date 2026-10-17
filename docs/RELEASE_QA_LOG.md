# GOLDEN 360 — Release QA Log

## Current automated verification

- UX PR [#13](https://github.com/morady-eg/GOLDEN365/pull/13) was merged into `main` as commit `af601ea3897818352e3c9815c17bfd283f7ffe91`.
- Android CI run [39080013929](https://github.com/morady-eg/GOLDEN365/actions/runs/39080013929) completed successfully for the UX changes.
- The run passed unit tests, built the debug and release variants, and uploaded the installable debug APK plus an unsigned release QA APK.
- This is not proof of a signed production release or successful physical-device testing.

## Previously observed build issue

Run [39077382300](https://github.com/morady-eg/GOLDEN365/actions/runs/39077382300) failed on an unresolved `calculate` reference in payroll validation. That historical issue was corrected in a subsequent commit. Always verify the latest commit's CI before publishing.

## Manual release gates — still required

- [ ] Install and launch the debug APK on a physical Android device.
- [ ] Test employee creation/editing, employee list states, and ID card fallback imagery.
- [ ] Confirm the requested transparent `golden1.png` brand asset is present and displayed in the app. It was not found in the repository during the latest review and must be supplied before integrating that exact asset.
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
