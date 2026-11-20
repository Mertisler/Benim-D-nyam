package com.loc.benimdunyam.domain.sync

/** Cihazda zaten bulunan bir anı için buluttaki UPSERT olayının sınıfı. */
enum class RemoteEditDecision {
    /** Yalnızca diğer cihaz düzenlemiş: otomatik uygulanabilir. */
    APPLY,
    /** Cihaz zaten bu sürümde. */
    ALREADY_CURRENT,
    /** Cihazdaki sürüm daha yeni; gönderilmeyi bekler veya zaten gönderilmiştir. */
    LOCAL_AHEAD,
    /** İki taraf da değiştirmiş ya da durum belirsiz: asla otomatik ezilmez. */
    CONFLICT
}

/**
 * Faz C — düzenleme senkronizasyonunun saf karar kuralı.
 *
 * Temel ilke: "Bu cihazda gönderilmemiş değişiklik yoksa ve buluttaki sürüm daha
 * yeniyse, düzenlemeyi yalnızca diğer cihaz yapmıştır." Bu durumda uzak metin
 * güvenle uygulanır. Yerelde bekleyen (PENDING) veya daha önce çakışma olarak
 * işaretlenmiş (CONFLICT) her kayıt kullanıcı kararına bırakılır.
 *
 * Android / Firebase bağımlılığı yoktur.
 */
object RemoteEditPolicy {

    /**
     * @param localJournalExists cihazda bu kimlikte anı var mı
     * @param localState journal_sync_events.state (yoksa null)
     * @param localChangeType journal_sync_events.changeType (yoksa null)
     * @param localVersion journal_sync_events.changeVersion (yoksa null)
     * @param remoteRevision buluttaki en yüksek UPSERT sürümü
     */
    fun classify(
        localJournalExists: Boolean,
        localState: String?,
        localChangeType: String?,
        localVersion: Long?,
        remoteRevision: Long
    ): RemoteEditDecision {
        require(remoteRevision > 0) { "Bulut sürümü geçersiz." }
        // Silinmiş/izlenmeyen anılar düzenleme kapsamında değildir (Faz B / ilk kurulum).
        if (!localJournalExists) return RemoteEditDecision.CONFLICT
        if (localState == null || localVersion == null) return RemoteEditDecision.CONFLICT
        if (localChangeType != JournalSyncChangePolicy.UPSERT) return RemoteEditDecision.CONFLICT
        if (localState != JournalSyncChangePolicy.SYNCED) return RemoteEditDecision.CONFLICT
        return when {
            remoteRevision > localVersion -> RemoteEditDecision.APPLY
            remoteRevision == localVersion -> RemoteEditDecision.ALREADY_CURRENT
            else -> RemoteEditDecision.LOCAL_AHEAD
        }
    }

    /**
     * Yükleme öncesi kontrol: bulutta bu anı için, gönderilecek sürüme EŞİT veya
     * daha yüksek başka bir olay varsa diğer cihaz aynı tabandan düzenleme yapmıştır.
     * Bu olay gönderilmez; çakışma olarak işaretlenir ve kuyruğun geri kalanı devam eder.
     *
     * @param otherRemoteRevisions bu anıya ait, gönderilecek olayın kendi belgesi
     *        DIŞINDAKİ bulut olaylarının sürümleri
     */
    fun uploadWouldConflict(localVersion: Long, otherRemoteRevisions: List<Long>): Boolean {
        require(localVersion > 0) { "Yerel sürüm geçersiz." }
        return otherRemoteRevisions.any { it >= localVersion }
    }

    /**
     * "Benimkini koru" kararı: yerel metnin bulut sürümlerinin hepsinden yüksek
     * bir sürümle yeniden gönderilmesi gerekir; aksi halde yine çakışır.
     */
    fun versionToKeepLocal(highestRemoteRevision: Long, localVersion: Long): Long {
        require(highestRemoteRevision > 0 && localVersion > 0) { "Sürüm geçersiz." }
        val base = maxOf(highestRemoteRevision, localVersion)
        require(base < Long.MAX_VALUE) { "Sürüm sınırı aşıldı." }
        return base + 1
    }
}
