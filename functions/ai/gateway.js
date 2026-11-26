"use strict";

/**
 * Yapay zekâ giriş kapısı: Firebase'den bağımsız iş mantığı (testlerde sahte
 * bağımlılıklarla çalıştırılır). onCall bağlantısı index.js / ai/index.js'tedir.
 *
 * Akış: kimlik → izin → görev → girdi doğrulama → ayar (model) → günlük limit →
 * Claude → kullanım kaydı (içerik yok) → yanıt.
 */
const {GOREVLER, GirdiHatasi} = require("./gorevler");
const {modelSec, maliyetMikro} = require("./ayarlar");
const {cagir, ClaudeHatasi} = require("./claude");

class KapiHatasi extends Error {
  /** @param {string} kod Firebase HttpsError kodu */
  constructor(kod, mesaj) {
    super(mesaj);
    this.kod = kod;
  }
}

const UID = /^[A-Za-z0-9_-]{6,128}$/;

/**
 * @param {object} bag
 * @param {() => Promise<object>} bag.ayar birleştirilmiş sunucu ayarı
 * @param {(uid: string, gun: string) => Promise<number>} bag.bugunkuIstek
 * @param {(uid: string, gun: string, kayit: object) => Promise<void>} bag.kullanimYaz
 * @param {() => object} bag.claude Anthropic istemcisi
 * @param {() => Date} [bag.simdi]
 */
function kapiOlustur(bag) {
  const simdi = bag.simdi || (() => new Date());

  return async function calistir({uid, data}) {
    if (typeof uid !== "string" || !UID.test(uid)) {
      throw new KapiHatasi("unauthenticated", "Yapay zekâ için bulut hesabıyla giriş yapılmalı.");
    }
    const d = data && typeof data === "object" ? data : {};
    // İzin cihazda verilir; sunucu yine de açık beyan ister.
    if (d.izin !== true) {
      throw new KapiHatasi("failed-precondition", "Yapay zekâ özellikleri Ayarlar'dan açılmamış.");
    }
    const gorev = GOREVLER[d.gorev];
    if (!gorev || !Object.prototype.hasOwnProperty.call(GOREVLER, d.gorev)) {
      throw new KapiHatasi("invalid-argument", "Bilinmeyen yapay zekâ görevi.");
    }

    let veri;
    try {
      veri = gorev.dogrula(d.girdi && typeof d.girdi === "object" ? d.girdi : {});
    } catch (e) {
      if (e instanceof GirdiHatasi) throw new KapiHatasi("invalid-argument", e.message);
      throw e;
    }

    const ayar = await bag.ayar();
    if (!ayar.acik) {
      throw new KapiHatasi("unavailable", "Yapay zekâ özellikleri şu anda kapalı.");
    }
    const gun = simdi().toISOString().slice(0, 10);
    if (await bag.bugunkuIstek(uid, gun) >= ayar.gunlukIstekLimiti) {
      throw new KapiHatasi("resource-exhausted", "Bugünkü yapay zekâ kullanım sınırına ulaşıldı. Yarın tekrar deneyebilirsin.");
    }

    const {model, effort} = modelSec(ayar, d.gorev, uid);
    const istek = {model, effort, ...gorev.istek(veri)};

    let sonuc;
    try {
      sonuc = await cagir(bag.claude(), istek);
    } catch (e) {
      if (e instanceof ClaudeHatasi) {
        const kod = e.tur === "gecici" ? "unavailable" : e.tur === "reddedildi" ? "failed-precondition" : "internal";
        throw new KapiHatasi(kod, e.tur === "gecici" ?
          "Yapay zekâ servisi şu anda yoğun. Biraz sonra tekrar dene." :
          "Bu istek yapay zekâ tarafından yanıtlanamadı.");
      }
      throw e;
    }

    const kullanim = sonuc.kullanim.reduce((t, k) => ({
      girdi: t.girdi + k.girdi,
      cikti: t.cikti + k.cikti,
      mikroUsd: t.mikroUsd + maliyetMikro(k.model, k.girdi, k.cikti),
    }), {girdi: 0, cikti: 0, mikroUsd: 0});
    // Kayıt başarısız olsa da kullanıcı cevabını alır.
    await bag.kullanimYaz(uid, gun, {gorev: d.gorev, model: sonuc.model, ...kullanim}).catch(() => {});

    let cikti;
    if (gorev.tur === "json") {
      try {
        cikti = JSON.parse(sonuc.metin);
      } catch (_) {
        throw new KapiHatasi("internal", "Yapay zekâ yanıtı okunamadı.");
      }
    } else {
      cikti = sonuc.metin.trim();
      if (!cikti) throw new KapiHatasi("internal", "Yapay zekâ boş yanıt döndü.");
    }

    return {
      gorev: d.gorev,
      tur: gorev.tur,
      sonuc: cikti,
      kaynaklar: gorev.kaynaklar(veri),
      // Uygulama önbelleği bu değer değişince eski sonuçları ayırt eder.
      surum: `${model}:${gorev.surum}`,
      model: sonuc.model,
      yedegeDustu: sonuc.yedegeDustu,
      kesildi: sonuc.kesildi,
    };
  };
}

module.exports = {kapiOlustur, KapiHatasi};
