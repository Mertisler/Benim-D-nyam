package com.loc.benimdunyam.domain.sync

import java.util.concurrent.TimeUnit

/** Buluttaki bir DELETE olayının bu cihaz için anlamı. */
enum class RemoteDeleteDecision {
    /** Yalnızca diğer cihaz sildi; bu cihazda değişiklik yok: "Son silinenler"e taşı. */
    MOVE_TO_TRASH,
    /** Anı bu cihazda zaten yok. */
    ALREADY_GONE,
    /** Bu cihazda gönderilmemiş/çakışan değişiklik var veya durum belirsiz: dokunma. */
    CONFLICT
}

/**
 * Faz B — uzaktan silmenin saf karar kuralları.
 *
 * İlke Faz C ile aynıdır: bu cihazda gönderilmemiş değişiklik yoksa ve buluttaki
 * silme daha yeniyse, silmeyi yalnızca diğer cihaz yapmıştır. Yine de anı
 * kalıcı silinmez; [TRASH_RETENTION_MILLIS] boyunca geri getirilebilir.
 *
 * Android / Firebase bağımlılığı yoktur.
 */
object RemoteDeletePolicy {

    /** "Son silinenler" saklama süresi. */
    val TRASH_RETENTION_MILLIS: Long = TimeUnit.DAYS.toMillis(30)

    fun classify(
        localJournalExists: Boolean,
        localState: String?,
        localChangeType: String?,
        localVersion: Long?,
        remoteRevision: Long
    ): RemoteDeleteDecision {
        require(remoteRevision > 0) { "Bulut sürümü geçersiz." }
        if (!localJournalExists) return RemoteDeleteDecision.ALREADY_GONE
        if (localState != JournalSyncChangePolicy.SYNCED ||
            localChangeType != JournalSyncChangePolicy.UPSERT ||
            localVersion == null) return RemoteDeleteDecision.CONFLICT
        return if (remoteRevision > localVersion) RemoteDeleteDecision.MOVE_TO_TRASH
        else RemoteDeleteDecision.CONFLICT
    }

    /**
     * Bu cihazda silinmiş (ve silmesi buluta ulaşmış) bir anı, başka cihazda
     * daha yüksek bir sürümle GERİ GETİRİLDİYSE yeni anı gibi içe alınabilir.
     * Bu cihazda bekleyen bir değişiklik varsa alınmaz.
     */
    fun isRestoredElsewhere(
        localState: String?,
        localChangeType: String?,
        localVersion: Long?,
        remoteRevision: Long
    ): Boolean = localState == JournalSyncChangePolicy.SYNCED &&
            localChangeType == JournalSyncChangePolicy.DELETE &&
            localVersion != null && remoteRevision > localVersion

    fun expiresAt(trashedAt: Long): Long {
        require(trashedAt > 0) { "Silinme zamanı geçersiz." }
        return if (trashedAt > Long.MAX_VALUE - TRASH_RETENTION_MILLIS) Long.MAX_VALUE
        else trashedAt + TRASH_RETENTION_MILLIS
    }

    fun isExpired(expiresAt: Long, now: Long): Boolean = now >= expiresAt

    /** Ekranda gösterilecek kalan gün (en az 0). */
    fun daysLeft(expiresAt: Long, now: Long): Long {
        val remaining = expiresAt - now
        if (remaining <= 0) return 0
        return (remaining + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1)
    }
}
