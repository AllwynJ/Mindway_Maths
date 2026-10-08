import { readFile } from 'node:fs/promises';
import { before, after, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, setDoc, getDoc, getDocs, collection, query, limit, deleteDoc, serverTimestamp } from 'firebase/firestore';

let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-mindway', firestore: { rules: await readFile(new URL('../firestore.rules', import.meta.url), 'utf8') } });
});
after(async () => env?.cleanup());
beforeEach(async () => env.clearFirestore());
test('content requires authentication and never permits client writes', async () => {
  await env.withSecurityRulesDisabled(async context => setDoc(doc(context.firestore(), 'questions', 'q1'), { questionText: 'sample' }));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'questions', 'q1')));
  await assertSucceeds(getDoc(doc(env.authenticatedContext('alice').firestore(), 'questions', 'q1')));
  await assertFails(setDoc(doc(env.authenticatedContext('alice').firestore(), 'questions', 'q1'), { questionText: 'modified' }));
});
test('profiles and progress cannot cross accounts', async () => {
  const alice = env.authenticatedContext('alice').firestore(); const bob = env.authenticatedContext('bob').firestore();
  await assertSucceeds(setDoc(doc(alice, 'users', 'alice'), { uid: 'alice', dailyGoal: 20, selectedLanguage: 'en' }));
  await assertFails(getDoc(doc(bob, 'users', 'alice')));
  await assertFails(setDoc(doc(bob, 'users', 'alice'), { uid: 'bob', dailyGoal: 20, selectedLanguage: 'en' }));
  await assertFails(setDoc(doc(alice, 'users', 'alice'), { uid: 'alice', dailyGoal: 20, selectedLanguage: 'en', admin: true }));
  await assertSucceeds(deleteDoc(doc(alice, 'users', 'alice')));
});
test('content lists must be bounded', async () => {
  const db = env.authenticatedContext('alice').firestore();
  await assertFails(getDocs(collection(db, 'questions')));
  await assertSucceeds(getDocs(query(collection(db, 'questions'), limit(50))));
});
test('bookmarks are owner-only and schema validated', async () => {
  const db = env.authenticatedContext('alice').firestore();
  await setDoc(doc(db, 'users/alice'), { uid: 'alice', dailyGoal: 20, selectedLanguage: 'en' });
  await assertSucceeds(setDoc(doc(db, 'users/alice/bookmarks/q1'), { questionId: 'q1', active: true, updatedAt: 1 }));
  await assertFails(setDoc(doc(db, 'users/bob/bookmarks/q1'), { questionId: 'q1', active: true, updatedAt: 1 }));
  await assertFails(setDoc(doc(db, 'users/alice/bookmarks/q2'), { questionId: 'q1', active: true, updatedAt: 1 }));
});
test('attempt sync is immutable, idempotent and blocked once deletion begins', async () => {
  const db = env.authenticatedContext('alice').firestore();
  await setDoc(doc(db, 'users/alice'), { uid: 'alice', dailyGoal: 20, selectedLanguage: 'en' });
  const attempt = { id: 's_q1', uid: 'alice', questionId: 'q1', topic: 'percentage', correct: true, timeTaken: 3000, attemptNumber: 1, timestamp: 1, receivedAt: serverTimestamp() };
  const ref = doc(db, 'users/alice/attempts/s_q1');
  await assertSucceeds(setDoc(ref, attempt));
  await assertSucceeds(setDoc(ref, attempt));
  await assertFails(setDoc(ref, { ...attempt, correct: false }));
  await setDoc(doc(db, 'users/alice'), { deletionPending: true }, { merge: true });
  await assertFails(setDoc(ref, attempt));
  await assertSucceeds(deleteDoc(ref));
});
test('Firebase Auth emulator issues an ID token and rejects malformed sessions', async () => {
  const host = process.env.FIREBASE_AUTH_EMULATOR_HOST ?? '127.0.0.1:9099';
  const response = await fetch(`http://${host}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ returnSecureToken: true }) });
  assert.equal(response.status, 200); const user = await response.json(); assert.ok(user.idToken);
  const invalid = await fetch(`http://${host}/identitytoolkit.googleapis.com/v1/accounts:lookup?key=fake`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ idToken: 'invalid' }) });
  assert.equal(invalid.status, 400);
});
