#!/usr/bin/env node
/**
 * Benim Dünyam — Faz 2H.3 — Canlı Firebase SALT OKUNUR denetimi.
 *
 * Ağ erişimi: yalnızca Google'ın REST GET uç noktaları.
 * gcloud: auth print-access-token, projects describe/get-iam-policy,
 * functions describe, run services get-iam-policy, storage buckets get-iam-policy.
 *
 * ASLA deploy, set, patch, delete, create, invoke veya veri listesi/okuması YAPMAZ.
 * Erişilemeyen kanıtları OK değil BİLİNMİYOR olarak raporlar.
 * OAuth belirteçleri yalnızca bellekte kalır, çıktı dosyasına eklenmez.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '../..');
const TARGET_FUNCTIONS = [
  'bdMediaCleanupStatusV2',
  'bdMediaCleanupPrepareV2',
  'bdMediaCleanupExecuteV2',
];
const SERVICE_IDS = [
  ['firestore.googleapis.com', 'Firestore App Check'],
  ['firebasestorage.googleapis.com', 'Storage App Check'],
  ['identitytoolkit.googleapis.com', 'Authentication App Check'],
];
const WIDE_ROLES = new Set([
  'roles/owner', 'roles/editor', 'roles/storage.admin',
  'roles/datastore.owner', 'roles/firebase.admin',
  'roles/iam.serviceAccountTokenCreator',
]);
const results = [];
const add = (status, name, detail, action = '') => {
  results.push({ status, name, detail, action });
  const mark = status === 'OK' ? '[OK]' : status === 'İNCELE' ? '[İNCELE]' : '[BİLİNMİYOR]';
  console.log(`${mark} ${name}: ${detail}`);
};

function usage() {
  console.log(`\nBenim Dünyam — Faz 2H.3 / salt okunur canlı Firebase güvenlik denetimi\n\nKullanım:\n  node live-readonly-audit.mjs --project=PROJE_ID --bucket=KOVA_ADI [--region=europe-west1] [--app-id=1:...:android:...]\n  node live-readonly-audit.mjs --project=PROJE_ID --bucket=KOVA_ADI --preview\n\nÖnkoşul: gcloud CLI kurulu ve 'gcloud auth login' ile giriş yapılmış olmalı.\nÇıktı: tools/security-2h3/reports/ altında SADECE özet JSON ve Markdown.\nÖNEMLİ: Bu araç Firebase'e HİÇBİR yapılandırma göndermez.\n`);
}
function options(argv) {
  if (argv.some(a => a === '--help' || a === '-h')) return { help: true };
  const out = { region: 'europe-west1', preview: false };
  for (const arg of argv) {
    if (arg === '--preview') { out.preview = true; continue; }
    const match = /^--(project|bucket|region|app-id)=(.+)$/.exec(arg);
    if (!match) throw new Error(`Bilinmeyen argüman: ${arg}`);
    const key = match[1] === 'app-id' ? 'appId' : match[1];
    if (out[key] && key !== 'region') throw new Error(`Tekrarlanan argüman: ${key}`);
    out[key] = match[2];
  }
  if (!out.project || !/^[a-z][a-z0-9-]{4,35}$/.test(out.project) || out.project.startsWith('demo-')) {
    throw new Error('Gerçek Firebase Project ID gerekli (--project=...). demo- kimlikleri canlı denetimde yasak.');
  }
  if (!out.bucket || !/^[a-z0-9][a-z0-9._-]{2,220}$/.test(out.bucket)) {
    throw new Error('Storage kova adı gerekli (--bucket=...). gs:// yazmayın.');
  }
  if (!/^[a-z][a-z0-9-]{2,35}$/.test(out.region)) throw new Error('Geçersiz bölge.');
  if (out.appId && !/^1:[0-9]+:android:[a-zA-Z0-9]+$/.test(out.appId)) {
    throw new Error('Firebase Android App ID biçimi 1:PROJE_NUMARASI:android:... olmalıdır.');
  }
  return out;
}

// Komutların listesi SABİT; kullanıcıdan gelen değerler üstte sıkı doğrulanır.
function gcloud(args) {
  const proc = spawnSync('gcloud', args, {
    encoding: 'utf8', timeout: 45_000, maxBuffer: 8 * 1024 * 1024,
    windowsHide: true, shell: process.platform === 'win32',
  });
  if (proc.error) throw new Error('gcloud çalıştırılamadı. Google Cloud CLI kurulu ve PATH üzerinde mi?');
  if (proc.status !== 0) throw new Error(`gcloud salt-okunur sorgu başarısız (kod ${proc.status}). IAM izni / kimlik / bölge kontrol edin.`);
  return (proc.stdout || '').trim();
}
function gjson(args) {
  const raw = gcloud([...args, '--format=json']);
  try { return JSON.parse(raw); } catch { throw new Error('gcloud JSON yanıtı çözülemedi.'); }
}
const normal = t => String(t).replace(/^\uFEFF/, '').replace(/\r\n/g, '\n');
const hash = t => createHash('sha256').update(normal(t), 'utf8').digest('hex');
function localRules(rel) {
  const file = path.resolve(ROOT, rel);
  if (!file.startsWith(ROOT + path.sep)) return null;
  if (!fs.existsSync(file)) return null;
  return fs.readFileSync(file, 'utf8');
}
function membersWithRole(policy, role) {
  return (policy?.bindings || []).filter(b => b.role === role)
    .flatMap(b => Array.isArray(b.members) ? b.members : []);
}
function policyRoles(policy, principal) {
  return (policy?.bindings || []).filter(b => (b.members || []).includes(principal)).map(b => b.role);
}
function statusError(e) {
  // OAuth anahtarı, yanıt gövdesi, e-posta ve diğer özel veriler rapora yazılmaz.
  if (e?.httpStatus) return `HTTP ${e.httpStatus}. Yetki veya API etkinliği kontrol edilmeli.`;
  return e instanceof Error ? e.message.replace(/Bearer\s+\S+/gi, '[GİZLİ]') : 'Okuma başarısız.';
}
async function requestGet(url, token) {
  if (!/^https:\/\/(firebaserules|firebaseappcheck)\.googleapis\.com\//.test(url)) {
    throw new Error('İzin verilmeyen API adresi.');
  }
  let response;
  try {
    response = await fetch(url, { method: 'GET', headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(18_000) });
  } catch { throw new Error('Google REST API erişimi zaman aşımı veya ağ hatası.'); }
  if (!response.ok) {
    const err = new Error('REST GET başarısız.'); err.httpStatus = response.status; throw err;
  }
  return response.json();
}
async function listReleases(project, token) {
  const all = [];
  let page = '';
  for (let i=0; i<20; i++) {
    const u = `https://firebaserules.googleapis.com/v1/projects/${project}/releases?pageSize=100${page ? `&pageToken=${encodeURIComponent(page)}` : ''}`;
    const obj = await requestGet(u, token);
    all.push(...(obj.releases || []));
    if (!obj.nextPageToken) return all;
    page = obj.nextPageToken;
  }
  throw new Error('Kural yayınları sayfalaması 20 sayfayı geçti; eksik envanterle karşılaştırma yapılmadı.');
}
async function rulesAudit(project, bucket, token) {
  let releases;
  try { releases = await listReleases(project, token); }
  catch (e) {
    for (const label of ['Firestore canlı kural karşılaştırması', 'Storage canlı kural karşılaştırması']) {
      add('BİLİNMİYOR', label, statusError(e));
    }
    return;
  }
  const checks = [
    { label: 'Firestore canlı kural karşılaştırması', keys: [`projects/${project}/releases/cloud.firestore`, `projects/${project}/releases/cloud.firestore/(default)`], file:'firebase/security/firestore.rules' },
    { label: 'Storage canlı kural karşılaştırması', keys: [`projects/${project}/releases/firebase.storage/${bucket}`, `projects/${project}/releases/firebase.storage`], file:'firebase/security/storage.rules' },
  ];
  for (const item of checks) {
    // Birden fazla release eşleşirse yanlış kuralı seçmek yerine fail-closed.
    const matching = releases.filter(r => item.keys.includes(r.name));
    if (matching.length !== 1) {
      add('BİLİNMİYOR', item.label, `Tam eşleşen yayın sayısı: ${matching.length}. Farklı database/bucket yayınları elle incelenmeli.`);
      continue;
    }
    const local = localRules(item.file);
    if (local === null) {
      add('BİLİNMİYOR', item.label, `Yerel kaynak dosya eksik: ${item.file}`); continue;
    }
    try {
      const release = matching[0];
      if (!/^projects\/[a-z0-9-]+\/rulesets\/[A-Za-z0-9_-]+$/.test(release.rulesetName || '')) {
        throw new Error('Canlı yayın ruleset adı beklenen biçimde değil.');
      }
      const ruleset = await requestGet(`https://firebaserules.googleapis.com/v1/${release.rulesetName}`, token);
      const files = ruleset.source?.files || [];
      if (files.length !== 1 || typeof files[0]?.content !== 'string') {
        add('BİLİNMİYOR', item.label, `${files.length} canlı kaynak dosyası var. Birden çok dosyada doğrudan hash karşılaştırması güvenilir değil.`);
        continue;
      }
      const same = hash(local) === hash(files[0].content);
      const preview = `${hash(local).slice(0,12)} / ${hash(files[0].content).slice(0,12)}`;
      add(same ? 'OK' : 'İNCELE', item.label,
        `${same ? 'Yerel aday ile canlı kaynak birebir eşleşiyor.' : 'Yerel aday ile canlı kaynak FARKLI.'} SHA-256 (kısa): ${preview}`,
        same ? '' : 'Canlı kodu körlemesine değiştirmeyin; iki kural kümesini güvenlik açısından karşılaştırın.');
      if (item.file.endsWith('storage.rules') && !/resource\s*==\s*null/.test(local)) {
        add('İNCELE', 'Storage ilk-yazma koruması', 'Yerel storage.rules dosyasında resource == null kontrolü görünmüyor.');
      }
    } catch(e) { add('BİLİNMİYOR', item.label, statusError(e)); }
  }
}

async function appCheckAudit(projectNumber, token, appId) {
  if (!/^\d+$/.test(String(projectNumber || ''))) {
    add('BİLİNMİYOR', 'App Check servisleri', 'GCP proje numarası okunamadı.');
    return;
  }
  for (const [service, title] of SERVICE_IDS) {
    try {
      const data = await requestGet(`https://firebaseappcheck.googleapis.com/v1/projects/${projectNumber}/services/${service}`, token);
      const mode = data.enforcementMode || 'BİLİNMİYOR';
      add(mode === 'ENFORCED' ? 'OK' : mode === 'UNENFORCED' || mode === 'OFF' ? 'İNCELE' : 'BİLİNMİYOR',
        title, `Canlı enforcementMode: ${mode}`,
        mode === 'ENFORCED' ? '' : 'Kullanıcı trafiği doğrulandıktan sonra kademeli enforcement planı hazırlanmalı.');
    } catch(e) { add('BİLİNMİYOR', title, statusError(e)); }
  }
  if (!appId) {
    add('BİLİNMİYOR', 'Android Play Integrity ayarı', '--app-id sağlanmadı; Firebase Android App ID gerekir (paket adı değil).');
    return;
  }
  if (appId.split(':')[1] !== String(projectNumber)) {
    add('İNCELE', 'Android Play Integrity ayarı', 'Girilen uygulama ID’si bu proje numarasıyla eşleşmiyor.');
    return;
  }
  try {
    const p = await requestGet(`https://firebaseappcheck.googleapis.com/v1/projects/${projectNumber}/apps/${encodeURIComponent(appId)}/playIntegrityConfig`, token);
    add('OK', 'Android Play Integrity kaydı', `Kayıt bulundu. Token TTL: ${p.tokenTtl || 'varsayılan (1 saat)'}.`,
      'Play Console imzalama SHA-256 kimliği ve Debug token erişimlerini Console üzerinden ayrıca kontrol edin.');
  } catch(e) { add('BİLİNMİYOR', 'Android Play Integrity kaydı', statusError(e)); }
  // Not: Callable Cloud Functions enforceAppCheck=true kaynak koduyla saptanır;
  // servis GET bu fonksiyonların CANLI sürümünü kanıtlamaz.
}

function functionAudit(project, region, bucket) {
  let projectPolicy;
  try { projectPolicy = gjson(['projects','get-iam-policy', project]); }
  catch(e) { add('BİLİNMİYOR','Proje IAM politikası',statusError(e)); }
  let bucketPolicy;
  try { bucketPolicy = gjson(['storage','buckets','get-iam-policy',`gs://${bucket}`]); }
  catch(e) { add('BİLİNMİYOR','Bucket IAM politikası',statusError(e)); }
  if (bucketPolicy) {
    const publicRoles = (bucketPolicy.bindings || []).filter(b =>
      (b.members || []).some(x => ['allUsers','allAuthenticatedUsers'].includes(x)))
      .map(b => b.role);
    add(publicRoles.length ? 'İNCELE' : 'OK', 'Storage bucket anonim IAM',
      publicRoles.length ? `Genel üyelere verilen roller var: ${publicRoles.join(', ')}` : 'Doğrudan bucket IAM içinde allUsers/allAuthenticatedUsers yok.',
      'Üst kaynak IAM mirası ve Uniform Bucket-Level Access ayrıca incelenmeli.');
  }
  for (const name of TARGET_FUNCTIONS) {
    let fn;
    try { fn = gjson(['functions','describe',name,'--gen2',`--project=${project}`,`--region=${region}`]); }
    catch(e) {
      add('BİLİNMİYOR',`Cloud Function ${name}`,`${statusError(e)} Fonksiyon dağıtılmamış da olabilir.`);
      continue;
    }
    const runtimeEmail = fn.serviceConfig?.serviceAccountEmail || '';
    const env = fn.serviceConfig?.environmentVariables || {};
    const active = fn.state === 'ACTIVE';
    add(active ? 'OK' : 'İNCELE', `Cloud Function ${name}`, `Bulundu. Durum: ${fn.state || 'BİLİNMİYOR'}, çalışma zamanı: ${fn.buildConfig?.runtime || 'BİLİNMİYOR'}.`,
      'Canlı deploy kodu ve callable Auth+App Check davranışı bu metadata ile kanıtlanmaz.');
    const cleanupOn = env.BD_CLEANUP_V2_ENABLED === 'true';
    add(cleanupOn ? 'İNCELE' : 'OK', `${name} fiziksel silme anahtarı`,
      cleanupOn ? 'BD_CLEANUP_V2_ENABLED=true (canlı temizleme açık olabilir).' : 'BD_CLEANUP_V2_ENABLED true değil; varsayılan kapalı.',
      cleanupOn ? 'Canlı silme kapalı tutulmalı; IAM, App Check ve canlı fonksiyon sürümü ayrıca doğrulanmalı.' : '');
    if (env.BD_MEDIA_BUCKET && env.BD_MEDIA_BUCKET !== bucket) {
      add('İNCELE', `${name} bucket hedefi`, 'Canlı fonksiyon bucket ayarı istenen kovayla farklı.');
    }
    if (!runtimeEmail) {
      add('BİLİNMİYOR', `${name} runtime IAM`, 'Çalışma zamanı hizmet hesabı okunamadı.');
    } else if (projectPolicy || bucketPolicy) {
      const principal = `serviceAccount:${runtimeEmail}`;
      const roles = [
        ...policyRoles(projectPolicy,principal), ...policyRoles(bucketPolicy,principal)
      ];
      const risky = [...new Set(roles.filter(x => WIDE_ROLES.has(x)))];
      add(risky.length ? 'İNCELE' : 'OK', `${name} runtime IAM`,
        risky.length ? `Geniş yetkili rol bulundu: ${risky.join(', ')}` : 'Doğrudan proje/bucket IAM içinde listelenen geniş roller görülmedi.',
        'IAM mirası, koşullu/özel roller, Cloud Run identity ve object access yetkileri ayrıca incelenmeli.');
    } else {
      add('BİLİNMİYOR', `${name} runtime IAM`, 'Proje/bucket IAM okuma yetkisi yok.');
    }
    const runPath = fn.serviceConfig?.service || '';
    const serviceName = runPath.split('/').pop();
    if (!/^[a-z][a-z0-9-]{1,60}$/.test(serviceName || '')) {
      add('BİLİNMİYOR', `${name} Cloud Run invoke IAM`, 'Cloud Run servis adı metadata içinde bulunamadı.');
      continue;
    }
    try {
      const run = gjson(['run','services','get-iam-policy',serviceName,`--project=${project}`,`--region=${region}`]);
      const publicMembers = ['allUsers','allAuthenticatedUsers'];
      const anyoneInvokes = publicMembers.some(x => membersWithRole(run,'roles/run.invoker').includes(x));
      add(anyoneInvokes ? 'İNCELE' : 'OK', `${name} Cloud Run invocation`,
        anyoneInvokes ? 'Herkese açık invoker binding var. Callable için bazen gerekli olabilir; Auth+App Check zorunluluğu canlı sürümde doğrulanmalı.' : 'Açık invoker binding görünmüyor; istemci erişiminin çalıştığı da ayrıca doğrulanmalı.');
    } catch(e) { add('BİLİNMİYOR', `${name} Cloud Run invocation`,statusError(e)); }
  }
}

function localSourceChecks() {
  const index = path.join(ROOT, 'functions', 'index.js');
  const example = path.join(ROOT, 'functions', 'index_2G4C_ORNEK.js');
  if (!fs.existsSync(index)) {
    add('İNCELE', 'Cloud Functions export bağlantısı', 'functions/index.js bulunamadı. Örnek export dosyası canlı deploy kanıtı değildir.',
      fs.existsSync(example) ? 'index_2G4C_ORNEK.js yalnız örnek; üretim index.js içinde üç export ayrıca kontrol edilmeli.' : 'functions/index.js kaynakta kontrol edilmeli.');
  } else {
    const code = fs.readFileSync(index,'utf8');
    const missing = TARGET_FUNCTIONS.filter(x => !code.includes(x));
    add(missing.length ? 'İNCELE' : 'OK','Cloud Functions export bağlantısı',
      missing.length ? `Kaynak index.js içinde eksik export adları: ${missing.join(', ')}` : 'Kaynak index.js içinde üç işlev adı var (canlı sürüm kanıtı değil).');
  }
  const rules = localRules('firebase/security/storage.rules');
  if (rules && !/resource\s*==\s*null/.test(rules)) {
    add('İNCELE','Yerel Storage tekrar yükleme koruması','Testte düzelttiğimiz resource == null koşulu kaynak kurallarda eksik.');
  }
}

function report(opt) {
  const dir = path.join(HERE, 'reports');
  fs.mkdirSync(dir, { recursive:true });
  const stamp = new Date().toISOString();
  const counts = Object.fromEntries(['OK','İNCELE','BİLİNMİYOR'].map(x=>[x,results.filter(r=>r.status===x).length]));
  // Kullanıcı/servis hesabı e-postaları, tam IAM politika dökümleri, kuralların içerikleri, OAuth token ASLA kaydedilmez.
  const payload = { formatVersion:1, generatedAtUtc:stamp, runType:'LIVE_READ_ONLY',
    projectAlias:`${opt.project.slice(0,3)}***`, region:opt.region, counts, results,
    disclaimer:'Bu rapor yalnızca belirtilen okuma yetkileriyle incelenebilen kaynakların anlık durumudur; üretim güvenliği için tam onay değildir.' };
  const md = [
    '# Benim Dünyam — Faz 2H.3 Canlı Firebase Güvenlik Ön Denetimi',
    '', `Oluşturma (UTC): ${stamp}`, 'Mod: **Salt okunur**; Firebase kaynaklarında değişiklik yapılmadı.',
    '', `**Özet:** ${counts.OK} OK / ${counts['İNCELE']} inceleme / ${counts['BİLİNMİYOR']} doğrulanamadı`,
    '', '| Kontrol | Sonuç | Kanıt / açıklama |', '|---|---|---|',
    ...results.map(r => `| ${mdSafe(r.name)} | ${r.status} | ${mdSafe(r.detail)} |`),
    '', '## Sınırlar',
    '- Cloud Functions canlı kaynak sürümü ve IAM mirasının tamamı bu raporla kanıtlanmaz.',
    '- App Check isteği reddetme davranışı ve Android Play imza kimlikleri ayrıca değerlendirilmelidir.',
    '- Firestore/Storage kural SHA-256 eşitliği, yalnızca belirtilen tek kaynak dosyası ve eşleşen release için geçerlidir.',
    '- `BİLİNMİYOR` sonucu **başarılı** sayılmaz.',
    '- Kural dağıtımı, fiziksel medya silme ve `BD_CLEANUP_V2_ENABLED=true` bu araç tarafından yapılmaz.',
    '', '## Kullanıcı tarafından değerlendirilmesi gerekenler',
    ...results.filter(x=>x.action).map(x=>`- **${mdSafe(x.name)}**: ${mdSafe(x.action)}`),
    '', 'Kaynak: Firebase Security Rules REST API, Firebase App Check REST API, Google Cloud CLI (salt okunur).',
  ].join('\n') + '\n';
  fs.writeFileSync(path.join(dir,'son-denetim-ozeti.json'), JSON.stringify(payload,null,2)+'\n','utf8');
  fs.writeFileSync(path.join(dir,'son-denetim-raporu.md'),md,'utf8');
  console.log(`\nRapor yazıldı: ${dir}`);
  console.log(`Özet: ${counts.OK} OK / ${counts['İNCELE']} İNCELE / ${counts['BİLİNMİYOR']} BİLİNMİYOR`);
  console.log('Tam canlı güvenlik onayı verilmedi; fiziksel silme kapalı kalmalı.');
  if (counts['İNCELE'] || counts['BİLİNMİYOR']) process.exitCode = 2;
}
function mdSafe(t) { return String(t).replace(/\|/g, '\\|').replace(/[\r\n]/g, ' '); }

async function main() {
  let opt;
  try { opt=options(process.argv.slice(2)); }
  catch(e) { console.error(`HATA: ${e.message}`); usage(); process.exitCode=1; return; }
  if (opt.help) { usage(); return; }
  if (opt.preview) {
    console.log('ÖNİZLEME — Kimlik bilgisi veya ağ sorgusu çalıştırılmadı.');
    console.log('Yalnızca şu okuma işlemleri çalışacak:');
    console.log('- gcloud auth print-access-token (geçici; dosyaya yazılmaz)');
    console.log('- gcloud projects describe / get-iam-policy');
    console.log('- gcloud storage buckets get-iam-policy');
    console.log('- gcloud functions describe (3 işlev)');
    console.log('- gcloud run services get-iam-policy');
    console.log('- Firebase Rules REST: releases.list + rulesets.get (GET)');
    console.log('- Firebase App Check REST: services.get + playIntegrityConfig.get (GET)');
    console.log('SONUÇ: DEPLOY / DELETE / UPDATE / WRITE YOK.');
    return;
  }
  localSourceChecks();
  let token='';
  try { token = gcloud(['auth','print-access-token']); }
  catch(e) {
    add('BİLİNMİYOR','Kimlik doğrulama',statusError(e));
    add('BİLİNMİYOR','Canlı kaynaklar','Google Cloud oturumu yok. Offline kaynak kontrolleri haricinde hiçbir canlı sonuç elde edilmedi.');
    report(opt);
    return;
  }
  let projectNumber = '';
  try {
    const data = gjson(['projects','describe',opt.project]);
    projectNumber = String(data.projectNumber || '');
    add('OK','Proje kimliği','GCP projesi salt okunur erişimle doğrulandı.');
  } catch(e) { add('BİLİNMİYOR','Proje kimliği',statusError(e)); }
  try { await rulesAudit(opt.project,opt.bucket,token); }
  finally { /* Token hiçbir dosyaya veya rapora aktarılmaz. */ }
  await appCheckAudit(projectNumber,token,opt.appId);
  functionAudit(opt.project,opt.region,opt.bucket);
  token='';
  add('BİLİNMİYOR','Canlı callable kod eşitliği','gcloud metadata okuması canlı kaynak kodun, Auth ve enforceAppCheck davranışının tamamını kanıtlamaz.');
  report(opt);
}
main().catch(e=>{ console.error(`Denetim durdu: ${statusError(e)}`); process.exitCode=1; });
