#!/usr/bin/env node
/**
 * Faz 2H.2 — Firebase Security Rules izin doğrulama senaryoları.
 * Sadece `demo-benimdunyam-2h2` adlı yerel Firebase emülatöründe çalışır.
 * Kullanıcıların gerçek verisine ve gerçek bir Firebase projesine bağlanmaz.
 * Kotlin/Android test sınıfı oluşturmaz.
 */
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {initializeTestEnvironment, assertSucceeds, assertFails} from '@firebase/rules-unit-testing';
import {doc, collection, getDoc, getDocs, setDoc, updateDoc, deleteDoc, serverTimestamp} from 'firebase/firestore';
import {ref, uploadBytes, getMetadata, list, deleteObject} from 'firebase/storage';

const dir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(dir, '../..');
const projectId = 'demo-benimdunyam-2h2';
const bucket = `${projectId}.appspot.com`;
const sha = x => createHash('sha256').update(x).digest('hex');
const actualProject = process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT;
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
const storageHost = process.env.FIREBASE_STORAGE_EMULATOR_HOST;

if ((actualProject && actualProject !== projectId) ||
    !firestoreHost || !storageHost ||
    !/^(?:127\.0\.0\.1|localhost):\d{2,5}$/.test(firestoreHost) ||
    !/^(?:127\.0\.0\.1|localhost):\d{2,5}$/.test(storageHost)) {
  console.error('GÜVENLİK KİLİDİ: İki localhost emülatörünü birlikte başlatmadan bu dosya çalışmaz.');
  process.exit(2);
}

const rules = key => fs.readFileSync(path.join(root, `firebase/security/${key}.rules`), 'utf8');
const env = await initializeTestEnvironment({
  projectId,
  firestore: {host: '127.0.0.1', port: 18080, rules: rules('firestore')},
  storage: {host: '127.0.0.1', port: 19199, rules: rules('storage')},
});

const userA = 'alice_2h2';
const userB = 'bob_2h2';
const a = env.authenticatedContext(userA);
const b = env.authenticatedContext(userB);
const anon = env.unauthenticatedContext();
const fd = c => c.firestore();
const st = c => c.storage(`gs://${bucket}`);
const markerId = sha(`${userA}|MEDIA_DELETE|JOURNAL_PHOTO|journal-1|1700000000000_123e4567-e89b-12d3-a456-426614174000.jpg`);
const mediaId = 'a'.repeat(64);
const manifestPath = `users/${userA}/encrypted_media_v1/${mediaId}.bin`;
const bobPath = `users/${userB}/encrypted_media_v1/${'b'.repeat(64)}.bin`;
const ownerHash = 'c'.repeat(64);
const fileNameHash = 'd'.repeat(64);
const markerDoc = {
  formatVersion: 3, ownerUid: userA, ownerHash, fileNameHash,
  encryptedPayload: 'cipherv3_' + 'x'.repeat(80), createdAt: serverTimestamp(),
};
const manifestDoc = {
  formatVersion: 1, ownerUid: userA, kind: 'JOURNAL_PHOTO',
  storagePath: manifestPath, ownerHash, fileNameHash,
  createdAt: serverTimestamp(),
};
const marker = doc(fd(a), `users/${userA}/encrypted_media_deletions/${markerId}`);
const manifest = doc(fd(a), `users/${userA}/encrypted_media_entries/${mediaId}`);
const serverPermit = `users/${userA}/_server_media_cleanup_permits_v2/abc123`;
const serverAudit = `users/${userA}/_server_media_cleanup_audit/abc123`;
const results = [];
async function test(title, promise, allowed) {
  try {
    await (allowed ? assertSucceeds(promise) : assertFails(promise));
    results.push([title, true]);
    console.log(`[OK] ${title}`);
  } catch (err) {
    results.push([title, false]);
    console.error(`[HATA] ${title}: ${String(err.code || err.message || err).slice(0, 150)}`);
  }
}

