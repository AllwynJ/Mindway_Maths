import { beforeAll, describe, expect, it } from 'vitest';
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from 'jose';
import { firebaseVerifier } from '../src/auth';
import { createGateway, type Env } from '../src/index';

const project = 'mindway-test'; const number = '123456789'; const app = `1:${number}:android:test`;
let privateKey: CryptoKey; let verify: ReturnType<typeof firebaseVerifier>;
beforeAll(async () => {
  const pair = await generateKeyPair('RS256'); privateKey = pair.privateKey;
  const key = { ...await exportJWK(pair.publicKey), kid: 'test-key', alg: 'RS256', use: 'sig' };
  const getKey = createLocalJWKSet({ keys: [key] }); verify = firebaseVerifier({ id: getKey, app: getKey });
});
async function token(kind: 'id' | 'app', changes: Record<string, unknown> = {}) {
  const now = Math.floor(Date.now() / 1000);
  return new SignJWT({
    sub: kind === 'id' ? 'student-1' : app, iss: kind === 'id' ? `https://securetoken.google.com/${project}` : `https://firebaseappcheck.googleapis.com/${number}`,
    aud: kind === 'id' ? project : [`projects/${number}`], iat: now, exp: now + 3600, auth_time: now, ...changes,
  }).setProtectedHeader({ alg: 'RS256', kid: 'test-key' }).sign(privateKey);
}
function env(catalog: unknown = { percentages: { objectKey: 'aptitude/percentages.pdf', isActive: true } }, missing = false): Env {
  return { FIREBASE_PROJECT_ID: project, FIREBASE_PROJECT_NUMBER: number, FIREBASE_ANDROID_APP_IDS: app,
    R2_BUCKET: { get: async (key: string) => key === '_catalog/pdfs.json'
      ? { size: 100, json: async () => catalog }
      : missing ? null : { size: 9, body: new Blob(['%PDF-test']).stream() } } as unknown as R2Bucket };
}
async function request(path = 'percentages', id?: string, attestation?: string) {
  return new Request(`https://pdfs.example.com/v1/pdfs/${path}`, { headers: {
    Authorization: `Bearer ${id ?? await token('id')}`, 'X-Firebase-AppCheck': attestation ?? await token('app'),
  } });
}
describe('JWT and PDF access boundary', () => {
  it('streams bytes without bucket credentials or location', async () => {
    const response = await createGateway(verify).fetch(await request(), env());
    expect(response.status).toBe(200); expect(await response.text()).toBe('%PDF-test');
    expect(response.headers.get('Content-Type')).toBe('application/pdf');
    expect(response.headers.get('Location')).toBeNull(); expect(response.headers.get('Cache-Control')).toContain('no-store');
  });
  it('rejects missing authentication', async () => expect((await createGateway(verify).fetch(new Request('https://x/v1/pdfs/percentages'), env())).status).toBe(401));
  it.each([{ exp: 1 }, { aud: 'wrong' }, { iss: 'https://attacker.invalid' }, { sub: '' }, { iat: 9999999999 }])('rejects invalid ID claims %j', async claims => {
    expect((await createGateway(verify).fetch(await request('percentages', await token('id', claims)), env())).status).toBe(401);
  });
  it('rejects tampered signature', async () => {
    const t = await token('id'); const parts = t.split('.'); parts[2] = (parts[2][0] === 'a' ? 'b' : 'a') + parts[2].slice(1);
    expect((await createGateway(verify).fetch(await request('percentages', parts.join('.')), env())).status).toBe(401);
  });
  it.each([{ exp: 1 }, { aud: 'projects/other' }, { sub: 'unapproved-app' }, { iss: 'wrong' }])('rejects invalid attestation %j', async claims => {
    expect((await createGateway(verify).fetch(await request('percentages', undefined, await token('app', claims)), env())).status).toBe(403);
  });
  it('rejects absent attestation', async () => {
    const r = await request(); r.headers.delete('X-Firebase-AppCheck');
    expect((await createGateway(verify).fetch(r, env())).status).toBe(403);
  });
  it.each(['unknown', '__proto__', '%2e%2e%2fsecret', 'a%2Fb', '../secret', 'x?objectKey=secret'])('rejects unknown or unsafe ID %s', async path => {
    expect((await createGateway(verify).fetch(await request(path), env())).status).toBe(404);
  });
  it('rejects unsafe server mapping', async () => expect((await createGateway(verify).fetch(await request(), env({ percentages: { objectKey: '../secret.pdf', isActive: true } }))).status).toBe(404));
  it('handles missing object', async () => expect((await createGateway(verify).fetch(await request(), env(undefined, true))).status).toBe(404));
  it('handles malformed manifest without leaking internal details', async () => expect((await createGateway(verify).fetch(await request(), env('invalid'))).status).toBe(503));
  it('bounds requests and supplies retry guidance', async () => {
    const gateway = createGateway(verify); const environment = env(); const r = await request();
    let response = new Response();
    for (let i = 0; i < 31; i++) response = await gateway.fetch(r, environment);
    expect(response.status).toBe(429); expect(response.headers.get('Retry-After')).toBe('60');
  });
});
