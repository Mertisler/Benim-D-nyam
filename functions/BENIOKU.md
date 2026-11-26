# Benim Dünyam — Cloud Functions

| Fonksiyon | Ne yapar |
|---|---|
| `bdAiGatewayV1` | Yapay zekâ kapısı. Uygulama görev adını ve girdiyi gönderir; model seçimi, talimatlar ve sınırlar burada. |
| `bdMediaCleanup*V2` | Doğrulanmış medya temizleme. `BD_CLEANUP_V2_ENABLED=true` olmadıkça kilitli. |

## Yapay zekâ kapısını yüklemek (Blaze planı gerekir)

1. Firebase CLI'yi kur ve giriş yap:

   ```powershell
   npm install -g firebase-tools
   ```

   ```powershell
   firebase login
   ```

2. Anthropic API anahtarını Secret Manager'a kaydet. Komut anahtarı gizli olarak sorar; ekrana yazdırmaz.
   Üretim için deneme anahtarından **ayrı** bir anahtar oluşturman önerilir.

   ```powershell
   firebase functions:secrets:set ANTHROPIC_API_KEY
   ```

3. Yalnızca yapay zekâ kapısını yükle (proje kökünde çalıştır):

   ```powershell
   firebase deploy --only functions:bdAiGatewayV1
   ```

   İlk yüklemede Firebase, Cloud Build ve Artifact Registry API'lerini açmak için onay isteyebilir.

   "User code failed to load … Timeout after 10000" hatası alırsan (yavaş disk/antivirüs), bekleme süresini uzat:

   ```powershell
   $env:FUNCTIONS_DISCOVERY_TIMEOUT=60; firebase deploy --only functions:bdAiGatewayV1
   ```

4. App Check: Fonksiyon App Check jetonu zorunlu tutar. Debug sürümünü denerken Firebase Console →
   App Check → Uygulamalar → **Hata ayıklama jetonlarını yönet** bölümüne, Logcat'te
   `DebugAppCheckProvider` satırında görünen jetonu ekle. Release sürümü Play Integrity kullanır.

5. Uygulamada: Ayarlar → **Yapay zekâ özellikleri** → aç → (debug sürümünde) **Bağlantıyı dene**.

## Model ayarı (isteğe bağlı)

Varsayılan: her görev `claude-haiku-5-5`, effort `medium`, günlük 300 istek/kullanıcı.
Değiştirmek için Firestore'da `_server_ai_config/genel` belgesini oluştur (uygulamalar bu yolu okuyamaz/yazamaz):

```json
{
  "acik": true,
  "gunlukIstekLimiti": 300,
  "gorevler": {
    "donem_ozeti": { "model": "claude-opus-5-5", "effort": "medium" }
  },
  "denemeKullanicilari": {
    "<senin uid'in>": { "kisi_hikayesi": { "model": "claude-opus-5-5" } }
  }
}
```

- Görevler: `ani_analizi`, `soru_filtresi`, `soru_cevabi`, `donem_ozeti`, `kisi_hikayesi`.
- İzin verilen modeller: `claude-haiku-5-5`, `claude-sonnet-5-5`, `claude-opus-5-5`. Başka bir değer yazılırsa Haiku kullanılır.
- Ayar en geç 5 dakikada devreye girer. Model değişince uygulama o görevin eski sonuçlarını kendiliğinden yeniler.
- Seçilen model hata verirse aynı istek bir kez Haiku ile denenir.
- `acik: false` tüm yapay zekâ isteklerini durdurur (acil kapatma).

## Kullanım ve maliyet

Firestore → `_server_ai_usage/{uid}/gunler/{YYYY-AA-GG}`: istek sayısı, token, `mikroUsd` (1.000.000 = 1 dolar),
görev ve model kırılımı. Anı metni ve cevaplar **kaydedilmez**.

## Testler

```powershell
cd functions; npm test
```

Testler Claude ve Firestore'u taklit eder; ağ çağrısı yapmaz.
