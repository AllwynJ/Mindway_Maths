import { AccessError, firebaseVerifier, type FirebaseEnvironment } from './auth';

export interface Env extends FirebaseEnvironment { R2_BUCKET: R2Bucket; MAX_PDF_BYTES?: string }
interface PdfEntry { objectKey: string; isActive: boolean }
type Verify = (request: Request, env: FirebaseEnvironment) => Promise<string>;

// Best-effort free-tier, per-isolate throttling. Not a global distributed quota.
class LocalRateLimiter {
  private entries = new Map<string, { count: number; reset: number }>();
  allow(key: string, limit: number, now = Date.now()): boolean {
    const old = this.entries.get(key);
    if (!old || old.reset <= now) {
      if (this.entries.size >= 5000) {
        for (const [k, v] of this.entries) if (v.reset <= now) this.entries.delete(k);
        if (this.entries.size >= 5000) this.entries.delete(this.entries.keys().next().value!);
      }
      this.entries.set(key, { count: 1, reset: now + 60_000 }); return true;
    }
    return ++old.count <= limit;
  }
}
function headers(extra: HeadersInit = {}): Headers {
  const result = new Headers({
    'Cache-Control': 'private, no-store, max-age=0', 'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer', 'Cross-Origin-Resource-Policy': 'same-origin',
    'Strict-Transport-Security': 'max-age=31536000; includeSubDomains',
  });
  new Headers(extra).forEach((v, k) => result.set(k, v)); return result;
}
function failure(status: number, code: string): Response {
  return Response.json({ error: code }, { status, headers: headers(status === 429 || status === 503 ? { 'Retry-After': '60' } : {}) });
}
function safeEntry(value: unknown): value is PdfEntry {
  if (!value || typeof value !== 'object') return false;
  const entry = value as Partial<PdfEntry>;
  return entry.isActive === true && typeof entry.objectKey === 'string' && entry.objectKey.length <= 300 &&
    /^[a-zA-Z0-9_-]+(?:\/[a-zA-Z0-9_-]+)*\.pdf$/.test(entry.objectKey) && !entry.objectKey.includes('..');
}

export function createGateway(verify: Verify = firebaseVerifier()) {
  const rate = new LocalRateLimiter();
  let catalog: Record<string, unknown> | undefined;
  let catalogExpires = 0;
  let catalogBucket: R2Bucket | undefined;
  return {
    async fetch(request: Request, env: Env): Promise<Response> {
      try {
        const url = new URL(request.url);
        if (url.protocol !== 'https:') return failure(400, 'https_required');
        if (request.method !== 'GET') return new Response(null, { status: 405, headers: headers({ Allow: 'GET' }) });
        const match = /^\/v1\/pdfs\/([A-Za-z0-9_-]{1,100})$/.exec(url.pathname);
        if (!match || url.search) return failure(404, 'not_found');
        const ip = request.headers.get('CF-Connecting-IP') ?? 'local';
        if (!rate.allow(`ip:${ip}`, 60)) return failure(429, 'too_many_requests');
        const uid = await verify(request, env);
        if (!rate.allow(`uid:${uid}`, 30)) return failure(429, 'too_many_requests');
        if (!catalog || catalogExpires <= Date.now() || catalogBucket !== env.R2_BUCKET) {
          const manifest = await env.R2_BUCKET.get('_catalog/pdfs.json');
          if (!manifest || manifest.size > 1_048_576) return failure(503, 'temporarily_unavailable');
          const value: unknown = await manifest.json();
          if (!value || Array.isArray(value) || typeof value !== 'object') return failure(503, 'temporarily_unavailable');
          catalog = value as Record<string, unknown>; catalogExpires = Date.now() + 60_000; catalogBucket = env.R2_BUCKET;
        }
        const id = match[1];
        const entry = Object.hasOwn(catalog, id) ? catalog[id] : undefined;
        if (!safeEntry(entry)) return failure(404, 'not_found');
        // Authorization policy v1: any authenticated user of an approved Android app may read active materials.
        // Never accept a path, storage URL, or signed URL from the caller.
        const object = await env.R2_BUCKET.get(entry.objectKey);
        if (!object) return failure(404, 'not_found');
        const max = Number(env.MAX_PDF_BYTES ?? '41943040');
        if (!Number.isFinite(max) || max <= 0 || object.size > max) { await object.body.cancel(); return failure(503, 'temporarily_unavailable'); }
        return new Response(object.body, { status: 200, headers: headers({
          'Content-Type': 'application/pdf', 'Content-Disposition': 'inline; filename="study-material.pdf"',
          'Content-Length': String(object.size),
        }) });
      } catch (error) {
        if (error instanceof AccessError) return failure(error.status, error.code);
        // Tokens, claims, identifiers, bucket errors and object paths are deliberately not logged or returned.
        return failure(503, 'temporarily_unavailable');
      }
    },
  };
}
export default createGateway();
