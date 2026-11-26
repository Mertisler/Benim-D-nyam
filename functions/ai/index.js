"use strict";

/**
 * bdAiGatewayV1 — uygulamanın yapay zekâ için çağırdığı tek kapı.
 *
 * - Firebase Auth girişi ve App Check zorunlu.
 * - Anthropic API anahtarı Secret Manager'da (ANTHROPIC_API_KEY); uygulamada yoktur.
 * - Model seçimi `_server_ai_config/genel` belgesindedir (bkz. ayarlar.js).
 * - Kullanım sayaçları `_server_ai_usage/{uid}/gunler/{YYYY-AA-GG}` altında tutulur;
 *   anı metni ya da cevap kaydedilmez. İki yol da istemcilere kapalıdır.
 */
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {defineSecret} = require("firebase-functions/params");
const {initializeApp, getApps} = require("firebase-admin/app");
const {getFirestore, FieldValue, FieldPath} = require("firebase-admin/firestore");
const Anthropic = require("@anthropic-ai/sdk");
const {kapiOlustur, KapiHatasi, ozetle} = require("./gateway");
const {ayarOkuyucu} = require("./ayarlar");

if (!getApps().length) initializeApp();
const db = getFirestore();
const ANTHROPIC_API_KEY = defineSecret("ANTHROPIC_API_KEY");
const REGION = "europe-west1";

const gunBelgesi = (uid, gun) => db.collection("_server_ai_usage").doc(uid).collection("gunler").doc(gun);

let istemci = null;
const calistir = kapiOlustur({
  ayar: ayarOkuyucu(async () => (await db.collection("_server_ai_config").doc("genel").get()).data()),
  bugunkuIstek: async (uid, gun) => (await gunBelgesi(uid, gun).get()).get("istek") || 0,
  kullanimYaz: (uid, gun, k) => gunBelgesi(uid, gun).set({
    istek: FieldValue.increment(1),
    girdi: FieldValue.increment(k.girdi),
    cikti: FieldValue.increment(k.cikti),
    mikroUsd: FieldValue.increment(k.mikroUsd),
    gorevler: {[k.gorev]: {istek: FieldValue.increment(1), mikroUsd: FieldValue.increment(k.mikroUsd)}},
    modeller: {[k.model]: FieldValue.increment(1)},
    guncellendi: FieldValue.serverTimestamp(),
  }, {merge: true}),
  kullanimOzeti: async (uid, gun) => {
    const ay = gun.slice(0, 7);
    const snap = await db.collection("_server_ai_usage").doc(uid).collection("gunler")
      .where(FieldPath.documentId(), ">=", `${ay}-01`)
      .where(FieldPath.documentId(), "<=", `${ay}-31`)
      .get();
    return ozetle(Object.fromEntries(snap.docs.map((doc) => [doc.id, doc.data()])), gun);
  },
  claude: () => {
    if (!istemci) istemci = new Anthropic({apiKey: ANTHROPIC_API_KEY.value(), maxRetries: 2, timeout: 90_000});
    return istemci;
  },
});

exports.bdAiGatewayV1 = onCall({
  region: REGION,
  enforceAppCheck: true,
  secrets: [ANTHROPIC_API_KEY],
  timeoutSeconds: 120,
  memory: "256MiB",
  maxInstances: 5,
}, async (request) => {
  try {
    return await calistir({uid: request.auth?.uid, data: request.data});
  } catch (e) {
    if (e instanceof KapiHatasi) throw new HttpsError(e.kod, e.message);
    // Ayrıntı (anı metni içerebilecek hiçbir şey) istemciye ya da loglara yazılmaz.
    console.error("bdAiGatewayV1 beklenmeyen hata", e && e.name);
    throw new HttpsError("internal", "Yapay zekâ isteği tamamlanamadı.");
  }
});
