# Firebase setup

1. Create a Firebase project on the Spark plan. Analytics is optional and is not included in the app.
2. Add the release Android app with package **com.elsco.mindwaymaths** and the debug Android app with **com.elsco.mindwaymaths.debug**. Use a separate staging project for development where possible.
3. Add SHA-1 and SHA-256 certificate fingerprints for the local debug certificate. Add your upload/signing certificate fingerprints for production; with Play App Signing also register the **Play app-signing** certificate. `./gradlew signingReport` lists local signing fingerprints.
4. Download the public Android configuration to `app/google-services.json`. It must include clients for both IDs if shared at module root. Do not place an Admin SDK / service-account JSON in the Android project.
5. Authentication → Sign-in method → enable **Google**. Set the support email and OAuth consent configuration. Do not enable SMS authentication for this architecture.
6. Set `MINDWAY_WEB_CLIENT_ID` to the **Web application OAuth client ID**, not the Android client ID. Credential Manager exchanges Google's ID token for a Firebase credential. If no explicit property exists the app tries the Google Services-generated `default_web_client_id` resource.
7. Create the default **Cloud Firestore Standard / Native mode** database in an appropriate India/nearby region. Start in production/locked mode. The location is difficult to change later.
8. From `tools/`, install dependencies with Node 24 LTS and `npm ci`. Authenticate the CLI with your own account. Deploy from the project root:

   ```sh
   tools/node_modules/.bin/firebase deploy --only firestore:rules,firestore:indexes --project YOUR_PROJECT_ID
   ```

   Wait for indexes to finish building. The query filters use equality on exam, subject and topic and a document-ID cursor. Add an index for any new query shape before enabling that filter in production.
9. App Check → register each Android app. Use **Play Integrity** for release, linking the correct Google Cloud / Play project and release SHA-256. Choose verdict requirements appropriate to Play distribution and test them on an internal Play test track.
10. Debug builds compile a separate `AppCheckInstaller` with the Firebase Debug provider. Register the developer-generated debug token **only in staging**. Never copy a debug token into source, resources or release configuration. The release source set references only the Play Integrity provider.
11. Observe App Check request metrics first. Test login, Firestore reads/writes, returning users and the PDF Worker. Then enable App Check enforcement for supported Firebase resources. App Check enforcement is configured in Firebase Console, not through Firestore rules. The Worker requires valid tokens independently.
12. Configure Crashlytics using the Firebase Console setup wizard. The Crashlytics Gradle plugin is applied only when the Firebase config file exists; release mapping upload then uses the standard plugin. Diagnostics are off by default and can be enabled in Settings.
13. FCM requires Google Play services. The app requests `POST_NOTIFICATIONS` only when reminders are enabled. Send **data-only** messages to opted-in `learning-updates`, with `data.kind` equal to `challenge`, `pdf`, `video`, `reminder` or `streak`. Keep all message sending in a trusted admin process. Basic daily reminders are local WorkManager jobs, with no server cron.

## Controlled content import

```sh
cd tools
npm ci
npm run validate
node import-content.mjs --validate ../content/media.example.json
GOOGLE_CLOUD_PROJECT=YOUR_PROJECT_ID node import-content.mjs ../content/seed.json
```

The importer uses Application Default Credentials (for example a locally authenticated Google Cloud account or a short-lived CI identity). Keep long-lived keys out of the repository. Import credentials need only the required Firestore IAM role. Admin SDK operations bypass client rules; content writes are never permitted to Android clients, including users who invent an `admin` field.

## Account deletion

The app requires online reauthentication with the same Google account, writes a deletion-pending marker, deletes each known user subcollection in batches of 100, removes root settings/progress documents, deletes the profile, then deletes the Firebase Auth account. Local Room user data and PDF caches are cleared afterward. Interrupted deletions are repeatable; the sync worker checks the pending marker before uploading.

Maintain a public data-deletion request page with your contact and policy, as required for your store listing. A publisher operator can run `GOOGLE_CLOUD_PROJECT=YOUR_PROJECT node delete-user.mjs UID --execute` from `tools/` after verifying the request. It uses Firebase Admin SDK recursive deletion for `users/{uid}`, deletes the root progress/settings/bookmark documents, then deletes the Auth account. This administrative action is not exposed in the Android app or public Worker.

When adding a new user-owned collection, add it to the deletion flow, rules, tests and data inventory in the same change. Never assume deleting a Firestore parent document recursively deletes subcollections.
