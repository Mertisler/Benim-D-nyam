#!/usr/bin/env node
/**
 * Faz 2H.2 — Üretim ağına bağlanmayan, salt-okunur ön denetim.
 * Kasıtlı olarak yalnızca demo- proje ID'si kullanılır.
 */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const dir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(dir, '../..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const exists = p => fs.existsSync(path.join(root, p));
const issues = [];
function check(condition, message) {
  console.log(`${condition ? '[OK]' : '[HATA]'} ${message}`);
  if (!condition) issues.push(message);
}
const id = 'demo-benimdunyam-2h2';
const conf = JSON.parse(fs.readFileSync(path.join(dir, 'firebase.emulator.json'), 'utf8'));
check(id.startsWith('demo-'), 'Sanal ve gerçek bir Firebase projesine karşılık gelmeyen demo kimliği');
check(conf.emulators?.ui?.enabled === false &&
  conf.emulators?.firestore?.host === '127.0.0.1' &&
  conf.emulators?.storage?.host === '127.0.0.1', 'Emülatörler yalnızca localhost üzerinde');
for (const [field, file] of [['firestore', 'firebase/security/firestore.rules'], ['storage', 'firebase/security/storage.rules']]) {
  check(exists(file), `Faz 2H.1 ${field} kural adayı mevcut`);
  // Emülatör konfigürasyonu, proje dizini DIŞINA çıkmadan yerel kopyayı kullanır.
  const emulatorRulesPath = `firebase/security/${field}.rules`;
  check(conf[field]?.rules === emulatorRulesPath,
    `Emülatör ${field} kurallarını kendi dizininden okuyor`);
  // Test kopyası ile asıl güvenlik kuralı farklıysa yanıltıcı bir başarı sonucu verme.
  const sourceRules = path.join(root, file);
  const localRules = path.join(dir, emulatorRulesPath);
  check(fs.existsSync(sourceRules) && fs.existsSync(localRules) &&
    fs.readFileSync(sourceRules, 'utf8') === fs.readFileSync(localRules, 'utf8'),
    `Emülatör ${field} kopyası asıl aday kuralla birebir aynı`);
}
if (exists('firebase/security/firestore.rules')) {
  const src = read('firebase/security/firestore.rules');
  check(src.includes('encrypted_media_deletions') && src.includes('_server_media_cleanup_permits_v2') &&
    src.includes('_server_media_cleanup_audit'), 'Firestore tombstone ve sunucu koleksiyonları aday dosyada var');
  check(!/allow\s+[^;]*\s*:\s*if\s+true\s*;/m.test(src), 'Firestore kural adayında koşulsuz allow yok');
}
if (exists('firebase/security/storage.rules')) {
  const src = read('firebase/security/storage.rules');
  check(src.includes('encrypted_media_v1') && src.includes('allow update, delete: if false'),
    'Storage silme ve üzerine yazma kural adayında kapalı');
  check(!/allow\s+[^;]*\s*:\s*if\s+true\s*;/m.test(src), 'Storage kural adayında koşulsuz allow yok');
}
const project = process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT || '';
check(!project || project === id, 'Gerçek bulut projesi ortam değişkeninden alınmayacak');
const env = path.join(root, 'functions/.env');
if (fs.existsSync(env)) {
  const cfg = fs.readFileSync(env, 'utf8');
  check(!/^\s*BD_CLEANUP_V2_ENABLED\s*=\s*["']?true["']?\s*$/mi.test(cfg),
    'Functions .env içinde canlı silme açık değil');
}
console.log(`\nÖn kontrol: ${issues.length === 0 ? 'GEÇTİ' : 'BAŞARISIZ'} (${issues.length} sorun).`);
console.log('Bu adım canlı Firestore kuralları, IAM ve App Check Console ayarlarını doğrulamaz.');
if (issues.length) process.exitCode = 1;
