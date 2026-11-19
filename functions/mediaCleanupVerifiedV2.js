"use strict";

/**
 * Benim Dünyam — Faz 2G.4C
 * Firestore'daki şifreli silme kayıtları ve medya manifestleri arasında sunucuda
 * KİMLİK eşleştirmesi yapar. E2EE parolası sunucuya GELMEZ; AES-GCM içeriğinin
 * gerçekten çözüldüğünü cihaz kontrol eder. Sunucu değişmez metadata, tarih,
 * manifest tekilliği, Storage generation ve kullanıcı onayını bağımsız doğrular.
 *
 * Silme varsayılan olarak KAPALI. Gerçek Firebase kuralları denetlenmeden
 * BD_CLEANUP_V2_ENABLED=true olarak dağıtılmamalıdır.
 */
const crypto = require("node:crypto");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {initializeApp, getApps} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {Storage} = require("@google-cloud/storage");

if (!getApps().length) initializeApp();
const db = getFirestore();
const storage = new Storage();

const REGION = process.env.BD_CLEANUP_REGION || "europe-west1";
const ON = {region: REGION, enforceAppCheck: true, timeoutSeconds: 60,
  memory: "256MiB", maxInstances: 3};
const PHOTO_ID = /^[0-9]{13}_[0-9a-fA-F-]{36}\.(jpg|png|webp)$/;
const JOURNAL_ID = /^[A-Za-z0-9_-]{1,128}$/;
const HASH = /^[0-9a-f]{64}$/;
const GEN = /^[1-9][0-9]{0,24}$/;
const KEEP_MS = 7 * 24 * 60 * 60 * 1000;
const PERMIT_MS = 2 * 60 * 1000;
const RECORDS = "encrypted_media_deletions";
const MANIFESTS = "encrypted_media_entries";
const PERMITS = "_server_media_cleanup_permits_v2";
const AUDIT = "_server_media_cleanup_audit";

function fail(code, message) { throw new HttpsError(code, message); }
function sha(s) { return crypto.createHash("sha256").update(s).digest("hex"); }
function collection(uid, name) { return db.collection("users").doc(uid).collection(name); }
function scope(request) {
  const uid = request.auth?.uid;
  if (typeof uid !== "string" || !/^[A-Za-z0-9_-]{6,128}$/.test(uid)) {
    fail("unauthenticated", "Giriş yapılmış hesap gerekli.");
  }
  // App Check kuralı da fonksiyonun üzerinde zorunlu.
  const bucket = process.env.BD_MEDIA_BUCKET || "";
  if (process.env.BD_CLEANUP_V2_ENABLED !== "true" ||
      !/^[a-z0-9][a-z0-9._-]{2,220}$/.test(bucket)) {
    fail("failed-precondition", "Doğrulanmış bulut temizleme servisi kilitli.");
  }
  return {uid, bucket, prefix: `users/${uid}/encrypted_media_v1/`};
}
function input(request) {
  const {uid, bucket, prefix} = scope(request);
  const d = request.data || {};
  if (d.bucket !== bucket || typeof d.fullPath !== "string" ||
      !d.fullPath.startsWith(prefix) ||
      !new RegExp(`^users/${uid}/encrypted_media_v1/[0-9a-f]{64}\\.bin$`).test(d.fullPath) ||
      typeof d.generation !== "string" || !GEN.test(d.generation) ||
      typeof d.bytes !== "number" || !Number.isSafeInteger(d.bytes) || d.bytes < 1 ||
      !(d.md5Hash === undefined || d.md5Hash === null ||
        (typeof d.md5Hash === "string" && d.md5Hash.length < 128)) ||
      typeof d.journalId !== "string" || !JOURNAL_ID.test(d.journalId) ||
      typeof d.photoId !== "string" || !PHOTO_ID.test(d.photoId)) {
    fail("invalid-argument", "Dosya ve silme kimliği güvenlik kapsamının dışında.");
  }
  const obj = {
    uid, bucket, fullPath: d.fullPath, generation: d.generation,
    bytes: d.bytes, md5Hash: d.md5Hash || null,
    journalId: d.journalId, photoId: d.photoId,
  };
  obj.manifestId = obj.fullPath.substring(prefix.length).replace(/\.bin$/, "");
  obj.markerId = sha(`${uid}|MEDIA_DELETE|JOURNAL_PHOTO|${obj.journalId}|${obj.photoId}`);
  obj.ownerHash = sha(`${uid}|JOURNAL_PHOTO|${obj.journalId}`);
  obj.fileNameHash = sha(`${uid}|${obj.journalId}|${obj.photoId}`);
  return obj;
}
function permitKey(o) {
  return sha([o.uid, o.bucket, o.fullPath, o.generation].join("\n"));
}
function equalRecord(row, o) {
  return row?.ownerUid === o.uid && row?.formatVersion === 3 &&
      row?.ownerHash === o.ownerHash && row?.fileNameHash === o.fileNameHash &&
      typeof row?.encryptedPayload === "string" && row.encryptedPayload.length > 50;
}
function equalManifest(row, o) {
  return row?.ownerUid === o.uid && row?.formatVersion === 1 &&
      row?.kind === "JOURNAL_PHOTO" && row?.storagePath === o.fullPath &&
      row?.ownerHash === o.ownerHash && row?.fileNameHash === o.fileNameHash;
}
function tokenIsEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const x = Buffer.from(a, "utf8");
  const y = Buffer.from(b, "utf8");
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

