#!/usr/bin/env node
/**
 * Firebase Security Rules — kapsam genişletme senaryoları.
 *
 * verify-rules.mjs temel sahiplik kontrollerini sınar. Bu dosya:
 *  - Uygulamanın (Kotlin) GERÇEKTE yazdığı alan kümelerinin kabul edildiğini,
 *  - Kurallardaki her koleksiyonun (cloud_archives, sync_dependency_snapshots,
 *    encrypted_photo_layouts, _meta/connection...) kapsandığını,
 *  - Alan doğrulamalarının (sahip, sürüm, sunucu zamanı, fazladan alan,
 *    boyut ve aralık sınırları) tek tek çalıştığını
 * doğrular.
 *
 * Sadece `demo-benimdunyam-2h2` adlı YEREL emülatörde çalışır; gerçek projeye bağlanmaz.
 */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {initializeTestEnvironment, assertSucceeds, assertFails} from '@firebase/rules-unit-testing';
import {
  doc, collection, getDoc, getDocs, setDoc, updateDoc, deleteDoc,
  serverTimestamp, Timestamp, writeBatch,
} from 'firebase/firestore';
import {ref, uploadBytes, getMetadata} from 'firebase/storage';

const dir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(dir, '../..');
const projectId = 'demo-benimdunyam-2h2';
const bucket = `${projectId}.appspot.com`;
const actualProject = process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT;
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
const storageHost = process.env.FIREBASE_STORAGE_EMULATOR_HOST;
const local = /^(?:127\.0\.0\.1|localhost):\d{2,5}$/;

if ((actualProject && actualProject !== projectId) ||
    !local.test(firestoreHost || '') || !local.test(storageHost || '')) {
  console.error('GÜVENLİK KİLİDİ: İki localhost emülatörünü birlikte başlatmadan bu dosya çalışmaz.');
  process.exit(2);
}

const rules = key => fs.readFileSync(path.join(root, `firebase/security/${key}.rules`), 'utf8');
const env = await initializeTestEnvironment({
  projectId,
  firestore: {host: '127.0.0.1', port: 18080, rules: rules('firestore')},
  storage: {host: '127.0.0.1', port: 19199, rules: rules('storage')},
});

const A = 'alice_cov';
const B = 'bob_cov';
const a = env.authenticatedContext(A);
const b = env.authenticatedContext(B);
const anon = env.unauthenticatedContext();
const fs_ = c => c.firestore();
const st = c => c.storage(`gs://${bucket}`);
const hex = ch => ch.repeat(64);
const clientTime = Timestamp.fromMillis(Date.now());

const results = [];
let currentGroup = '';
function group(name) {
  currentGroup = name;
  console.log(`\n— ${name}`);
}
async function test(title, action, allowed) {
  const label = `${currentGroup}: ${title}`;
  try {
    const promise = typeof action === 'function' ? action() : action;
    await (allowed ? assertSucceeds(promise) : assertFails(promise));
    results.push([label, true]);
    console.log(`[OK] ${title}`);
  } catch (err) {
    results.push([label, false]);
    console.error(`[HATA] ${title}: ${String(err.code || err.message || err).slice(0, 200)}`);
  }
}

/** Kurallardan bağımsız olarak, test için önceden belge yerleştirir. */
async function seed(pathName, data) {
  await env.withSecurityRulesDisabled(ctx => setDoc(doc(ctx.firestore(), pathName), data));
}

// ---------------------------------------------------------------------------
// Uygulamanın gönderdiği alan kümeleri (Kotlin kaynak koduyla birebir)
// ---------------------------------------------------------------------------

