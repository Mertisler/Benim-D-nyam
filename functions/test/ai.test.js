"use strict";

/** Yapay zekâ kapısı: Claude ve Firestore sahte; gerçek ağ çağrısı yapılmaz. */
const test = require("node:test");
const assert = require("node:assert/strict");
const Anthropic = require("@anthropic-ai/sdk");
const {kapiOlustur, KapiHatasi, ozetle} = require("../ai/gateway");
const {birlestir, modelSec, maliyetMikro, ayarOkuyucu} = require("../ai/ayarlar");
const {GOREVLER} = require("../ai/gorevler");

const UID = "kullanici_123";
const ANI = {id: "j-1", tarih: "2025-07-28", baslik: "Koy", metin: "Ayşe elimi tuttu.", kisiler: ["Ayşe"], ruhHali: "EMOTIONAL"};

function sahteClaude(yanitlar) {
  const cagrilar = [];
  const uret = (params) => {
    cagrilar.push(params);
    const y = yanitlar.shift();
    if (y instanceof Error) throw y;
    return {
      model: params.model,
      stop_reason: y.stop_reason || "end_turn",
      content: [{type: "thinking", thinking: ""}, {type: "text", text: y.text}],
      usage: {input_tokens: y.girdi ?? 1000, output_tokens: y.cikti ?? 200},
    };
  };
  return {
    cagrilar,
    messages: {create: async (p) => uret(p)},
    beta: {messages: {create: async (p) => uret(p)}},
  };
}

function kapi({ayar = {}, claude, istekSayisi = 0, kayitlar = []} = {}) {
  return kapiOlustur({
    ayar: async () => birlestir(ayar),
    bugunkuIstek: async () => istekSayisi,
    kullanimYaz: async (uid, gun, k) => { kayitlar.push({uid, gun, ...k}); },
    claude: () => claude,
    simdi: () => new Date("2026-11-26T10:00:00Z"),
  });
}

async function hataKodu(p) {
  try {
    await p;
  } catch (e) {
    assert.ok(e instanceof KapiHatasi, `KapiHatasi bekleniyordu: ${e}`);
    return e.kod;
  }
  assert.fail("hata bekleniyordu");
}

test("giriş ve izin olmadan Claude çağrılmaz", async () => {
  const claude = sahteClaude([]);
  const k = kapi({claude});
  assert.equal(await hataKodu(k({uid: undefined, data: {izin: true, gorev: "ani_analizi", girdi: {ani: ANI}}})), "unauthenticated");
  assert.equal(await hataKodu(k({uid: UID, data: {gorev: "ani_analizi", girdi: {ani: ANI}}})), "failed-precondition");
  assert.equal(await hataKodu(k({uid: UID, data: {izin: true, gorev: "__proto__", girdi: {}}})), "invalid-argument");
  assert.equal(claude.cagrilar.length, 0);
});

test("girdi sınırları uygulanır", async () => {
  const k = kapi({claude: sahteClaude([])});
  const uzun = {...ANI, metin: "x".repeat(6001)};
  assert.equal(await hataKodu(k({uid: UID, data: {izin: true, gorev: "ani_analizi", girdi: {ani: uzun}}})), "invalid-argument");
  const cok = Array.from({length: 13}, (_, i) => ({...ANI, id: `j-${i}`}));
  assert.equal(await hataKodu(k({uid: UID, data: {izin: true, gorev: "soru_cevabi",
    girdi: {soru: "Ne?", bugun: "2026-11-26", anilar: cok}}})), "invalid-argument");
  const ayni = [ANI, ANI];
  assert.equal(await hataKodu(k({uid: UID, data: {izin: true, gorev: "soru_cevabi",
    girdi: {soru: "Ne?", bugun: "2026-11-26", anilar: ayni}}})), "invalid-argument");
  assert.equal(await hataKodu(k({uid: UID, data: {izin: true, gorev: "soru_cevabi",
    girdi: {soru: "Ne?", bugun: "26.11.2026", anilar: [ANI]}}})), "invalid-argument");
});

test("günlük limit ve kapalı ayar", async () => {
  assert.equal(await hataKodu(kapi({claude: sahteClaude([]), istekSayisi: 300})(
    {uid: UID, data: {izin: true, gorev: "ani_analizi", girdi: {ani: ANI}}})), "resource-exhausted");
  assert.equal(await hataKodu(kapi({claude: sahteClaude([]), ayar: {acik: false}})(
    {uid: UID, data: {izin: true, gorev: "ani_analizi", girdi: {ani: ANI}}})), "unavailable");
});

