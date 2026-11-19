#!/usr/bin/env node
/**
 * Faz 2H.2 — V2 Cloud Functions kodu için yalnızca kaynak denetimi.
 * IAM, App Check token kabulü, üretim deploy'u ve GCS davranışını kanıtlamaz.
 * Çıkış kodu: 0 = kaynak kontrolleri geçti; 1 = zorunlu kontrol başarısız.
 */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const p = path.join(root, 'functions/mediaCleanupVerifiedV2.js');
const failed = [];
const ok = (cond, message) => {
  console.log(`${cond ? '[OK]' : '[HATA]'} ${message}`);
  if (!cond) failed.push(message);
};
if (!fs.existsSync(p)) {
  console.error('[HATA] functions/mediaCleanupVerifiedV2.js kaynak dosyası bulunamadı. Faz 2G.4C eksik.');
  process.exit(1);
}
const src = fs.readFileSync(p, 'utf8');
ok(src.includes('enforceAppCheck: true'), 'Callable tanımlarında App Check zorunlu');
ok(src.includes('request.auth?.uid'), 'Firebase Auth kimliği isteniyor');
ok(src.includes('process.env.BD_CLEANUP_V2_ENABLED !== "true"'), 'Fiziksel silme varsayılan olarak kapalı');
ok(src.includes('process.env.BD_MEDIA_BUCKET'), 'Hedef bucket çevre değişkenine bağlı');
ok(src.includes('encrypted_media_deletions') && src.includes('encrypted_media_entries'), 'Silme ve medya manifesti karşılaştırılıyor');
ok(src.includes('twins.size !== 1'), 'Medya manifestinde tekillik kontrolü var');
ok(src.includes('const KEEP_MS = 7 * 24 * 60 * 60 * 1000'), 'Yedi günlük bekleme süresi');
ok(src.includes('const PERMIT_MS = 2 * 60 * 1000'), 'Kısa ömürlü işlem izni');
ok(src.includes('status: "claimed"'), 'Fiziksel silmeden önce tek kullanımlık izin claim ediliyor');
ok(src.includes('ifGenerationMatch: o.generation') && src.includes('ifMetagenerationMatch:'),
  'GCS generation ve metageneration silme önkoşulları');
ok(src.includes('immutableDeletionRecordId') && src.includes('confirmation !== "TEMİZLE"'),
  'Kullanıcı onayı ve marker kimliği kontrol ediliyor');
ok(src.includes('needs_manual_review'), 'Yarıda kalan silme manuel incelemeye düşüyor');
const index = path.join(root, 'functions/index.js');
if (!fs.existsSync(index)) {
  console.log('[BİLGİ] functions/index.js mevcut değil. Örnek export dosyası deploy kanıtı sayılamaz.');
} else {
  const code = fs.readFileSync(index, 'utf8');
  ok(['bdMediaCleanupStatusV2','bdMediaCleanupPrepareV2','bdMediaCleanupExecuteV2']
    .every(x => code.includes(x)), 'Gerçek functions/index.js içinde 3 export var');
}
for (const file of [p, ...(fs.existsSync(index) ? [index] : [])]) {
  // Node.js parse kontrolü bu dosyayı çalıştırmadan `node --check` üzerinden ayrıca yapılır.
  ok(!/admin\.(?:storage|firestore)\(\)\.delete\(/.test(fs.readFileSync(file,'utf8')),
    'İncelenen JS dosyasında kontrolsüz doğrudan admin silme zinciri görünmüyor');
}
console.log(`\nKaynak kontrolü: ${failed.length ? 'BAŞARISIZ' : 'GEÇTİ'}. Canlı IAM ve Function yetkileri ayrıca kontrol edilmeli.`);
if (failed.length) process.exitCode = 1;