try {
  // İlk denemede emülatörün eski dokümanlarını temizle. Canlı sistemden silme YOK.
  await env.clearFirestore();
  await env.clearStorage();

  // Firestore: başarılı işlemler de zorunlu; yalnızca reddetme kontrolü yeterli değil.
  await test('A kendi şifreli medya manifestini oluşturabilir', setDoc(manifest, manifestDoc), true);
  await test('A kendi manifestini okuyabilir', getDoc(manifest), true);
  await test('A kendi şifreli silme işaretini oluşturabilir', setDoc(marker, markerDoc), true);
  await test('A kendi silme işaretini okuyabilir', getDoc(marker), true);
  await test('B, A manifestini okuyamaz', getDoc(doc(fd(b), manifest.path)), false);
  await test('B, A marker koleksiyonunu listeleyemez',
    getDocs(collection(fd(b), `users/${userA}/encrypted_media_deletions`)), false);
  await test('Anonim kullanıcı A manifestini okuyamaz', getDoc(doc(fd(anon), manifest.path)), false);
  await test('A, B klasörüne yeni manifest yazamaz',
    setDoc(doc(fd(a), `users/${userB}/encrypted_media_entries/${'b'.repeat(64)}`),
      {...manifestDoc, ownerUid: userB, storagePath: bobPath}), false);
  await test('A eski silme işaretini değiştiremez', updateDoc(marker, {ownerHash: 'e'.repeat(64)}), false);
  await test('A eski silme işaretini silemez', deleteDoc(marker), false);
  await test('A manifesti güncelleyemez', updateDoc(manifest, {kind: 'SEASON_COVER'}), false);
  await test('A manifesti kendisi silemez', deleteDoc(manifest), false);
  await test('A sunucu silme izni okuyamaz', getDoc(doc(fd(a), serverPermit)), false);
  await test('B sunucu silme izni yazamaz',
    setDoc(doc(fd(b), serverPermit), {status: 'prepared'}), false);
  await test('A sunucu audit kaydı okuyamaz', getDoc(doc(fd(a), serverAudit)), false);
  await test('A sunucu audit kaydı oluşturamaz', setDoc(doc(fd(a), serverAudit), {status: 'deleted'}), false);
  await test('A, kendisi adına olsa da eksik marker metadata yükleyemez',
    setDoc(doc(fd(a), `users/${userA}/encrypted_media_deletions/${'f'.repeat(64)}`),
      {formatVersion: 3, ownerUid: userA, createdAt: serverTimestamp()}), false);
  await test('A şifresiz /users kökü yazamaz', setDoc(doc(fd(a), 'users/foreign'), {public: true}), false);
  await test('A şifreli journal olayını oluşturabilir',
    setDoc(doc(fd(a), `users/${userA}/journal_change_events/${'1'.repeat(64)}`), {
      formatVersion: 1, ownerUid: userA, journalHash: '1'.repeat(64),
      operation: 'UPSERT', revision: 1, encryptedPayload: 'ENC_' + 'X'.repeat(90),
      uploadedAt: serverTimestamp(),
    }), true);
  await test('B, A journal olayını okuyamaz',
    getDoc(doc(fd(b), `users/${userA}/journal_change_events/${'1'.repeat(64)}`)), false);
  await test('A, eski journal olayını silemez',
    deleteDoc(doc(fd(a), `users/${userA}/journal_change_events/${'1'.repeat(64)}`)), false);
  await test('A bulut arşiv parçasını parent manifestten önce oluşturabilir',
    setDoc(doc(fd(a), `users/${userA}/cloud_archives/archive-smoke/parts/part_0000`),
      {formatVersion: 1, index: 0, data: 'sealed-payload-fragment'}), true);

  // Storage: bir kullanıcının ilk şifreli dosyayı yaratması gerekiyor.
  const sample = new Uint8Array(96).fill(17);
  const aFile = ref(st(a), manifestPath);
  const bFile = ref(st(b), manifestPath);
  const anonymousFile = ref(st(anon), manifestPath);
  await test('A kendi application/octet-stream şifreli dosyasını yükleyebilir',
    uploadBytes(aFile, sample, {contentType: 'application/octet-stream'}), true);
  await test('A kendi dosyasının metadata bilgisini okuyabilir', getMetadata(aFile), true);
  await test('B, A dosya metadata bilgisini okuyamaz', getMetadata(bFile), false);
  await test('Anonim kullanıcı A dosya metadata bilgisini okuyamaz', getMetadata(anonymousFile), false);
  await test('A başkasının Storage klasörüne şifreli dosya yükleyemez',
    uploadBytes(ref(st(a), bobPath), sample, {contentType: 'application/octet-stream'}), false);
  await test('A kendi şifreli dosyasını doğrudan silemez', deleteObject(aFile), false);
  await test('A şifreli dosya üzerine yeniden yazamaz',
    uploadBytes(aFile, sample, {contentType: 'application/octet-stream'}), false);
  await test('A şifresiz image/jpeg yükleyemez',
    uploadBytes(ref(st(a), `users/${userA}/encrypted_media_v1/${'e'.repeat(64)}.bin`),
      sample, {contentType: 'image/jpeg'}), false);
  await test('A yanlış uzantılı dosya oluşturamaz',
    uploadBytes(ref(st(a), `users/${userA}/encrypted_media_v1/photo.jpg`),
      sample, {contentType: 'application/octet-stream'}), false);
  await test('A kendi Storage klasörünü listeleyebilir',
    list(ref(st(a), `users/${userA}/encrypted_media_v1`), {maxResults: 50}), true);
  await test('B, A Storage klasörünü listeleyemez',
    list(ref(st(b), `users/${userA}/encrypted_media_v1`), {maxResults: 50}), false);
  await test('Anonim kullanıcı A Storage klasörünü listeleyemez',
    list(ref(st(anon), `users/${userA}/encrypted_media_v1`), {maxResults: 50}), false);
} finally {
  await env.cleanup();
}

const passed = results.filter(([, ok]) => ok).length;
const failed = results.length - passed;
console.log(`\nFAZ 2H.2 — EMÜLATÖR İZİN RAPORU: ${passed} geçti / ${failed} başarısız`);
if (failed) {
  console.error('ÜRETİME DAĞITIM YAPMA. Hataları düzeltip emülatör doğrulamasını tekrar çalıştır.');
  process.exitCode = 1;
} else {
  console.log('Emülatör senaryoları geçti. CANLI kurallar, IAM ve App Check ayarları ayrıca doğrulanmalıdır.');
}