test("anı analizi: varsayılan Haiku, JSON döner, kullanım kaydedilir, kimlik modele gitmez", async () => {
  const analiz = {konular: ["arkadaşlık"], yerler: ["koy"], olaylar: [], ruhHaliOnerisi: "EMOTIONAL", ozet: "Ayşe ile."};
  const claude = sahteClaude([{text: JSON.stringify(analiz), girdi: 1000, cikti: 200}]);
  const kayitlar = [];
  const r = await kapi({claude, kayitlar})({uid: UID, data: {izin: true, gorev: "ani_analizi", girdi: {ani: ANI}}});

  assert.deepEqual(r.sonuc, analiz);
  assert.equal(r.surum, "claude-haiku-5-5:1");
  const p = claude.cagrilar[0];
  assert.equal(p.model, "claude-haiku-5-5");
  assert.equal(p.output_config.format.type, "json_schema");
  assert.equal(p.betas, undefined);
  const gonderilen = p.messages[0].content;
  assert.ok(gonderilen.includes("Ayşe elimi tuttu."));
  assert.ok(!gonderilen.includes("j-1"), "anı kimliği modele gitmemeli");
  assert.ok(!gonderilen.includes("Duygusal"), "analizde seçili ruh hali gönderilmemeli");
  assert.deepEqual(kayitlar, [{uid: UID, gun: "2026-11-26", gorev: "ani_analizi", model: "claude-haiku-5-5",
    girdi: 1000, cikti: 200, mikroUsd: maliyetMikro("claude-haiku-5-5", 1000, 200)}]);
});

test("metin cevaplarında kaynak takma adları anı kimliklerine eşlenir ve tarih verilir", async () => {
  const claude = sahteClaude([{text: "Kaş'a gitmişsin [a2]."}]);
  const r = await kapi({claude})({uid: UID, data: {izin: true, gorev: "soru_cevabi", girdi: {
    soru: "Geçen yaz nereye gittik?", bugun: "2026-11-26",
    anilar: [ANI, {...ANI, id: "j-2", tarih: "2026-07-11"}],
  }}});
  assert.equal(r.tur, "metin");
  assert.equal(r.sonuc, "Kaş'a gitmişsin [a2].");
  assert.deepEqual(r.kaynaklar, {a1: "j-1", a2: "j-2"});
  assert.ok(claude.cagrilar[0].system.includes("2026-11-26"));
});

test("sunucu ayarı ve deneme kullanıcısı modeli değiştirir; izinsiz model yok sayılır", () => {
  const ayar = birlestir({
    gorevler: {soru_cevabi: {model: "claude-opus-5-5", effort: "high"}, ani_analizi: {model: "gpt-x"}},
    denemeKullanicilari: {[UID]: {kisi_hikayesi: {model: "claude-sonnet-5-5"}}},
  });
  assert.deepEqual(modelSec(ayar, "soru_cevabi", "baska_kullanici"), {model: "claude-opus-5-5", effort: "high"});
  assert.deepEqual(modelSec(ayar, "ani_analizi", UID), {model: "claude-haiku-5-5", effort: "medium"});
  assert.equal(modelSec(ayar, "kisi_hikayesi", UID).model, "claude-sonnet-5-5");
  assert.equal(modelSec(ayar, "kisi_hikayesi", "baska_kullanici").model, "claude-haiku-5-5");
});

test("Opus geçici hata verirse Haiku'ya düşer; iki kullanım da sayılır", async () => {
  const yogun = new Anthropic.InternalServerError(529, {type: "error"}, "overloaded", new Headers());
  const claude = sahteClaude([yogun, {text: "Tamam [a1].", girdi: 500, cikti: 100}]);
  const kayitlar = [];
  const r = await kapi({claude, kayitlar, ayar: {gorevler: {donem_ozeti: {model: "claude-opus-5-5"}}}})(
    {uid: UID, data: {izin: true, gorev: "donem_ozeti", girdi: {donemAdi: "Ekim 2025", bugun: "2026-11-26", anilar: [ANI]}}});
  assert.equal(r.yedegeDustu, true);
  assert.equal(claude.cagrilar[0].model, "claude-opus-5-5");
  assert.deepEqual(claude.cagrilar[0].betas, ["server-side-fallback-2026-07-01"]);
  assert.equal(claude.cagrilar[0].fallbacks, "default");
  assert.equal(claude.cagrilar[1].model, "claude-haiku-5-5");
  assert.equal(kayitlar[0].girdi, 500);
});

