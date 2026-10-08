// Privileged operator-only deletion; never packaged into the Android app or public Worker.
import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';

const uid = process.argv[2];
if (!uid || uid.includes('/') || uid.length > 128 || !process.argv.includes('--execute')) {
  throw new Error('After verifying the request, run: GOOGLE_CLOUD_PROJECT=project node delete-user.mjs UID --execute');
}
if (!process.env.GOOGLE_CLOUD_PROJECT) throw new Error('Set the target project explicitly');
initializeApp({ credential: applicationDefault(), projectId: process.env.GOOGLE_CLOUD_PROJECT });
const db = getFirestore();
await db.doc(`users/${uid}`).set({ deletionPending: true }, { merge: true });
await db.recursiveDelete(db.doc(`users/${uid}`));
for (const collection of ['progress', 'bookmarks', 'settings']) await db.doc(`${collection}/${uid}`).delete();
try { await getAuth().deleteUser(uid); } catch (error) { if (error.code !== 'auth/user-not-found') throw error; }
console.log('Requested user records and Firebase Auth account deleted.');
