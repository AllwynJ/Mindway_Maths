# Content protection and release security

## Implemented controls

- `FLAG_SECURE` is set on MainActivity before Compose renders. Sensitive fullscreen dialogs explicitly request a secure window. This covers questions, explanations, test/results, PDFs, videos and profile transitions.
- TLS-only network security, system trust roots, no custom TrustManager, no certificate-verification bypass, no cleartext traffic.
- Backend requests restricted to configured HTTPS host; redirects disabled; auth/App Check in headers; a single safe GET retry on 401.
- Firebase Credential Manager login, UID-scoped repositories, protected typed navigation and owner-only Firestore user records.
- Release Play Integrity provider is in its own source set. Debug provider dependency/source is debug-only.
- Private R2 binding, authenticated PDF gateway, validated logical IDs and safe object mappings. No arbitrary path endpoint or R2 signed URL response.
- Keystore-encrypted private PDF cache, no external storage permission, no FileProvider, no share/export/open-with/print intents. Backup and device transfer are excluded for private app data.
- WebView JavaScript exists only for the fixed YouTube embed; no file/content access, no mixed content, no arbitrary native bridge, no media interception.
- R8 and resource shrinking in release; production Log verbose/debug/info calls removed. No token/content logger exists. Crash collection defaults off and is preference controlled.
- Only launcher activity is exported. Notification pending intent is immutable. FCM input accepts predefined event kinds rather than external URLs or arbitrary private text.

## Threat boundaries

This is deterrence and access control, not DRM. Rooted/instrumented devices, memory inspection, physical cameras and previously delivered content remain outside the secure-window guarantee. The app cannot make YouTube IDs invisible or make unlisted URLs unshareable. Offline content necessarily remains available for some time without reattestation.

Signed Firebase tokens can remain valid until expiration after account revocation. The Worker validates signatures and claims but does not query Firebase for per-request revocation or consume App Check tokens. Add a server-side authorization/revocation database if an immediate-revocation policy becomes necessary. No claim is made that Play Integrity detects every compromised device.

Client progress is not trusted for public ranking. No anti-cheat leaderboard or client-admin role is implemented. Question answers reside in the authenticated offline question payload because instant offline feedback requires them.

## Production configuration review

Use the correct production Firebase Android client, OAuth web client, Play signing certificate and allowed App Check app IDs. Deploy rules/indexes before publishing content. Test Play Integrity from a Play internal test track. Review Firebase API-key restrictions for intended Android APIs. Never restrict an API key in a way that disables Credential Manager/Firebase token refresh without testing it.

There is no release signing keystore in the repository. Store signing secrets in your CI secret manager or protected local keystore. Never add service-account private JSON or R2 API credentials to BuildConfig/resources/assets. `.gitignore` excludes credential/configuration files; review changes before committing.

Certificate pinning is intentionally not enabled: unmanaged pins can lock students out during normal edge certificate rotation. HTTPS system verification remains enabled. If pinning is introduced, use owned-key backup pins, a rotation plan and real outage tests.
