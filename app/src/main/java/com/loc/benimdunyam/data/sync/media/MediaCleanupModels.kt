package com.loc.benimdunyam.data.sync.media

/**
 * Faz 2G.4 — Salt okunur Storage taramasında elde edilen NESNE metadata'sı.
 * Şifreli dosyanın içeriği okunmaz; yalnızca dosya boyutu ve sürüm bilgileri okunur.
 * Bir Storage nesnesinin varlığı, onun silinebilir olduğu anlamına GELMEZ.
 */
data class CloudMediaObject(
    val bucket: String,
    val fullPath: String,
    val bytes: Long,
    val updatedAtMs: Long,
    val generation: String?,
    val md5Hash: String?
)

/** Tamamlanmamış bir tarama, yanlış boyut hesabına ve tehlikeli bir seçime yol açabilir. */
data class CloudMediaInventory(
    val uid: String,
    val mediaPrefix: String,
    val generatedAtMs: Long,
    val objects: List<CloudMediaObject>,
    // Firestore'daki şifreli silme belgeleri sadece SAYILIR, çözülmez veya eşleştirilmez.
    val cloudDeletionMarkerCount: Int,
    val markerCountTruncated: Boolean
) {
    val objectCount: Int get() = objects.size
    val totalBytes: Long get() = objects.sumOf { it.bytes }
}

/**
 * Bu izin sadece güvenilir 2G.3 entegrasyonu + atomik sunucu silme işleyicisi tarafından üretilebilir.
 * Doğrulanmamış bir Firestore belgesini ya da yalnızca fotoğraf ismini izin sayma.
 */
data class VerifiedMediaCleanupPermit(
    val uid: String,
    val bucket: String,
    val fullPath: String,
    val generation: String,
    val immutableDeletionRecordId: String,
    // Yalnızca AES-GCM çözülen 2G.3 kaydından gelir.
    val journalId: String,
    val photoId: String,
    val expiresAtMs: Long,
    val opaqueProof: String
)

data class PreparedMediaCleanup(
    val uid: String,
    val mediaPrefix: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val permits: List<VerifiedMediaCleanupPermit>,
    val objects: List<CloudMediaObject>
) {
    val reclaimableBytes: Long get() = objects.sumOf { it.bytes }
}

data class CloudCleanupExecutionResult(
    val deletedPaths: List<String>,
    val failures: List<String>
)
