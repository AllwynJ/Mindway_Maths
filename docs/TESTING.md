# Verification

## Automated commands

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Android instrumentation needs a running API 26+ emulator/device. Tests use test-only authentication and an in-memory Room database for offline repository behavior; no anonymous/guest sign-in path is compiled into production.

```sh
cd worker
npm ci
npm run check
npm test
npm run build
```

Worker tests generate RSA keys and exercise actual JWT signature verification with local JWKS. They cover missing/invalid/expired ID tokens, issuer/audience errors, tampering, missing/invalid App Check, unapproved app IDs, path traversal/encoded paths, missing PDF, malformed manifest, successful streaming and rate limits. R2 is a test binding, not a live bucket.

```sh
cd tools
npm ci
npm run validate
npm run test:emulators
```

Firebase Emulator Suite tests use only `demo-mindway`, start Auth and Firestore, load the real rules and test unauthenticated denial, ownership, field restrictions, bounded queries, bookmark validation and emulator Auth token issuance/rejection. This is not Google account-selector or Play Integrity verification; emulators do not reproduce those services.

## Android test coverage

- Unit: answer types, numerical tolerances / NaN, negative marks, skipped questions, finite empty-set results, mastery bounds, slow answers, configurable repetition, streak dates, malformed content, configuration limits.
- Room integration: offline durable attempts, idempotent re-submission, user isolation, bookmark isolation, repeated test completion, empty sets and local deletion.
- Compose: configured/unconfigured login, explanation correctness text, result review action, bottom navigation/profile confirmation, saved practice and bookmarking, PDF invalid-ID retry UI with no export/share actions.
- Window: FLAG_SECURE asserted on MainActivity before protected navigation.

## Required staging / device acceptance before publishing

1. Google account selection, cancellation, account switch, returning user, revoked/expired login and recent-login deletion. Verify Play app-signing SHA fingerprints.
2. App Check metrics and enforcement on a real Play-distributed build; missing/invalid token requests to deployed Worker must return 401/403.
3. Upload a small real PDF and an image-heavy PDF; test Android 8 and Android 15+ rendering/search, page navigation, pinch/pan, fullscreen, airplane-mode cache reuse, cache expiry, missing file and malformed content type.
4. Play your own unlisted lesson, then make it private/delete it; verify retry messages, standard controls, fullscreen and pause-on-background. Verify configured origin/referrer avoids player error 153.
5. Start a test, rotate, kill the process, return before/after the deadline, and inspect saved answers/results. Simulate offline attempts on two devices and delayed synchronization.
6. Android screenshot and screen-recording attempts on every protected destination, task-switcher previews and fullscreen dialog windows; use actual OEM devices as well as emulator flag assertions.
7. TalkBack, 200% font scaling, dark mode, RTL layout scaffolding and low-memory behavior. Expand translations before enabling another language.
8. Firestore quota denial, Worker 429/503 and disconnected network must preserve Room data. Work retries must stop after their bounded retry budget.
9. Complete deletion, interrupt midway and retry. Check every user subcollection plus Auth, then check local private files.

See `docs/VERIFICATION.md` for the recorded results from this implementation environment. Deployment-dependent checks must not be reported as passed from a local build alone.
