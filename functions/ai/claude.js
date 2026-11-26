"use strict";

/**
 * Claude çağrısı. Seçilen model başarısız olursa (aşırı yük, sunucu hatası, zaman aşımı
 * ya da güvenlik reddi) aynı istek bir kez varsayılan modelle (Haiku) denenir.
 * Anı metni hiçbir yere loglanmaz.
 */
const Anthropic = require("@anthropic-ai/sdk");
const {VARSAYILAN_MODEL} = require("./ayarlar");

class ClaudeHatasi extends Error {
  /** @param {"reddedildi"|"gecici"|"kalici"} tur */
  constructor(tur, mesaj) {
    super(mesaj);
    this.tur = tur;
  }
}

function geciciMi(e) {
  return e instanceof Anthropic.RateLimitError ||
    e instanceof Anthropic.InternalServerError ||
    e instanceof Anthropic.APIConnectionError ||
    (e instanceof Anthropic.APIError && (e.status === 529 || e.status >= 500));
}

/** Tek model ile tek istek. */
async function tekCagri(client, {model, effort, system, kullanici, sema, maxTokens}) {
  const params = {
    model,
    max_tokens: maxTokens,
    system,
    messages: [{role: "user", content: kullanici}],
    output_config: {effort, ...(sema ? {format: {type: "json_schema", schema: sema}} : {})},
  };
  let yanit;
  try {
    if (model === VARSAYILAN_MODEL) {
      yanit = await client.messages.create(params);
    } else {
      // Güvenlik reddinde API isteği kendi içinde uygun bir modelle tekrar dener.
      yanit = await client.beta.messages.create({
        ...params, betas: ["server-side-fallback-2026-07-01"], fallbacks: "default",
      });
    }
  } catch (e) {
    if (geciciMi(e)) throw new ClaudeHatasi("gecici", `Claude geçici olarak yanıt vermedi (${e.status || "bağlantı"}).`);
    throw new ClaudeHatasi("kalici", `Claude isteği reddetti (${e.status || "bilinmiyor"}).`);
  }
  if (yanit.stop_reason === "refusal") throw new ClaudeHatasi("reddedildi", "Model bu isteği yanıtlamadı.");
  const metin = yanit.content.filter((b) => b.type === "text").map((b) => b.text).join("");
  return {
    metin,
    model: yanit.model || model,
    kesildi: yanit.stop_reason === "max_tokens",
    girdi: yanit.usage?.input_tokens || 0,
    cikti: yanit.usage?.output_tokens || 0,
  };
}

/** Seçilen modeli dener; olmazsa bir kez varsayılan modele düşer. Kullanımları toplar. */
async function cagir(client, istek) {
  const denemeler = [istek.model];
  if (istek.model !== VARSAYILAN_MODEL) denemeler.push(VARSAYILAN_MODEL);
  const kullanim = [];
  let sonHata;
  for (const model of denemeler) {
    try {
      const r = await tekCagri(client, {...istek, model});
      kullanim.push({model: r.model, girdi: r.girdi, cikti: r.cikti});
      return {...r, kullanim, yedegeDustu: model !== istek.model};
    } catch (e) {
      sonHata = e;
      if (!(e instanceof ClaudeHatasi) || e.tur === "kalici") break;
    }
  }
  throw sonHata;
}

module.exports = {cagir, tekCagri, ClaudeHatasi};