/** Sunucuda tüm üyelik ve tekillik koşullarını eksiksiz doğrula. */
async function verify(o) {
  const markerRef = collection(o.uid, RECORDS).doc(o.markerId);
  const manifestRef = collection(o.uid, MANIFESTS).doc(o.manifestId);
  const [marker, manifest, twins] = await Promise.all([
    markerRef.get(), manifestRef.get(),
    collection(o.uid, MANIFESTS).where("fileNameHash", "==", o.fileNameHash).limit(2).get(),
  ]);
  if (!marker.exists || !manifest.exists || twins.size !== 1 ||
      twins.docs[0].id !== o.manifestId ||
      !equalRecord(marker.data(), o) || !equalManifest(manifest.data(), o)) {
    fail("failed-precondition", "Silme kaydı, fotoğraf ve manifest tekil olarak eşleşmedi.");
  }
  if (!marker.createTime || !marker.updateTime ||
      marker.createTime.toMillis() !== marker.updateTime.toMillis() ||
      Date.now() - marker.createTime.toMillis() < KEEP_MS ||
      marker.createTime.toMillis() > Date.now()) {
    fail("failed-precondition", "İşaret değişmiş veya yedi günlük bekleme tamamlanmamış.");
  }
  const f = storage.bucket(o.bucket).file(o.fullPath);
  let meta;
  try { [meta] = await f.getMetadata(); } catch (e) {
    if (Number(e.code) === 404) fail("not-found", "Storage nesnesi yok.");
    throw e;
  }
  if (String(meta.generation) !== o.generation || Number(meta.size) !== o.bytes ||
      (meta.md5Hash || null) !== o.md5Hash ||
      !meta.timeCreated || new Date(meta.timeCreated).getTime() > marker.createTime.toMillis()) {
    fail("failed-precondition", "Depolama nesnesi yeni sürümde veya işaretten sonra oluşturulmuş.");
  }
  return {markerRef, manifestRef, marker, manifest, file: f,
    metageneration: String(meta.metageneration)};
}

exports.bdMediaCleanupStatusV2 = onCall(ON, async (request) => {
  if (!request.auth?.uid) fail("unauthenticated", "Oturum gerekli.");
  return {ready: process.env.BD_CLEANUP_V2_ENABLED === "true" &&
    /^[a-z0-9][a-z0-9._-]{2,220}$/.test(process.env.BD_MEDIA_BUCKET || ""),
  retentionDays: 7, maxCandidates: 3};
});

exports.bdMediaCleanupPrepareV2 = onCall(ON, async (request) => {
  const o = input(request);
  const checked = await verify(o);
  const key = permitKey(o);
  const ref = collection(o.uid, PERMITS).doc(key);
  const now = Date.now();
  let permit;
  await db.runTransaction(async (tx) => {
    const [marker, manifest, existing] = await Promise.all([
      tx.get(checked.markerRef), tx.get(checked.manifestRef), tx.get(ref),
    ]);
    if (!marker.exists || !manifest.exists ||
        marker.updateTime.toMillis() !== checked.marker.updateTime.toMillis() ||
        manifest.updateTime.toMillis() !== checked.manifest.updateTime.toMillis() ||
        !equalRecord(marker.data(), o) || !equalManifest(manifest.data(), o)) {
      fail("failed-precondition", "Fotoğrafın bulut kaydı tarama sırasında değişti.");
    }
    const old = existing.data();
    if (existing.exists && old.status !== "prepared") {
      fail("failed-precondition", "Önceki silme inceleme gerektiriyor veya tamamlandı.");
    }
    if (existing.exists && old.expiresAtMs > now && old.markerId === o.markerId &&
        old.manifestUpdateMs === manifest.updateTime.toMillis() &&
        old.markerUpdateMs === marker.updateTime.toMillis()) {
      permit = old;
      return;
    }
    permit = {
      ...o, status: "prepared", token: crypto.randomBytes(32).toString("base64url"),
      createdAtMs: now, expiresAtMs: now + PERMIT_MS,
      markerUpdateMs: marker.updateTime.toMillis(),
      manifestUpdateMs: manifest.updateTime.toMillis(),
    };
    tx.set(ref, permit);
  });
  return {
    uid: o.uid, bucket: o.bucket, fullPath: o.fullPath,
    generation: o.generation, immutableDeletionRecordId: o.markerId,
    journalId: o.journalId, photoId: o.photoId,
    expiresAtMs: permit.expiresAtMs, opaqueProof: permit.token,
  };
});

