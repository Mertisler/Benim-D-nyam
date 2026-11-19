package com.loc.benimdunyam.data.sync.media

/**
 * Faz 2G.3'ün AES-GCM şifreli silme defterine açılan GÜVENLİ uyarlama noktası.
 * Bir belgeyi yalnızca listelemek KANIT değildir: kayıt çözülmeli, özgün fotoğraf ID'si
 * ve Storage tam yolu eşleştirilmeli, bütünlük kontrolü yapılmalı ve buluta gönderilmiş
 * değiştirilemeyen silme kaydı SERVER kaynağından doğrulanmalıdır.
 *
 * İkinci yöntem Cloud Function / güvenilir sunucuda generation-match ile atomik silme
 * yapmalıdır. Android StorageReference.delete() burada kullanılmamalıdır; son metadata
 * okuması ile delete() çağrısı arasında nesne başka bir sürümle değiştirilebilir.
 */
interface VerifiedMediaCleanupGateway {
    /** Doğrulama sistemi hazır değilse false. */
    suspend fun isReady(uid: String): Boolean

    /** Kanıt veya tekillik eksikse null döndür. Silme adayı yalnızca izinle oluşur. */
    suspend fun prepare(uid: String, item: CloudMediaObject, journalId: String, photoId: String): VerifiedMediaCleanupPermit?

    /**
     * Her çağrıda kanıtı sunucuda tekrar doğrula ve generation-match precondition uygula.
     * Nesne değişmişse silmek yerine hata fırlat. Başarıyı yalnızca sunucu teyit etsin.
     */
    suspend fun deleteAtomically(uid: String, item: CloudMediaObject, permit: VerifiedMediaCleanupPermit)
}

/**
 * Varsayılan güvenli uygulama: fiziksel silme KAPALI.
 * 2G.3'ün gerçek kaynakları ve sunucu tarafı koşullu silme doğrulanana kadar bu sınıfı koru.
 */
class LockedMediaCleanupGateway : VerifiedMediaCleanupGateway {
    override suspend fun isReady(uid: String): Boolean = false
    override suspend fun prepare(uid: String, item: CloudMediaObject, journalId: String, photoId: String): VerifiedMediaCleanupPermit? = null
    override suspend fun deleteAtomically(
        uid: String,
        item: CloudMediaObject,
        permit: VerifiedMediaCleanupPermit
    ): Unit = error("Fiziksel silme kilitli: şifreli kanıt ve atomik sunucu silmesi bağlı değil.")
}