/** FirebaseCloudArchiveRepository.kt — arşiv manifesti */
const archiveManifest = () => ({
  formatVersion: 1, createdAt: serverTimestamp(), partCount: 2, encryptedBytes: 150000,
  sha256: hex('a'), journalCount: 10, peopleCount: 4, seasonCount: 2, episodeCount: 3,
  mentionCount: 12, specialMemoryCount: 1, moodCount: 7,
});
/** FirebaseCloudArchiveRepository.kt / SyncDependencyCoverageRepository.kt — parça */
const part = (index, data = 'sealed-fragment') => ({formatVersion: 1, index, data});
/** ManualJournalEventUploader.kt */
const journalEvent = uid => ({
  formatVersion: 1, ownerUid: uid, journalHash: hex('1'), operation: 'UPSERT',
  revision: 3, encryptedPayload: 'ENC_' + 'X'.repeat(200), uploadedAt: serverTimestamp(),
});
/** SyncDependencyCoverageRepository.kt — katalog manifesti */
const catalogManifest = uid => ({
  formatVersion: 1, ownerUid: uid, createdAt: serverTimestamp(),
  capturedAtMillis: 1_763_000_000_000, partCount: 3, encodedLength: 250000, sha256: hex('b'),
});
/** EncryptedMediaSyncRepository.kt */
const mediaEntry = (uid, id) => ({
  formatVersion: 1, ownerUid: uid, storagePath: `users/${uid}/encrypted_media_v1/${id}.bin`,
  kind: 'JOURNAL_PHOTO', ownerHash: hex('c'), fileNameHash: hex('d'), createdAt: serverTimestamp(),
});
/** EncryptedPhotoLayoutSyncRepository.kt */
const layout = uid => ({
  formatVersion: 2, ownerUid: uid, ownerHash: hex('e'),
  encryptedPayload: 'cipher_' + 'y'.repeat(120), createdAt: serverTimestamp(),
});
/** EncryptedMediaDeletionRepository.kt */
const deletion = uid => ({
  formatVersion: 3, ownerUid: uid, ownerHash: hex('c'), fileNameHash: hex('d'),
  encryptedPayload: 'cipherv3_' + 'x'.repeat(80), createdAt: serverTimestamp(),
});

/** Bir alanı değiştirilmiş / çıkarılmış / eklenmiş kopya. */
const withField = (obj, key, value) => ({...obj, [key]: value});
const without = (obj, key) => { const c = {...obj}; delete c[key]; return c; };

