# Private PDF delivery on Cloudflare

1. Create a Cloudflare account. Keep Workers on the Free plan. Review R2 billing activation and its included allowances; R2 Standard usage above the allowance is billable.
2. Create a **Standard** R2 bucket named `mindway-pdfs`; optionally a separate private `mindway-pdfs-preview` for staging.
3. Keep **Public access disabled**. Do not enable `r2.dev`, attach a public bucket domain, or publish S3 credentials.
4. Upload your own PDFs with the dashboard or an authenticated admin tool. For example `aptitude/percentages.pdf` and `reasoning/syllogism.pdf`. Optimize PDFs for mobile; this client limits a document to 40 MiB.
5. Copy `content/pdf-manifest.example.json`, edit logical IDs and private object keys, then upload it to **`_catalog/pdfs.json` in the bucket**. This is a private manifest used only by the Worker. Its current limit is 1 MiB, adequate for hundreds/thousands of mappings. It is cached for 60 seconds per Worker isolate.
6. In `worker/wrangler.jsonc`, set your bucket binding and the public Firebase project ID, **numeric** project number and allowed Firebase **Android app IDs** (comma-separated). App IDs look like `1:123456789:android:abc`, not package names. Do not allow staging/debug app IDs in the production Worker.
7. The Worker needs no Firebase service-account key and no R2 API secret: Firebase signatures are verified with Google's public JWKS and R2 uses a binding. Put any future confidential value in `wrangler secret put NAME`, never `vars` or `.env` committed to source.
8. Build and deploy:

   ```sh
   cd worker
   npm ci
   npm run check
   npm test
   npm run build
   npx wrangler login
   npm run deploy
   ```

9. Copy the deployed HTTPS Worker URL (with trailing slash) into `MINDWAY_BACKEND_URL`. No custom domain is required: a `workers.dev` address is sufficient.
10. Add matching safe **metadata** documents to Firestore `pdfs/{logicalId}` using the importer. Do not put `objectKey`, bucket names, signed URLs or credentials in client-readable metadata.
11. Test from a configured staging Android build. Every request must include `Authorization: Bearer <Firebase ID token>` and `X-Firebase-AppCheck: <token>` in headers. Do not paste tokens into URLs, issue reports, build logs or committed test files.

## Endpoint contract

`GET /v1/pdfs/{logicalId}`, with no query string. Logical IDs match `[A-Za-z0-9_-]{1,100}`.

| Status | Meaning |
| --- | --- |
| 200 | PDF byte stream, `Content-Type: application/pdf`, inline disposition, private/no-store cache headers |
| 401 | Firebase identity missing, expired, incorrectly signed, wrong issuer/audience or malformed |
| 403 | App Check token missing/invalid or from an unapproved Android app |
| 404 | Invalid/unknown ID, inactive manifest entry, unsafe object mapping or missing object |
| 405 | Method other than GET |
| 429 | Best-effort per-isolate abuse threshold; Retry-After 60 seconds |
| 503 | Storage/catalog failure, invalid manifest or document size limit; Retry-After 60 seconds |

Authorization v1 permits any authenticated user of an approved app to read an active material. Add explicit server-side entitlements before introducing paid/member-only content. There is no arbitrary object-path endpoint, token query parameter, redirect or public signed-URL response.

## Operational details

- Rate limiting is bounded-memory and **per isolate**, not a globally consistent abuse quota. Workers Free account limits provide an additional ceiling. No paid rate-limiter service is assumed.
- JWKS caching supports key rotation. Unknown keys and key-fetch failures fail closed. ID and App Check signatures, expiration, issuer, audience, issued-at and subject are validated; ID token auth_time is checked too.
- Tokens are bearer credentials until expiration. Signature verification is not Firebase session revocation checking or App Check one-time-token replay protection. For immediate revocation, add a server-side revocation/entitlement policy; do not claim revoked tokens become instantly invalid here.
- No secret is logged. Built-in Worker observability is disabled so full request metadata is not captured by default.
- PDFs are streamed; the Worker does not buffer complete PDF objects or use public edge caching.
- Use active=false tombstones in both the private manifest and Firestore metadata to withdraw material. Already cached offline copies expire according to the app cache policy; revocation cannot recall bytes already delivered.
- Monitor Worker CPU on real traffic. Free plan's 10 ms CPU limit must accommodate verification and catalog parsing; do not infer its performance from desktop test duration.
