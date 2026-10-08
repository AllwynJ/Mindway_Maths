# Local verification record

Verified on **2026-10-08**, macOS / Apple Silicon, Android Studio JBR 25, Gradle 9.8.1, AGP 9.4.1, Android SDK 37.

Package migration follow-up: all 36 Kotlin files and the Room schema export now use `com.elsco.mindwaymaths`. A clean debug/release build, instrumentation APK compilation, all 13 unit tests and Android lint passed after migration. Generated APK metadata and merged manifest components were checked against the new package. Device instrumentation and backend results below are from the original implementation run; device tests were not rerun for this package-only migration.

| Check | Result |
| --- | --- |
| Debug APK compilation | Passed |
| Release APK compilation, R8 minification and resource shrinking | Passed; unsigned APK generated |
| Android lint | Passed with non-blocking warnings; no suppressed error baseline |
| JVM unit tests | **13 passed** |
| Android instrumentation, Pixel 9 AVD / Android 16 (API 36) | **13 passed** |
| Worker TypeScript check | Passed |
| Worker JWT / PDF gateway tests | **23 passed** |
| Wrangler production bundle dry-run | Passed; approximately 11.4 KiB gzip |
| Firebase Auth / Firestore emulator integration | **6 passed** |
| Content schema validation | Passed: 59 seed documents and two inactive media examples |
| Release package inspection | `com.elsco.mindwaymaths`, min SDK 26, target SDK 37, label Mindway Maths |
| Release App Check mapping inspection | Play Integrity present; debug App Check factory absent |
| Release typed-navigation enum | Kept through minification |

The instrumentation suite covers offline Room persistence, idempotent answers/tests, user isolation, bookmarks, local deletion, login states, practice feedback, result review, bottom navigation, profile deletion confirmation, PDF invalid-ID retry/no-export UI, and FLAG_SECURE on MainActivity. A shared-route restoration bug was found by navigation tests and fixed before the passing run.

Final artifacts:

- `app/build/outputs/apk/debug/app-debug.apk` — approximately 30.9 MiB, debug signed.
- `app/build/outputs/apk/release/app-release-unsigned.apk` — approximately 3.8 MiB, **not signed for distribution**.
- `app/build/outputs/mapping/release/mapping.txt` — release R8 mapping.
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`

The final manifest also removes optional biometric/fingerprint permissions inherited from Credential Manager's unused passkey functionality. Firebase/WorkManager contribute normal support permissions for messaging, boot scheduling and background work.

## Not verified by these local checks

No production Firebase configuration, Google OAuth identity, Cloudflare account/bucket, private PDF, or publisher-owned unlisted YouTube lesson was supplied. Consequently live Google account selection, deployed Firestore/App Check enforcement, Play Integrity verdicts, live R2 streaming and live YouTube embedding are **configuration-dependent acceptance tests**, not reported as passed. The app intentionally has no production authentication bypass.

Tests use a local RSA JWKS and R2 test binding for the Worker, Firebase emulators for rules/Auth, and test-only authentication for UI/offline repository tests. No paid infrastructure was deployed. No production signing key or server secret was created or embedded.

Current content/product boundaries are documented in README and ARCHITECTURE: English-only UI, sample rather than complete exam content, date-seeded daily practice, filtered previous-year practice rather than curated ordered papers, and no leaderboard. Publisher legal text is explicitly a placeholder. Complete the staging acceptance list in TESTING.md before public release.