try {
  await env.clearFirestore();
  await env.clearStorage();

  // -------------------------------------------------------------------------
  group('_meta/connection (bağlantı sağlık kontrolü)');
  const conn = id => doc(fs_(id), `users/${id === a ? A : B}/_meta/connection`);
  await test('Sahip, belge olmasa bile okuyabilir', () => getDoc(conn(a)), true);
  await test('Başka kullanıcı okuyamaz', () => getDoc(doc(fs_(b), `users/${A}/_meta/connection`)), false);
  await test('Anonim okuyamaz', () => getDoc(doc(fs_(anon), `users/${A}/_meta/connection`)), false);
  await test('Sahip yazamaz', () => setDoc(conn(a), {ok: true}), false);
  await test('Sahip listeleyemez', () => getDocs(collection(fs_(a), `users/${A}/_meta`)), false);
  await test('_meta altındaki başka belge okunamaz', () => getDoc(doc(fs_(a), `users/${A}/_meta/other`)), false);

  // -------------------------------------------------------------------------
  group('cloud_archives (eski tam arşiv)');
  const archivePath = `users/${A}/cloud_archives/arc-1`;
  const archive = doc(fs_(a), archivePath);
  await test('Uygulamanın parça yazımı (toplu, 2 parça) kabul edilir', () => {
    const batch = writeBatch(fs_(a));
    batch.set(doc(fs_(a), `${archivePath}/parts/part_0000`), part(0));
    batch.set(doc(fs_(a), `${archivePath}/parts/part_0001`), part(1));
    return batch.commit();
  }, true);
  await test('Uygulamanın manifest yazımı kabul edilir', () => setDoc(archive, archiveManifest()), true);
  await test('Sahip manifesti ve parçaları okuyabilir', () => getDocs(collection(fs_(a), `${archivePath}/parts`)), true);
  await test('Sahip arşivleri listeleyebilir', () => getDocs(collection(fs_(a), `users/${A}/cloud_archives`)), true);
  await test('Başka kullanıcı manifesti okuyamaz', () => getDoc(doc(fs_(b), archivePath)), false);
  await test('Başka kullanıcı parçaları listeleyemez', () => getDocs(collection(fs_(b), `${archivePath}/parts`)), false);
  await test('Arşiv değiştirilemez', () => updateDoc(archive, {journalCount: 0}), false);
  await test('Arşiv silinemez', () => deleteDoc(archive), false);
  await test('Parça değiştirilemez', () => updateDoc(doc(fs_(a), `${archivePath}/parts/part_0000`), {data: 'x'}), false);
  await test('Parça silinemez', () => deleteDoc(doc(fs_(a), `${archivePath}/parts/part_0000`)), false);
  const arc2 = n => doc(fs_(a), `users/${A}/cloud_archives/arc-${n}`);
  await test('İstemci saatiyle createdAt reddedilir', () => setDoc(arc2(2), withField(archiveManifest(), 'createdAt', clientTime)), false);
  await test('Fazladan alan reddedilir', () => setDoc(arc2(3), withField(archiveManifest(), 'plainTitle', 'Gizli')), false);
  await test('Eksik alan reddedilir', () => setDoc(arc2(4), without(archiveManifest(), 'moodCount')), false);
  await test('Bilinmeyen formatVersion reddedilir', () => setDoc(arc2(5), withField(archiveManifest(), 'formatVersion', 2)), false);
  await test('partCount 0 reddedilir', () => setDoc(arc2(6), withField(archiveManifest(), 'partCount', 0)), false);
  await test('partCount 121 reddedilir', () => setDoc(arc2(7), withField(archiveManifest(), 'partCount', 121)), false);
  await test('10 MB üstü encryptedBytes reddedilir', () => setDoc(arc2(8), withField(archiveManifest(), 'encryptedBytes', 10000001)), false);
  await test('Geçersiz sha256 reddedilir', () => setDoc(arc2(9), withField(archiveManifest(), 'sha256', 'ABC')), false);
  await test('Sayı alanına metin reddedilir', () => setDoc(arc2(10), withField(archiveManifest(), 'journalCount', '10')), false);
  await test('Geçersiz parça adı reddedilir', () => setDoc(doc(fs_(a), `${archivePath}/parts/part_1`), part(1)), false);
  await test('Parça index 120 reddedilir', () => setDoc(doc(fs_(a), `${archivePath}/parts/part_0120`), part(120)), false);
  await test('100 KB üstü parça reddedilir', () => setDoc(doc(fs_(a), `${archivePath}/parts/part_0002`), part(2, 'z'.repeat(100001))), false);
  await test('Boş parça reddedilir', () => setDoc(doc(fs_(a), `${archivePath}/parts/part_0003`), part(3, '')), false);
  await test('Başkasının arşivine parça yazılamaz', () => setDoc(doc(fs_(a), `users/${B}/cloud_archives/x/parts/part_0000`), part(0)), false);

  // -------------------------------------------------------------------------
  group('journal_change_events (şifreli günlük olayları)');
  const ev = n => doc(fs_(a), `users/${A}/journal_change_events/${hex(String(n))}`);
  await test('Uygulamanın olay yazımı kabul edilir', () => setDoc(ev(2), journalEvent(A)), true);
  await test('DELETE olayı kabul edilir', () => setDoc(ev(3), withField(journalEvent(A), 'operation', 'DELETE')), true);
  await test('Sahip olayları listeleyebilir', () => getDocs(collection(fs_(a), `users/${A}/journal_change_events`)), true);
  await test('Başka kullanıcı listeleyemez', () => getDocs(collection(fs_(b), `users/${A}/journal_change_events`)), false);
  await test('Olay değiştirilemez', () => updateDoc(ev(2), {revision: 4}), false);
  await test('ownerUid başka hesap olamaz', () => setDoc(ev(4), withField(journalEvent(A), 'ownerUid', B)), false);
  await test('Bilinmeyen işlem reddedilir', () => setDoc(ev(5), withField(journalEvent(A), 'operation', 'MERGE')), false);
  await test('revision 0 reddedilir', () => setDoc(ev(6), withField(journalEvent(A), 'revision', 0)), false);
  await test('Ondalıklı revision reddedilir', () => setDoc(ev(7), withField(journalEvent(A), 'revision', 1.5)), false);
  await test('Geçersiz journalHash reddedilir', () => setDoc(ev(8), withField(journalEvent(A), 'journalHash', 'j1')), false);
  await test('İstemci saatiyle uploadedAt reddedilir', () => setDoc(ev(9), withField(journalEvent(A), 'uploadedAt', clientTime)), false);
  await test('Şifresiz metin alanı eklenemez', () => setDoc(doc(fs_(a), `users/${A}/journal_change_events/${hex('a')}`), withField(journalEvent(A), 'content', 'Açık metin')), false);
  await test('600 KB üstü şifreli içerik reddedilir', () => setDoc(doc(fs_(a), `users/${A}/journal_change_events/${hex('b')}`), withField(journalEvent(A), 'encryptedPayload', 'X'.repeat(600001))), false);
  await test('Boş şifreli içerik reddedilir', () => setDoc(doc(fs_(a), `users/${A}/journal_change_events/${hex('c')}`), withField(journalEvent(A), 'encryptedPayload', '')), false);

  // -------------------------------------------------------------------------
  group('sync_dependency_snapshots (şifreli ilişki kataloğu)');
  const catPath = `users/${A}/sync_dependency_snapshots/cat-1`;
  await test('Uygulamanın katalog parçaları kabul edilir', () => {
    const batch = writeBatch(fs_(a));
    for (let i = 0; i < 3; i++) batch.set(doc(fs_(a), `${catPath}/parts/part_000${i}`), part(i));
    return batch.commit();
  }, true);
  await test('Uygulamanın katalog manifesti kabul edilir', () => setDoc(doc(fs_(a), catPath), catalogManifest(A)), true);
  await test('Sahip katalogları listeleyebilir', () => getDocs(collection(fs_(a), `users/${A}/sync_dependency_snapshots`)), true);
  await test('Başka kullanıcı kataloğu okuyamaz', () => getDoc(doc(fs_(b), catPath)), false);
  await test('Başka kullanıcı parçaları okuyamaz', () => getDocs(collection(fs_(b), `${catPath}/parts`)), false);
  await test('Katalog değiştirilemez', () => updateDoc(doc(fs_(a), catPath), {partCount: 1}), false);
  await test('Katalog silinemez', () => deleteDoc(doc(fs_(a), catPath)), false);
  const cat = n => doc(fs_(a), `users/${A}/sync_dependency_snapshots/cat-${n}`);
  await test('ownerUid başka hesap olamaz', () => setDoc(cat(2), withField(catalogManifest(A), 'ownerUid', B)), false);
  await test('partCount 81 reddedilir', () => setDoc(cat(3), withField(catalogManifest(A), 'partCount', 81)), false);
  await test('8 MB üstü encodedLength reddedilir', () => setDoc(cat(4), withField(catalogManifest(A), 'encodedLength', 8000001)), false);
  await test('capturedAtMillis 0 reddedilir', () => setDoc(cat(5), withField(catalogManifest(A), 'capturedAtMillis', 0)), false);
  await test('Katalog parçası index 80 reddedilir', () => setDoc(doc(fs_(a), `${catPath}/parts/part_0080`), part(80)), false);

  // -------------------------------------------------------------------------
  group('encrypted_media_entries (şifreli medya manifesti)');
  const mid = hex('7');
  await test('Uygulamanın manifest yazımı kabul edilir', () => setDoc(doc(fs_(a), `users/${A}/encrypted_media_entries/${mid}`), mediaEntry(A, mid)), true);
  for (const kind of ['SEASON_COVER', 'PERSON_PROFILE', 'PERSON_COVER']) {
    const id = hex(kind === 'SEASON_COVER' ? '8' : kind === 'PERSON_PROFILE' ? '9' : 'f');
    await test(`${kind} türü kabul edilir`, () => setDoc(doc(fs_(a), `users/${A}/encrypted_media_entries/${id}`), withField(mediaEntry(A, id), 'kind', kind)), true);
  }
  await test('Bilinmeyen tür reddedilir', () => setDoc(doc(fs_(a), `users/${A}/encrypted_media_entries/${hex('0')}`), withField(mediaEntry(A, hex('0')), 'kind', 'VIDEO')), false);
  await test('storagePath belge kimliğiyle uyuşmazsa reddedilir', () => setDoc(doc(fs_(a), `users/${A}/encrypted_media_entries/${hex('2')}`), mediaEntry(A, hex('3'))), false);
  await test('storagePath başka hesabı gösteremez', () => setDoc(doc(fs_(a), `users/${A}/encrypted_media_entries/${hex('4')}`), withField(mediaEntry(A, hex('4')), 'storagePath', `users/${B}/encrypted_media_v1/${hex('4')}.bin`)), false);
  await test('Belge kimliği hex64 olmalı', () => setDoc(doc(fs_(a), `users/${A}/encrypted_media_entries/foto1`), mediaEntry(A, 'foto1')), false);

  // -------------------------------------------------------------------------
  group('encrypted_photo_layouts (şifreli fotoğraf düzeni)');
  const lay = id => doc(fs_(a), `users/${A}/encrypted_photo_layouts/${id}`);
  await test('Uygulamanın düzen yazımı kabul edilir', () => setDoc(lay(hex('1')), layout(A)), true);
  await test('Sahip okuyabilir', () => getDoc(lay(hex('1'))), true);
  await test('Başka kullanıcı okuyamaz', () => getDoc(doc(fs_(b), `users/${A}/encrypted_photo_layouts/${hex('1')}`)), false);
  await test('Başka kullanıcı listeleyemez', () => getDocs(collection(fs_(b), `users/${A}/encrypted_photo_layouts`)), false);
  await test('Düzen değiştirilemez', () => updateDoc(lay(hex('1')), {formatVersion: 2}), false);
  await test('Düzen silinemez', () => deleteDoc(lay(hex('1'))), false);
  await test('Eski formatVersion 1 reddedilir', () => setDoc(lay(hex('2')), withField(layout(A), 'formatVersion', 1)), false);
  await test('ownerUid başka hesap olamaz', () => setDoc(lay(hex('3')), withField(layout(A), 'ownerUid', B)), false);
  await test('Belge kimliği hex64 olmalı', () => setDoc(lay('j1'), layout(A)), false);
  await test('500 KB üstü içerik reddedilir', () => setDoc(lay(hex('4')), withField(layout(A), 'encryptedPayload', 'y'.repeat(500001))), false);

  // -------------------------------------------------------------------------
  group('encrypted_media_deletions (şifreli silme işaretleri)');
  const del = id => doc(fs_(a), `users/${A}/encrypted_media_deletions/${id}`);
  await test('Uygulamanın işaret yazımı kabul edilir', () => setDoc(del(hex('5')), deletion(A)), true);
  await test('Çok kısa şifreli içerik (50 karakter) reddedilir', () => setDoc(del(hex('6')), withField(deletion(A), 'encryptedPayload', 'x'.repeat(50))), false);
  await test('Eski formatVersion 2 reddedilir', () => setDoc(del(hex('7')), withField(deletion(A), 'formatVersion', 2)), false);
  await test('İstemci saatiyle createdAt reddedilir', () => setDoc(del(hex('8')), withField(deletion(A), 'createdAt', clientTime)), false);

  // -------------------------------------------------------------------------
  group('Tanımsız yollar (varsayılan ret)');
  await test('Kullanıcı kök belgesi okunamaz', () => getDoc(doc(fs_(a), `users/${A}`)), false);
  await test('Kullanıcı kök belgesi yazılamaz', () => setDoc(doc(fs_(a), `users/${A}`), {name: 'A'}), false);
  await test('Bilinmeyen alt koleksiyon yazılamaz', () => setDoc(doc(fs_(a), `users/${A}/journals/j1`), {title: 'Açık'}), false);
  await test('Bilinmeyen alt koleksiyon okunamaz', () => getDoc(doc(fs_(a), `users/${A}/profile/main`)), false);
  await test('Kök seviyede koleksiyon yazılamaz', () => setDoc(doc(fs_(a), 'public/x'), {a: 1}), false);
  await test('Tüm kullanıcılar listelenemez', () => getDocs(collection(fs_(a), 'users')), false);
  await seed(`users/${A}/journal_change_events/${hex('d')}`, journalEvent(A));
  await test('Sunucu tarafından yazılmış olay bile istemciden silinemez', () => deleteDoc(doc(fs_(a), `users/${A}/journal_change_events/${hex('d')}`)), false);

  // -------------------------------------------------------------------------
  group('Storage (şifreli medya dosyaları)');
  const file = (ctx, uid, id) => ref(st(ctx), `users/${uid}/encrypted_media_v1/${id}.bin`);
  const octet = {contentType: 'application/octet-stream'};
  await test('49 bayt (en küçük geçerli) yüklenebilir', () => uploadBytes(file(a, A, hex('1')), new Uint8Array(49), octet), true);
  await test('48 bayt reddedilir', () => uploadBytes(file(a, A, hex('2')), new Uint8Array(48), octet), false);
  await test('Üst sınır (26247168 bayt) yüklenebilir', () => uploadBytes(file(a, A, hex('3')), new Uint8Array(26247168), octet), true);
  await test('Üst sınırdan 1 bayt fazlası reddedilir', () => uploadBytes(file(a, A, hex('4')), new Uint8Array(26247169), octet), false);
  // İçerik türü verilmezse SDK bayt dizisine application/octet-stream atar;
  // kural bunu kabul eder. Kritik olan, dosyanın image/* olarak saklanamamasıdır.
  await test('İçerik türü verilmezse octet-stream olarak saklanır', async () => {
    const result = await uploadBytes(file(a, A, hex('5')), new Uint8Array(100));
    if (result.metadata.contentType !== 'application/octet-stream') {
      throw new Error(`Beklenmeyen içerik türü: ${result.metadata.contentType}`);
    }
  }, true);
  await test('text/plain içerik türü reddedilir', () => uploadBytes(file(a, A, hex('9')), new Uint8Array(100), {contentType: 'text/plain'}), false);
  await test('Büyük harfli hex dosya adı reddedilir', () => uploadBytes(ref(st(a), `users/${A}/encrypted_media_v1/${'A'.repeat(64)}.bin`), new Uint8Array(100), octet), false);
  await test('Anonim yükleyemez', () => uploadBytes(file(anon, A, hex('6')), new Uint8Array(100), octet), false);
  await test('Klasör dışına (kullanıcı kökü) yüklenemez', () => uploadBytes(ref(st(a), `users/${A}/foto.bin`), new Uint8Array(100), octet), false);
  await test('Başka klasöre yüklenemez', () => uploadBytes(ref(st(a), `users/${A}/public/${hex('7')}.bin`), new Uint8Array(100), octet), false);
  await test('Kök dizine yüklenemez', () => uploadBytes(ref(st(a), `${hex('8')}.bin`), new Uint8Array(100), octet), false);
  await test('Geçersiz adlı dosyanın metadatası okunamaz', () => getMetadata(ref(st(a), `users/${A}/encrypted_media_v1/x.bin`)), false);
} finally {
  await env.cleanup();
}

const passed = results.filter(([, ok]) => ok).length;
const failed = results.length - passed;
console.log(`\nKURAL KAPSAM RAPORU: ${passed} geçti / ${failed} başarısız`);
if (failed) {
  console.error('Başarısız senaryolar:');
  results.filter(([, ok]) => !ok).forEach(([t]) => console.error(`  - ${t}`));
  console.error('ÜRETİME DAĞITIM YAPMA. Kural veya beklentiyi gözden geçir.');
  process.exitCode = 1;
}
