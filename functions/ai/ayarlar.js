"use strict";

/**
 * İş bazlı model ayarı.
 *
 * Varsayılanlar koddadır. Firestore'daki `_server_ai_config/genel` belgesi (istemciler
 * okuyamaz/yazamaz; Firestore kuralları tanımsız her yolu kapatır) bunları ezebilir:
 *
 *   {
 *     acik: true,
 *     gunlukIstekLimiti: 300,
 *     gorevler: { soru_cevabi: { model: "claude-opus-5-5", effort: "medium" } },
 *     denemeKullanicilari: { "<uid>": { kisi_hikayesi: { model: "claude-opus-5-5" } } }
 *   }
 *
 * Yalnızca izin verilen modeller seçilebilir; yazım hatası varsayılana düşer.
 */

const IZINLI_MODELLER = {
  "claude-haiku-5-5": {girdi: 0.10, cikti: 0.50},
  "claude-sonnet-5-5": {girdi: 2.00, cikti: 10.00},
  "claude-opus-5-5": {girdi: 4.00, cikti: 20.00},
};
const VARSAYILAN_MODEL = "claude-haiku-5-5";
const EFFORTLAR = ["low", "medium", "high"];

const VARSAYILAN = Object.freeze({
  acik: true,
  gunlukIstekLimiti: 300,
  gorevler: {},
  denemeKullanicilari: {},
});

function nesne(x) { return x && typeof x === "object" && !Array.isArray(x) ? x : {}; }

function birlestir(belge) {
  const b = nesne(belge);
  const limit = Number.isSafeInteger(b.gunlukIstekLimiti) && b.gunlukIstekLimiti >= 0 &&
    b.gunlukIstekLimiti <= 100000 ? b.gunlukIstekLimiti : VARSAYILAN.gunlukIstekLimiti;
  return {
    acik: b.acik !== false,
    gunlukIstekLimiti: limit,
    gorevler: nesne(b.gorevler),
    denemeKullanicilari: nesne(b.denemeKullanicilari),
  };
}

/** Görev için model ve effort: önce deneme kullanıcısı ayarı, sonra görev ayarı, sonra varsayılan. */
function modelSec(ayar, gorev, uid) {
  const ozel = nesne(nesne(ayar.denemeKullanicilari[uid])[gorev]);
  const genel = nesne(ayar.gorevler[gorev]);
  const model = [ozel.model, genel.model].find((m) => typeof m === "string" && IZINLI_MODELLER[m]) ||
    VARSAYILAN_MODEL;
  const effort = [ozel.effort, genel.effort].find((e) => EFFORTLAR.includes(e)) || "medium";
  return {model, effort};
}

/** Mikro-dolar (1e-6 USD); Firestore'da tam sayı olarak toplanır. */
function maliyetMikro(model, girdi, cikti) {
  const f = IZINLI_MODELLER[model] || IZINLI_MODELLER[VARSAYILAN_MODEL];
  return Math.round(girdi * f.girdi + cikti * f.cikti);
}

/** Firestore okumasını 5 dakika önbelleğe alır. */
function ayarOkuyucu(oku, sureMs = 5 * 60 * 1000, simdi = Date.now) {
  let onbellek = null;
  let zaman = 0;
  return async () => {
    if (onbellek && simdi() - zaman < sureMs) return onbellek;
    try {
      onbellek = birlestir(await oku());
    } catch (e) {
      // Ayar okunamazsa güvenli varsayılanla devam et, ama önceki iyi ayarı kaybetme.
      onbellek = onbellek || birlestir(null);
    }
    zaman = simdi();
    return onbellek;
  };
}

module.exports = {IZINLI_MODELLER, VARSAYILAN_MODEL, birlestir, modelSec, maliyetMikro, ayarOkuyucu};
