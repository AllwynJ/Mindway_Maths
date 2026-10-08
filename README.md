# Mindway Maths

Native Android exam preparation for quantitative aptitude and logical reasoning. Kotlin, Jetpack Compose / Material 3, Hilt, Room, DataStore, WorkManager and Firebase. A separate Cloudflare Worker delivers private R2 PDFs after Firebase ID-token and App Check verification.

## Build

Open the root directory in current Android Studio. Install Android SDK 37 and use Android Studio's bundled JDK 25 (the Gradle daemon toolchain is pinned to Java 25; application bytecode targets Java 17).

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

- Release application ID: `com.elsco.mindwaymaths`; debug: `com.elsco.mindwaymaths.debug`.
- Minimum Android 8 / API 26; target and compile Android 17 / API 37.
- Gradle 9.8.1, AGP 9.4.1, Kotlin 2.4.21, Compose BoM 2026.09.00, Firebase BoM 35.0.0. Stable versions were checked against Google Maven / Maven Central on **2026-10-08**.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Unsigned, shrunk release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Release has R8 minification and resource shrinking. No private signing key is supplied. Use Android Studio's signed-bundle workflow or your CI secret store.

**The project builds without Firebase credentials. That build displays configuration guidance and does not bypass authentication.** To sign in and use remote content, configure your own services below. Sample questions are supplied for controlled import; they are not a complete exam question bank.

## Connect your services

1. Follow [Firebase setup](docs/FIREBASE_SETUP.md). Add `app/google-services.json` containing Android clients for both application IDs.
2. Copy the public configuration values from `config/gradle.properties.example` to your private `~/.gradle/gradle.properties`, or pass them with `-P`.
3. Follow [Cloudflare / R2 setup](docs/CLOUDFLARE_SETUP.md), upload your PDFs and the logical-ID manifest, and set `MINDWAY_BACKEND_URL`.
4. Follow [YouTube setup](docs/YOUTUBE_SETUP.md). Configure an owned HTTPS embedding origin and your own **unlisted** lessons.
5. Validate and import `content/seed.json`; then replace the inactive examples in `content/media.example.json` with your material.
6. Replace the marked legal text and publish your privacy, terms and account-deletion web pages before store submission.

`bundleRelease` checks the Firebase file, HTTPS backend, HTTPS video origin and OAuth web client configuration. A successful local unsigned release compilation is not evidence of a deployed or signed production service.

## User experience

- Google Credential Manager login and persistent Firebase authentication.
- Exam → subject → topic selection, configurable filters, instant answer explanations and shortcuts.
- MCQ, numerical answers and true/false; topic, random, weak, incorrect, bookmark, previous-year, daily, speed and spaced-revision modes.
- Timed tests with negative marking, answer palette, review marks, submission confirmation and detailed results.
- Room-persisted answers and deadlines survive process recreation. Test timers continue when leaving the screen.
- Home target, accuracy, streak, continue learning, weak topics, materials and video entry points.
- Native weekly activity visualization, monthly totals, topic mastery and test history.
- YouTube IFrame playback with standard controls and fullscreen; no stream extraction or downloading.
- Private PDF rendering, zoom/pan, page navigation, fullscreen and retry. Text search uses native Android 15+ APIs; image-only PDFs have no searchable text without OCR.
- Theme, daily goal, reminder and crash-diagnostic preferences. English ships initially.
- Five comfortably sized bottom actions: Home, Practice, Tests, Progress and More. More exposes Videos, PDFs and Profile; the top avatar also opens Profile.

## Architecture and data

See [architecture](docs/ARCHITECTURE.md), [Firestore schema](docs/FIRESTORE_SCHEMA.md), [security](docs/SECURITY.md) and [testing](docs/TESTING.md).

```
app/src/main/java/com/elsco/mindwaymaths/
  core/                    Hilt, theme, connectivity
  data/local/              Room tables, DataStore
  data/remote/             Firebase access, paginated content source
  data/repository/         auth and offline learning repositories
  domain/model/            immutable content, sessions, attempts and results
  domain/repository/       repository contracts
  domain/usecase/          scoring and configurable repetition policy
  feature/                 lifecycle-aware ViewModels and Compose screens
  navigation/              typed routes and authentication gate
  network/                 centralized authenticated Retrofit/OkHttp client
  security/                app-private encrypted PDF cache
  worker/                  controlled sync, FCM and local reminders
worker/                    deployable TypeScript Cloudflare Worker
content/                   JSON schema, original sample questions, metadata examples
tools/                     privileged offline content importer and emulator tests
```

