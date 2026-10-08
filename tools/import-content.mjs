import { readFile } from 'node:fs/promises';
import Ajv from 'ajv';

const validateOnly = process.argv.includes('--validate');
const file = process.argv.find((arg, i) => i >= 2 && !arg.startsWith('--'));
if (!file) throw new Error('Usage: node import-content.mjs [--validate] content.json');
const schema = JSON.parse(await readFile(new URL('../content/content.schema.json', import.meta.url), 'utf8'));
const content = JSON.parse(await readFile(file, 'utf8'));
const validate = new Ajv({ allErrors: true, strict: false }).compile(schema);
if (!validate(content)) { console.error(validate.errors); process.exit(1); }
for (const [collection, documents] of Object.entries(content)) {
  const seen = new Set();
  for (const document of documents) {
    if (seen.has(document.id)) throw new Error(`Duplicate ID in ${collection}: ${document.id}`);
    seen.add(document.id);
    if (collection === 'questions' && document.type !== 'NUMERICAL' && document.correctOption >= document.options.length) throw new Error(`Invalid correctOption: ${document.id}`);
    if (collection === 'videos' && document.isActive && document.youtubeVideoId.startsWith('REPLACE')) throw new Error('Replace video placeholders before activating');
  }
}
console.log(`Validated ${Object.values(content).reduce((n, docs) => n + docs.length, 0)} documents.`);
if (!validateOnly) {
  if (!process.env.GOOGLE_CLOUD_PROJECT) throw new Error('Set GOOGLE_CLOUD_PROJECT explicitly before import');
  const { initializeApp, applicationDefault } = await import('firebase-admin/app');
  const { getFirestore } = await import('firebase-admin/firestore');
  initializeApp({ credential: applicationDefault(), projectId: process.env.GOOGLE_CLOUD_PROJECT });
  const db = getFirestore();
  const documents = Object.entries(content).flatMap(([collection, docs]) => docs.map(data => ({ collection, data })));
  for (let i = 0; i < documents.length; i += 200) {
    const batch = db.batch();
    documents.slice(i, i + 200).forEach(({ collection, data }) => batch.set(db.collection(collection).doc(data.id), { ...data, updatedAt: Date.now() }, { merge: true }));
    await batch.commit();
  }
  console.log('Import complete. IDs are stable; re-import updates existing documents.');
}
