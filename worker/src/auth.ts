import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from 'jose';

export interface FirebaseEnvironment {
  FIREBASE_PROJECT_ID: string;
  FIREBASE_PROJECT_NUMBER: string;
  FIREBASE_ANDROID_APP_IDS: string;
}
export class AccessError extends Error {
  constructor(readonly status: number, readonly code: string) { super(code); }
}
const idKeys = createRemoteJWKSet(new URL('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com'),
  { timeoutDuration: 5000, cooldownDuration: 30_000, cacheMaxAge: 3_600_000 });
const appKeys = createRemoteJWKSet(new URL('https://firebaseappcheck.googleapis.com/v1/jwks'),
  { timeoutDuration: 5000, cooldownDuration: 30_000, cacheMaxAge: 3_600_000 });

export function firebaseVerifier(keys: { id: JWTVerifyGetKey; app: JWTVerifyGetKey } = { id: idKeys, app: appKeys }) {
  return async (request: Request, env: FirebaseEnvironment): Promise<string> => {
    const authorization = request.headers.get('Authorization') ?? '';
    const token = /^Bearer ([A-Za-z0-9_.-]+)$/.exec(authorization)?.[1];
    if (!token || token.length > 8192) throw new AccessError(401, 'authentication_required');
    let uid: string;
    try {
      const { payload } = await jwtVerify(token, keys.id, {
        algorithms: ['RS256'], issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
        audience: env.FIREBASE_PROJECT_ID, requiredClaims: ['sub', 'exp', 'iat', 'auth_time'], clockTolerance: 5,
      });
      const now = Math.floor(Date.now() / 1000);
      if (!payload.sub || payload.sub.length > 128 || (payload.iat ?? Infinity) > now + 5 ||
          typeof payload.auth_time !== 'number' || payload.auth_time > now + 5) throw new Error('invalid claims');
      uid = payload.sub;
    } catch { throw new AccessError(401, 'invalid_authentication'); }
    const attestation = request.headers.get('X-Firebase-AppCheck');
    if (!attestation || attestation.length > 8192) throw new AccessError(403, 'attestation_required');
    try {
      const { payload } = await jwtVerify(attestation, keys.app, {
        algorithms: ['RS256'], issuer: `https://firebaseappcheck.googleapis.com/${env.FIREBASE_PROJECT_NUMBER}`,
        audience: `projects/${env.FIREBASE_PROJECT_NUMBER}`, requiredClaims: ['sub', 'exp', 'iat'], clockTolerance: 5,
      });
      const allowedApps = env.FIREBASE_ANDROID_APP_IDS.split(',').map(id => id.trim()).filter(Boolean);
      if (!payload.sub || !allowedApps.includes(payload.sub) || (payload.iat ?? Infinity) > Date.now() / 1000 + 5) throw new Error('unapproved app');
    } catch { throw new AccessError(403, 'invalid_attestation'); }
    return uid;
  };
}
