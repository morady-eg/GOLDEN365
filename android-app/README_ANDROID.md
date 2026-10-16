# GOLDEN 360 — Android starter

This folder contains an initial native Android app scaffold using Kotlin, Jetpack Compose, and Room.

## Included in this starter
- Arabic dark/black and gold employee form UI.
- Employee model and basic validation.
- Room database/DAO scaffold with a unique employee code index and sequential `GS_000001` codes.
- Unit tests for validation and code formatting.

## Open the project
Open the `android-app` directory in Android Studio. Use JDK 17 and Android SDK 35.

## Current limitations
- This is an early scaffold, not a finished payroll application.
- The form is not yet wired to persist employees through Room.
- Attachment controls are placeholders; image picking and durable URI permissions are not implemented.
- Employee listing/editing, advances, penalties, bonuses, overtime entries, absence calculations, and payroll reports still need implementation.
- Android build and tests have not been run in this environment.
