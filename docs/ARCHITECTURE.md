# Architecture and extension points

## Boundaries

Compose observes immutable models through lifecycle-aware Flow collection. ViewModels own jobs and navigation events. `LearningEngine` contains answer checking, scoring, mastery and repetition policy. Repository interfaces separate Firebase authentication and offline learning storage. Hilt wires one Room database, repositories, JSON codec, Firebase provider and private PDF cache.

Firebase calls are in remote sources / repositories, not Composables. Retrofit's suspending API handles PDF requests. An OkHttp interceptor attaches tokens only to the configured HTTPS backend, forbids redirects and refreshes both credentials once on a GET 401. Token waiting happens on OkHttp threads with a timeout.

## Offline source of truth

Room stores indexed questions, metadata, immutable attempts, mastery, bookmark tombstones, sessions, results and sync cursors. Every user-owned row has a UID; reads filter by it. One transaction records an attempt, changes mastery and saves the session. Attempt IDs are `sessionId_questionId`, making crash/retry duplicate submissions harmless.

Settings are in DataStore. Firebase persists identity; no custom password/token storage is introduced. Firestore's own cache is supplementary. Catalogs fetch 50-document pages on explicit screen entry/refresh; questions fetch on practice start or explicit “save more”. Room remains available if those fetches fail.

User uploads use bounded batches (100 attempts/bookmarks, eight results, keeping even large snapshots below Firestore's request-size limit). Remote attempts/results use server `receivedAt` and document-ID cursors, so late offline uploads are not skipped just because their client timestamp is old. Tests preserve compressed session snapshots for review on another device, with compressed/decompressed size limits. Bookmarks use last-write-wins timestamps with tombstones. Device clock skew can influence bookmark conflict resolution; these are personal learning records, not money or leaderboard scores.

Progress is aggregated through SQL rather than loading all attempts into the UI. History UI shows the latest 100 results; activity charts show up to 366 daily buckets. A session operates on at most 100 questions. Local candidate selection is capped at 1,000 to bound memory. A future bank search can introduce Paging / SQL ranked selection without changing screen contracts.

## Revision

`RepetitionPolicy` specifies target time, correct/slow gains, incorrect penalty and day intervals. Scores stay in [0,100]. Mistakes and slow answers increase repeat priority; elapsed overdue time and weak topics add weight. Strong fast performance lengthens the next interval. Policies can be injected/configured without changing repository persistence. Current labels: 0–30 Weak, 31–60 Improving, 61–80 Good, 81–100 Mastered.

## Sessions and navigation

Serializable routes contain only screen/topic/session/material IDs. Protected NavHost exists only for a signed-in user and is keyed by UID, which prevents restoring another account's back stack. Small setup inputs use `rememberSaveable`; session contents and answers live in Room. PDF page state is in SavedStateHandle. LazyColumn and navigation save state retain scroll positions.

Test deadline is an absolute timestamp persisted in Room. The ViewModel updates the visible timer once per second and submits on expiration, including when reopening an expired session. This is not a trusted anti-cheat clock; do not use client scores for a competitive leaderboard without server validation.

## PDF lifecycle

PDF bytes arrive only on open. AES-256-GCM with Android Keystore encrypts the private cache. Files are keyed by a hash of UID and logical ID. Rendering creates a private temporary plaintext file, opens a read-only descriptor, then immediately unlinks the pathname. PdfRenderer owns the descriptor until close. Rendering/search run on IO with serialized renderer access. Page images are capped at 2.5 million pixels to bound memory. Cache policy defaults to seven days / 120 MiB total; individual PDFs are capped at 40 MiB. Stale render files and partial downloads are cleaned up.

## UX, accessibility and localization

The sage/forest visual theme supports system/light/dark modes. Text uses scalable sp sizes, controls use Material touch targets, meaningful icons have descriptions and chart bars have semantic counts. Answer correctness has text, not color alone. No essential animation is used.

Primary/shared labels are Android string resources and there is an ISO language preference. English-only feature copy is still present in several Compose screens. Before enabling another language, externalize remaining English feature strings and add translated resources (Tamil `values-ta`, Hindi `values-hi`, Telugu `values-te`, Kannada `values-kn`, Malayalam `values-ml`) and language-specific question metadata. Do not enable a language selector entry until both UI and content are available.

## Content changes

Stable IDs are independent of display names. Import backend content without rebuilding the app. Use inactive tombstones rather than hard deletion so devices that refresh the page remove withdrawn metadata. Offline clients can retain previously delivered material until their next refresh/cache expiry. New question-type behavior is a code change; new questions/topics/exams/media are data changes.

`tests`, `testQuestions` and `dailyChallenges` collections are reserved in rules/schema design for future curated papers/challenge sets. Current release flow builds filtered tests from question metadata; `PREVIOUS_YEAR` selects questions with a year, rather than reproducing a publisher-defined ordered paper. Daily mode deterministically mixes the saved eligible question pool for the current India date; it does not imply a global leaderboard.
