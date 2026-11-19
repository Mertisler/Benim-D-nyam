#!/usr/bin/env node
/**
 * Faz 2H.2 — gcloud ile alınmış salt-okunur IAM JSON kanıtlarını inceler.
 * Hiçbir ağa bağlanmaz, hiçbir yetkiyi güncellemez.
 * IAM kaynak mirası, koşullu IAM ve özel roller nedeniyle nihai güvenlik onayı değildir.
 */
import fs from 'node:fs';
import path from 'node:path';
const dir = path.resolve(process.argv[2] || './iam-evidence-2h2');
const names = ['bdMediaCleanupStatusV2', 'bdMediaCleanupPrepareV2', 'bdMediaCleanupExecuteV2'];
const read = file => JSON.parse(fs.readFileSync(path.join(dir, file), 'utf8'));
const project = read('project-policy.json');
const bucket = read('bucket-policy.json');
const errors = [];
const warnings = [];
function flag(message, critical = true) {
  (critical ? errors : warnings).push(message);
  console.log(`[${critical ? 'İNCELE' : 'NOT'}] ${message}`);
}
function rolesFor(bindings, member) {
  return (bindings || []).filter(b => (b.members || []).includes(member))
    .map(b => ({role:b.role, condition:b.condition || null}));
}
function broad(role) {
  return ['roles/owner', 'roles/editor', 'roles/storage.admin',
    'roles/datastore.owner', 'roles/firebase.admin', 'roles/iam.serviceAccountTokenCreator']
    .includes(role);
}
for (const fn of names) {
  const functionInfo = read(`function-${fn}.json`);
  const run = read(`run-${fn}.json`);
  const sa = functionInfo.serviceConfig?.serviceAccountEmail;
  if (!sa || !sa.includes('@')) {
    flag(`${fn}: çalışma zamanı hizmet hesabı kesin olarak belirlenemiyor.`);
    continue;
  }
  console.log(`\nFonksiyon: ${fn}\nRuntime SA: ${sa}`);
  const member = `serviceAccount:${sa}`;
  const inherited = rolesFor(project.bindings, member);
  const scoped = rolesFor(bucket.bindings, member);
  const invokers = (run.bindings || []).filter(b => b.role === 'roles/run.invoker')
    .flatMap(b => b.members || []);
  console.log(`Projeye özgü roller: ${inherited.map(x=>x.role).join(', ') || '(bulunamadı)'}`);
  console.log(`Bucket rolleri: ${scoped.map(x=>x.role).join(', ') || '(bulunamadı)'}`);
  if ([...inherited, ...scoped].some(x => broad(x.role))) {
    flag(`${fn}: runtime SA geniş yönetici/editör yetkisi içeriyor; en az yetki ile daraltılmalı.`);
  }
  if (scoped.some(x => x.role === 'roles/storage.objectAdmin')) {
    flag(`${fn}: storage.objectAdmin bucket genelinde geniş olabilir; yalnız gereken nesne yetkilerini incele.`, false);
  }
  if (invokers.includes('allUsers')) {
    flag(`${fn}: HTTPS çağrısı allUsers tarafından tetiklenebiliyor. Callable içinde Auth + App Check doğrulaması zorunlu.`, false);
  }
  if (invokers.length === 0) {
    flag(`${fn}: Cloud Run invoker politikası boş; callable Android istemcisinden erişilemiyor olabilir.`, false);
  }
}
console.log('\nÖnemli: Bu rapor, koşullu/kurumdan miras alınan IAM rollerini, Service Account Permissions Test, App Check Console enforcing ayarını veya canlı kod sürümünü kanıtlamaz.');
console.log(`Sonuç: ${errors.length} kritik inceleme / ${warnings.length} not`);
if (errors.length) process.exitCode = 1;
