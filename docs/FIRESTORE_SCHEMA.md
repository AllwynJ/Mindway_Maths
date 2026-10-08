# Firestore schema

Content documents use stable `[A-Za-z0-9_-]{1,100}` IDs. All root content is authenticated-read, admin-IAM-write. Collections are paginated. No Android client has content administration privileges.

| Path | Fields / purpose |
| --- | --- |
| `users/{uid}` | uid, displayName, email, photoUrl?, createdAt, lastLoginAt, selectedExam, selectedLanguage, dailyGoal, totalQuestionsSolved, totalCorrect, totalTests, streak, appVersion, deletionPending |
| `exams/{id}` | id, title, order, isActive |
| `subjects/{id}` | id, title, order, isActive |
| `topics/{id}` | id, title, subject, exam optional, order, isActive |
| `questions/{id}` | id, exam, subject, topic, subtopic, difficulty, type, questionText, imageUrl?, options, correctOption, numericalAnswer?, tolerance, explanation, shortcut, solution, source, tags, year?, isActive, createdAt, updatedAt |
| `videos/{id}` | id, title, description, youtubeVideoId, visibility=`unlisted`, thumbnailUrl, topicId, subject, exam, duration seconds, order, isActive, createdAt, updatedAt |
| `pdfs/{id}` | id, title, description, subject, topic, exam, pageCount, fileSize bytes, thumbnailUrl, sortOrder, isActive, createdAt, updatedAt |
| `users/{uid}/attempts/{sessionId_questionId}` | id, uid, questionId, topic, selectedOption?, numericalAnswer?, correct, timeTaken milliseconds, attemptNumber, timestamp milliseconds, receivedAt server Timestamp |
| `users/{uid}/testHistory/{sessionId}` | score/result fields, JSON payload, bounded gzip/base64 completed-session snapshot, receivedAt server Timestamp |
| `users/{uid}/bookmarks/{questionId}` | questionId, active boolean tombstone, updatedAt milliseconds |
| `progress/{uid}` | self-tracking aggregate totals and updatedAt |
| `settings/{uid}`, `bookmarks/{uid}` | reserved root documents; existing data can be read/deleted by owner; current app uses DataStore/profile and bookmark subcollection |
| `users/{uid}/mastery/{id}` | reserved for future server-derived mastery; client create/update denied; local mastery derived from attempts |
| `tests/{id}` | reserved curated configuration: title, exam, durationMinutes, marks, negativeMarks, questionIds, isActive |
| `testQuestions/{testId_questionId}` | reserved large-paper membership: testId, questionId, order |
| `dailyChallenges/{YYYY-MM-DD_exam}` | reserved curated question IDs and timing; default client daily mode is date-seeded local practice |
| `leaderboards/{id}` | no client permissions in first version; never trust client-written scores for ranking |

PDF object keys are deliberately **not** in readable Firestore metadata. Firestore rules cannot redact a field from a readable document. Instead the private R2 `_catalog/pdfs.json` manifest holds `{ logicalId: { objectKey, isActive } }`. The Worker validates and resolves that mapping; the APK sees only the logical ID.

Question types are `MCQ`, `NUMERICAL`, `TRUE_FALSE`; correctOption is zero-based. MCQs have 4–8 options, true/false has 2; numerical answers use finite numbers and nonnegative tolerance. Difficulty strings are Easy, Medium, Hard.

JSON schema and example bundles live in `content/`. The importer also checks duplicate IDs and valid answer indexes. `createdAt/updatedAt` content values may be epoch milliseconds; the Android mapper also accepts Firestore Timestamp values. Questions should be original or appropriately licensed; source/year are metadata, not a license grant.

User attempts and results are immutable apart from server receipt timestamps on idempotent reupload. Owner rules isolate accounts and reject unknown fields. These records support self-reported educational progress, not a tamper-proof exam service. Production schema changes require Room migrations, rules updates and matching emulator tests.