exports.bdMediaCleanupExecuteV2 = onCall(ON, async (request) => {
  if (request.data?.confirmed !== true || request.data?.confirmation !== "TEMİZLE") {
    fail("failed-precondition", "Ayrı açık kullanıcı onayı gerekli.");
  }
  const o = input(request);
  const token = request.data?.opaqueProof;
  if (typeof token !== "string" || token.length < 32 || token.length > 128 ||
      request.data?.immutableDeletionRecordId !== o.markerId) {
    fail("permission-denied", "İzin ve silme kayıt kimliği geçersiz.");
  }
  const ref = collection(o.uid, PERMITS).doc(permitKey(o));
  const old = await ref.get();
  // Ağ kopması sonrası aynı isteğin tekrarında yeniden silme yapılmaz.
  if (old.exists && old.data().status === "deleted" && tokenIsEqual(old.data().token, token)) {
    return {deleted: true, generation: o.generation, alreadyCompleted: true};
  }
  const checked = await verify(o);
  const now = Date.now();
  await db.runTransaction(async (tx) => {
    const [marker, manifest, permit] = await Promise.all([
      tx.get(checked.markerRef), tx.get(checked.manifestRef), tx.get(ref),
    ]);
    const p = permit.data();
    if (!permit.exists || p.status !== "prepared" || !tokenIsEqual(p.token, token) ||
        p.uid !== o.uid || p.fullPath !== o.fullPath || p.generation !== o.generation ||
        p.bucket !== o.bucket || p.bytes !== o.bytes || p.md5Hash !== o.md5Hash ||
        p.journalId !== o.journalId || p.photoId !== o.photoId ||
        p.markerId !== o.markerId || p.expiresAtMs <= now ||
        !marker.exists || !manifest.exists ||
        marker.updateTime.toMillis() !== p.markerUpdateMs ||
        manifest.updateTime.toMillis() !== p.manifestUpdateMs ||
        !equalRecord(marker.data(), o) || !equalManifest(manifest.data(), o)) {
      fail("failed-precondition", "İzin süresi, bulut referansı veya silme kanıtı değişti.");
    }
    // Tek seferlik kullanım: eşzamanlı ikinci istek daha dosyaya gitmeden reddedilir.
    tx.update(ref, {status: "claimed", claimedAtMs: now});
  });

  // GCS nesnesini silerken MUTLAKA sürüm önkoşulunu kullanırız.
  // Storage başarılı olsa bile Firestore güncellemesi başarısızsa sessiz tekrar YOK.
  let physicallyDeleted = false;
  try {
    await checked.file.delete({
      ifGenerationMatch: o.generation,
      ifMetagenerationMatch: checked.metageneration,
    });
    physicallyDeleted = true;
    const batch = db.batch();
    // Bu manifest kalırsa mevcut restoreNext() artık bulunmayan dosyayı indirebilir.
    // İlgili manifest yalnızca sunucu üzerinden kaldırılır; tombstone ASLA kaldırılmaz.
    batch.delete(checked.manifestRef);
    batch.update(ref, {status: "deleted", completedAtMs: Date.now()});
    batch.create(collection(o.uid, AUDIT).doc(), {
      uid: o.uid, bucket: o.bucket, fullPath: o.fullPath,
      generation: o.generation, markerId: o.markerId, manifestId: o.manifestId,
      bytes: o.bytes, status: "deleted", atMs: Date.now(),
      processVersion: 2,
    });
    await batch.commit();
    return {deleted: true, generation: o.generation, alreadyCompleted: false};
  } catch (error) {
    try {
      await ref.update({status: "needs_manual_review", failedAtMs: Date.now(),
        storageDeleteMayHaveSucceeded: physicallyDeleted,
        errorCode: String(error.code || "unknown").slice(0, 50)});
      await collection(o.uid, AUDIT).add({
        uid: o.uid, fullPath: o.fullPath, generation: o.generation,
        markerId: o.markerId, status: "needs_manual_review", atMs: Date.now(),
        storageDeleteMayHaveSucceeded: physicallyDeleted,
      });
    } catch (_ignored) { /* Claimed kaydı yönetici incelemesi için kalır. */ }
    fail("failed-precondition", "Silme durdu. Sunucu kaydı manuel inceleme gerektiriyor.");
  }
});
