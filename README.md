# GOLDEN 360 — Native Android Payroll & HR

GOLDEN 360 is being developed as a **native Android application written in Kotlin**. It is not an HTML/JavaScript app wrapped with Capacitor.

## Native Android stack
- Kotlin
- Jetpack Compose for native Android UI
- Material 3 components
- Room for on-device SQLite persistence
- Gradle Kotlin DSL
- JUnit for unit tests

## Current starter scope
The `android-app/` directory contains an initial Android Studio project scaffold:
- Arabic employee form with black-and-gold styling
- Employee data model and validation
- Room entity/DAO/database scaffold
- Sequential employee code formatter: `GS_000001`
- Unit tests for validation and money conversion

## Open in Android Studio
Open the `android-app` directory as the project. Use JDK 17 and Android SDK 35.

## Not finished yet
This is an early starter, not a production-ready payroll app. Employee form submission is not yet wired to persist data through Room; attachment buttons are placeholders; and employee listing/editing, advances, penalties, bonuses, overtime entries, absence deductions, payroll calculation, and reports remain to be implemented. The Android build and tests have not yet been run in this environment.

Employee personal data should not be committed to GitHub. The repository is for source code only.
