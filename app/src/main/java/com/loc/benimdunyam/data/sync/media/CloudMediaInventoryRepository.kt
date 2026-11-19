package com.loc.benimdunyam.data.sync.media

import com.loc.benimdunyam.core.cloud.SparkMediaPolicy

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.loc.benimdunyam.data.local.media.JournalPhotoStore
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.CancellationException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.ArrayDeque
import javax.inject.Inject

/** Firebase görevini ek bir coroutines-play-services bağımlılığı eklemeden bekler. */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase görevi başarısız."))
        }
    }
}

/**
 * Faz 2G.4 — Sadece doğrulanan HESAP ve belirli MEDYA klasörü için rapor çıkarır.
 * İçerik indirmez, hiçbir Firebase belgesini değiştirmez ve dosya silmez.
 */
class CloudMediaInventoryRepository @Inject constructor(
    private val cleanupGateway: VerifiedMediaCleanupGateway,
    private val deletionRepository: EncryptedMediaDeletionRepository,
    private val photoStore: JournalPhotoStore,
    private val session: ActiveWorkspaceSession,
    private val codec: EncryptedMediaCodec
) {
    companion object {
        private const val PAGE_SIZE = 50
        private const val MAX_PAGES = 30
        private const val MAX_FOLDERS = 120
        private const val MAX_OBJECTS = 400
        private const val MAX_MARKERS = 501L
        private const val PLAN_DURATION_MS = 2 * 60 * 1000L
        // Tek işlemde az sayıda fotoğraf: riski, süreyi ve maliyeti sınırlarız.
        private const val MAX_CLEANUP_CANDIDATES = 3
    }

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val storage: FirebaseStorage get() = FirebaseStorage.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun requireAuthorizedPrefix(uid: String, mediaPrefix: String) {
        SparkMediaPolicy.requireRemoteMedia()
        require(uid.isNotBlank() && auth.currentUser?.uid == uid &&
                session.isReadyFor(uid) && !session.needsRestart()) {
            "Bulut hesabı değişti veya kullanıcı oturumu kapalı."
        }
        // Yalnızca mevcut uygulamanın yazdığı gerçek şifreli medya klasörü.
        require(mediaPrefix == "users/$uid/encrypted_media_v1") {
            "Yalnızca aktif hesabın şifreli medya klasörü taranabilir."
        }
    }

    /**
     * listAll() yerine sayfalı list() kullanır. Limit dolduğunda hiçbir eksik listeyi
     * 'tam rapor' olarak sunmaz; hata verir. Cloud Storage Rules v2 gerekir.
     */
    suspend fun inspect(uid: String, mediaPrefix: String): CloudMediaInventory {
        requireAuthorizedPrefix(uid, mediaPrefix)
        val queue = ArrayDeque<StorageReference>()
        val root = storage.reference.child(mediaPrefix)
        queue.add(root)
        val all = mutableListOf<StorageReference>()
        var pageCount = 0
        var folderCount = 1

        while (queue.isNotEmpty()) {
            val folder = queue.removeFirst()
            var pageToken: String? = null
            do {
                requireAuthorizedPrefix(uid, mediaPrefix)
                if (pageCount >= MAX_PAGES) error("Tarama güvenlik sınırını aştı ($MAX_PAGES sayfa). Rapor tamamlanmadı.")
                val page = if (pageToken == null) {
                    folder.list(PAGE_SIZE).awaitResult()
                } else {
                    folder.list(PAGE_SIZE, pageToken).awaitResult()
                }
                pageCount++
                for (child in page.prefixes) {
                    if (++folderCount > MAX_FOLDERS) error("Çok fazla medya alt klasörü var. Rapor tamamlanmadı.")
                    check(child.path.startsWith("$mediaPrefix/")) { "Tarama kökünden dışarı çıkıldı." }
                    queue.add(child)
                }
                for (item in page.items) {
                    if (all.size >= MAX_OBJECTS) error("Çok fazla medya nesnesi var. Rapor tamamlanmadı.")
                    check(item.path.startsWith("$mediaPrefix/")) { "Tarama kökünden dışarı çıkıldı." }
                    all.add(item)
                }
                pageToken = page.pageToken
            } while (pageToken != null)
        }

        val found = ArrayList<CloudMediaObject>(all.size)
        for (ref in all) {
            requireAuthorizedPrefix(uid, mediaPrefix)
            // Metadata okunamazsa boyutu sıfır varsaymak yerine taramayı iptal et.
            val metadata = ref.metadata.awaitResult()
            require(metadata.sizeBytes >= 0L) { "Geçersiz dosya boyutu." }
            found += CloudMediaObject(
                bucket = ref.bucket,
                fullPath = ref.path,
                bytes = metadata.sizeBytes,
                updatedAtMs = metadata.updatedTimeMillis,
                generation = metadata.generation,
                md5Hash = metadata.md5Hash
            )
        }

        // Şifreli silme defterinin yalnızca sunucu üzerinde mevcut belge sayısı.
        // Belgeleri şifresizmiş gibi ayrıştırmak veya path eşleştirmek GÜVENSİZ olur.
        val markers = firestore.collection("users").document(uid)
            .collection("encrypted_media_deletions")
            .limit(MAX_MARKERS)
            .get(Source.SERVER)
            .awaitResult()
        requireAuthorizedPrefix(uid, mediaPrefix)
        return CloudMediaInventory(
            uid = uid,
            mediaPrefix = mediaPrefix,
            generatedAtMs = System.currentTimeMillis(),
            objects = found.sortedBy { it.fullPath },
            cloudDeletionMarkerCount = markers.size(),
            markerCountTruncated = markers.size() >= MAX_MARKERS
        )
    }

    suspend fun cleanupGatewayReady(uid: String): Boolean =
        SparkMediaPolicy.REMOTE_MEDIA_ENABLED &&
                auth.currentUser?.uid == uid && cleanupGateway.isReady(uid)

    /**
     * Listeyi görmek = temizleme hakkı değildir. Her dosya için ayrı güvenilir kanıt şarttır.
     * Önizlemede bir tek nesne bile koşulları sağlamıyorsa aday listesine alınmaz.
     */
    suspend fun prepareCleanup(inventory: CloudMediaInventory, password: CharArray): PreparedMediaCleanup {
        requireAuthorizedPrefix(inventory.uid, inventory.mediaPrefix)
        require(password.size in 12..128) { "Medya aktarım şifresini gir." }
        check(cleanupGateway.isReady(inventory.uid)) { "Güvenli temizleme sunucuda kilitli." }
        check(System.currentTimeMillis() - inventory.generatedAtMs in 0L until PLAN_DURATION_MS) {
            "Rapor eski; yeniden bulut taraması yap."
        }
        // Önce şifreli silme defterini server'dan tamamen getir ve AES-GCM ile doğrula.
        val deletions = deletionRepository.listVerifiedRemote(inventory.uid, password)
        val accepted = mutableListOf<CloudMediaObject>()
        val permits = mutableListOf<VerifiedMediaCleanupPermit>()
        val byPath = inventory.objects.associateBy { it.fullPath }
        val localIds = mutableMapOf<String, Set<String>>()
        val entries = firestore.collection("users").document(inventory.uid)
            .collection("encrypted_media_entries")
        for (deletion in deletions) {
            if (accepted.size >= MAX_CLEANUP_CANDIDATES) break
            requireAuthorizedPrefix(inventory.uid, inventory.mediaPrefix)
            // Silme kaydı bulutta olsa bile bu cihazda hâlâ duran fotoğrafı atla.
            val present = localIds[deletion.journalId]
                ?: photoStore.list(deletion.journalId).map { it.id }.toSet().also {
                    localIds[deletion.journalId] = it
                }
            if (deletion.photoId in present) continue
            val fileNameHash = codec.sha256("${inventory.uid}|${deletion.journalId}|${deletion.photoId}")
            val ownerHash = codec.sha256("${inventory.uid}|JOURNAL_PHOTO|${deletion.journalId}")
            // Tek alan indeksli sorgu: başka içerik sürümleri varsa temizleme yapma.
            val matches = entries.whereEqualTo("fileNameHash", fileNameHash)
                .limit(2).get(Source.SERVER).awaitResult()
            check(matches.size() <= 1) {
                "Aynı fotoğrafın birden fazla bulut sürümü var; manuel inceleme gerekli."
            }
            val doc = matches.documents.singleOrNull() ?: continue
            val path = "${inventory.mediaPrefix}/${doc.id}.bin"
            val item = byPath[path] ?: continue
            check(doc.id.matches(Regex("^[0-9a-f]{64}$")) &&
                    doc.getString("storagePath") == path &&
                    doc.getString("ownerUid") == inventory.uid &&
                    doc.getString("kind") == "JOURNAL_PHOTO" &&
                    doc.getLong("formatVersion") == 1L &&
                    doc.getString("fileNameHash") == fileNameHash &&
                    doc.getString("ownerHash") == ownerHash) {
                "Medya manifesti şifreli silme kaydıyla uyuşmuyor."
            }
            if (item.generation.isNullOrBlank()) continue
            val permit = cleanupGateway.prepare(inventory.uid, item, deletion.journalId, deletion.photoId)
                ?: continue
            check(permit.uid == inventory.uid && permit.bucket == item.bucket &&
                    permit.fullPath == item.fullPath && permit.generation == item.generation &&
                    permit.journalId == deletion.journalId && permit.photoId == deletion.photoId &&
                    permit.immutableDeletionRecordId.isNotBlank() && permit.opaqueProof.isNotBlank() &&
                    permit.expiresAtMs > System.currentTimeMillis()) {
                "Sunucunun döndürdüğü silme izni hatalı."
            }
            accepted += item
            permits += permit
        }
        val now = System.currentTimeMillis()
        val until = minOf(now + PLAN_DURATION_MS, permits.minOfOrNull { it.expiresAtMs } ?: now)
        check(accepted.isNotEmpty() && until > now) {
            "Silme kanıtı doğrulanmış ve 7 günlük koruma süresi dolmuş medya bulunamadı."
        }
        return PreparedMediaCleanup(
            uid = inventory.uid, mediaPrefix = inventory.mediaPrefix,
            createdAtMs = now, expiresAtMs = until, objects = accepted, permits = permits
        )
    }

    /**
     * Bu çağrı yalnızca AYRI bir onay diyaloğu sonucunda ViewModel tarafından yapılır.
     * Tam yol/sürüm tekrar kontrol edilir. Gerçek silme SERVER'da generation precondition
     * ile yapılmadıkça Gateway başarısız olmalıdır. Android StorageReference.delete() YOK.
     */
    suspend fun deleteAfterSeparateConfirmation(plan: PreparedMediaCleanup): CloudCleanupExecutionResult {
        requireAuthorizedPrefix(plan.uid, plan.mediaPrefix)
        check(cleanupGateway.isReady(plan.uid)) { "Sunucu silme servisi hazır değil." }
        check(plan.objects.isNotEmpty() && plan.objects.size == plan.permits.size) { "Geçersiz temizleme planı." }
        check(System.currentTimeMillis() < plan.expiresAtMs) { "Onay süresi doldu. Yeniden tara." }
        val deleted = mutableListOf<String>()
        val failures = mutableListOf<String>()

        check(plan.objects.size <= MAX_CLEANUP_CANDIDATES) { "İzinli temizleme kotası aşıldı." }
        for ((item, permit) in plan.objects.zip(plan.permits)) {
            try {
                requireAuthorizedPrefix(plan.uid, plan.mediaPrefix)
                check(System.currentTimeMillis() < plan.expiresAtMs) { "Temizleme planının süresi doldu." }
                check(item.fullPath.startsWith("${plan.mediaPrefix}/")) { "Medya klasörü dışına çıkıldı." }
                check(permit.uid == plan.uid && permit.fullPath == item.fullPath && permit.bucket == item.bucket)
                check(permit.generation == item.generation && permit.expiresAtMs > System.currentTimeMillis())
                val latest = storage.getReferenceFromUrl("gs://${item.bucket}/${item.fullPath}").metadata.awaitResult()
                check(latest.generation != null && latest.generation == item.generation) {
                    "Dosya sürümü değişti; atlandı."
                }
                check(latest.sizeBytes == item.bytes && latest.md5Hash == item.md5Hash) {
                    "Dosya metadata'sı değişti; atlandı."
                }
                // İKİNCİ kanıt sorgusu: eski önizlemeye dayanıp silme yok.
                check(photoStore.list(permit.journalId).none { it.id == permit.photoId }) {
                    "Fotoğraf bu cihazda hâlâ mevcut. Temizleme durduruldu."
                }
                val repeated = cleanupGateway.prepare(plan.uid, item, permit.journalId, permit.photoId)
                check(repeated != null && repeated == permit) {
                    "Şifreli silme kanıtı artık geçerli değil; atlandı."
                }
                cleanupGateway.deleteAtomically(plan.uid, item, permit)
                deleted += item.fullPath
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (problem: Exception) {
                // Hata durumunda kalan nesneleri silmek için ısrar etmiyoruz.
                failures += "${item.fullPath}: ${problem.message ?: "doğrulama hatası"}"
                break
            }
        }
        return CloudCleanupExecutionResult(deletedPaths = deleted, failures = failures)
    }
}