test("Haiku geçici hata verirse kullanıcıya 'yoğun' döner; ret 'yanıtlanamadı' olur", async () => {
  const yogun = new Anthropic.RateLimitError(429, {type: "error"}, "rate", new Headers());
  assert.equal(await hataKodu(kapi({claude: sahteClaude([yogun])})(
    {uid: UID, data: {izin: true, gorev: "ani_analizi", girdi: {ani: ANI}}})), "unavailable");
  assert.equal(await hataKodu(kapi({claude: sahteClaude([{text: "", stop_reason: "refusal"}])})(
    {uid: UID, data: {izin: true, gorev: "ani_analizi", girdi: {ani: ANI}}})), "failed-precondition");
});

test("ayar okuyucu önbelleğe alır, okunamazsa son iyi ayarı korur", async () => {
  let zaman = 0;
  let okuma = 0;
  let bozuk = false;
  const oku = ayarOkuyucu(async () => {
    okuma++;
    if (bozuk) throw new Error("ağ");
    return {gunlukIstekLimiti: 10};
  }, 1000, () => zaman);
  assert.equal((await oku()).gunlukIstekLimiti, 10);
  await oku();
  assert.equal(okuma, 1);
  zaman = 5000;
  bozuk = true;
  assert.equal((await oku()).gunlukIstekLimiti, 10);
});

test("her görevin talimatı veri/talimat ayrımını belirtir", () => {
  for (const [ad, g] of Object.entries(GOREVLER)) {
    const ornek = {
      ani_analizi: {ani: ANI},
      soru_filtresi: {soru: "Ne?", bugun: "2026-11-26", kisiler: [{id: "p1", ad: "Ayşe"}]},
      soru_cevabi: {soru: "Ne?", bugun: "2026-11-26", anilar: [ANI]},
      donem_ozeti: {donemAdi: "2025", bugun: "2026-11-26", anilar: [ANI]},
      kisi_hikayesi: {kisiAdi: "Ayşe", bugun: "2026-11-26", anilar: [ANI]},
    }[ad];
    const istek = g.istek(g.dogrula(ornek));
    assert.ok(/veridir/.test(istek.system), `${ad} talimatı`);
    assert.ok(istek.maxTokens > 0);
  }
});

test("kullanım özeti: Claude çağrılmaz, izin istemez, bugün ve bu ay toplanır", async () => {
  const claude = sahteClaude([]);
  const k = kapiOlustur({
    ayar: async () => birlestir({gunlukIstekLimiti: 50}),
    bugunkuIstek: async () => 999,
    kullanimYaz: async () => {},
    kullanimOzeti: async (uid, gun) => ozetle({
      "2026-11-25": {istek: 3, mikroUsd: 1000, gorevler: {ani_analizi: {istek: 3, mikroUsd: 1000}}},
      "2026-11-26": {istek: 2, mikroUsd: 500, gorevler: {ani_analizi: {istek: 1, mikroUsd: 100}, soru_cevabi: {istek: 1, mikroUsd: 400}}},
      "2026-10-31": {istek: 9, mikroUsd: 9000},
    }, gun),
    claude: () => claude,
    simdi: () => new Date("2026-11-26T10:00:00Z"),
  });
  const r = await k({uid: UID, data: {gorev: "kullanim"}});
  assert.equal(r.gunlukIstekLimiti, 50);
  assert.deepEqual(r.bugun, {istek: 2, mikroUsd: 500});
  assert.equal(r.ay.istek, 5);
  assert.equal(r.ay.mikroUsd, 1500);
  assert.deepEqual(r.ay.gorevler.ani_analizi, {istek: 4, mikroUsd: 1100});
  assert.equal(claude.cagrilar.length, 0);
  // Girişsiz olmaz.
  assert.equal(await hataKodu(k({uid: undefined, data: {gorev: "kullanim"}})), "unauthenticated");
});