Cloud content loads in pages of 50, not as a complete startup download. Practice selects a bounded local candidate set of up to 1,000 questions, then up to 100 per test. Use topic filters for large banks. User attempts are immutable and idempotent per session/question. Background sync uploads bounded batches and restores remote pages using persisted cursors. WorkManager retries exponentially at most four times per run; additional work can be scheduled manually or by the 12-hour periodic job.

## ₹0 early-growth infrastructure budget

**₹0 means within the providers' free tiers, not unlimited usage.** No paid VPS, Cloud Functions, video hosting, email platform or third-party analytics service is required by this project.

Published allowances checked on **2026-10-08**:

| Service | Included allowance / behavior | Monitor |
| --- | --- | --- |
| Firestore Standard free quota | 1 GiB stored; 50,000 reads/day; 20,000 writes/day; 20,000 deletes/day; 10 GiB outbound/month; one free database/project | Firebase Console → Firestore → Usage; Google Cloud quotas |
| Workers Free | 100,000 requests/day; 10 ms CPU/invocation | Cloudflare → Workers & Pages → Metrics / Limits |
| R2 **Standard** free allowance | 10 GB-month storage; 1 million Class A operations/month; 10 million Class B operations/month; free egress | Cloudflare → R2 → Metrics / Billing |
| Google sign-in, FCM, Crashlytics | No per-message FCM or Crashlytics charge; Firebase Auth has service-specific operational quotas | Firebase Authentication usage; Firebase pricing / quotas |
| App Check / Play Integrity | App Check is no-cost; Play Integrity has its own per-project quotas (verify current allocation in Console) | Firebase App Check and Play Console / Google Cloud quotas |
| YouTube embedded playback | Hosted by YouTube under its embedding terms; not a private-storage or bandwidth guarantee | YouTube Studio |

Sources: [Firestore quotas](https://firebase.google.com/docs/firestore/quotas), [Firebase pricing](https://firebase.google.com/pricing), [Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/), [R2 pricing](https://developers.cloudflare.com/r2/pricing/).

Keep Firebase on Spark and Workers on Free for initial operation. R2 activation may require billing details and its allowance is **not a hard spend cap**; exceeding it can incur charges. Do not enable Infrequent Access storage. Monitor storage and reads, set account billing notifications, and deactivate PDF delivery before exhausting your chosen budget. This app does not automatically upgrade any plan. Play Store developer registration, a custom domain and content production are separate from monthly infrastructure usage.

Firestore quota failures preserve Room progress and defer sync. PDF/Worker failures show availability/retry messages. Requests do not retry indefinitely. When Cloudflare rejects requests before Worker execution because the account quota is exhausted, the Android client still provides a friendly error; the Worker cannot customize a response it never receives.

## Protection boundaries

Authentication → App Check → private R2 → authenticated Worker → private encrypted cache → secure windows → R8 → no PDF export UI.

This is deterrence and access control, **not unbreakable DRM**. A compromised/rooted client can extract content; a second camera can capture a screen. YouTube IDs and traffic are observable. **Unlisted is not equivalent to secret. Anyone who obtains the URL can reshare it.**

The Worker has no R2 credentials: it uses a binding. The Android app never receives an R2 URL or object path. There are no public PDF sharing, printing or export intents.

## Content and publishing scope

The seed contains five exam configurations, two subjects, all 38 initial topics and 14 original sample questions for SSC. Add licensed/owned exam questions and previous-year items (`year`, `source`) through the importer. No exam-paper authenticity is implied by sample data. Daily practice is currently a deterministic date-seeded mixed practice test, with no global ranking. User scores are self-tracking, not trusted leaderboard results.

Localization resources and a language preference are present; English is the only enabled translation. Additional translations, complete papers, real media, Google Play sign-in / Integrity validation and publisher-reviewed legal content are release configuration/content work. See [testing](docs/TESTING.md) for what was actually verified locally and what requires a configured staging project.
