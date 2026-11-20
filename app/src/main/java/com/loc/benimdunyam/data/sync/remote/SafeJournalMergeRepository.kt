package com.loc.benimdunyam.data.sync.remote

import com.loc.benimdunyam.domain.sync.RemoteEditPolicy
import com.loc.benimdunyam.domain.sync.RemoteEditDecision
import android.util.Base64
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.gson.Gson
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalPersonMentionDao
import com.loc.benimdunyam.data.local.dao.JournalSyncEventDao
import com.loc.benimdunyam.data.local.dao.LifeEpisodeDao
import com.loc.benimdunyam.data.local.dao.LifeSeasonDao
import com.loc.benimdunyam.data.local.dao.PersonDao
import com.loc.benimdunyam.data.local.dao.PendingMoodMergeDao
import com.loc.benimdunyam.data.local.dao.PersonSpecialMemoryDao
import com.loc.benimdunyam.data.local.entity.JournalEntryEntity
import com.loc.benimdunyam.data.local.entity.JournalPersonMentionEntity
import com.loc.benimdunyam.data.local.entity.JournalSyncEventEntity
import com.loc.benimdunyam.data.local.entity.LifeEpisodeEntity
import com.loc.benimdunyam.data.local.entity.LifeSeasonEntity
import com.loc.benimdunyam.data.local.entity.PersonEntity
import com.loc.benimdunyam.data.local.entity.PendingMoodMergeEntity
import com.loc.benimdunyam.data.local.mood.JournalMoodRecord
import com.loc.benimdunyam.data.local.mood.JournalMoodRepository
import com.loc.benimdunyam.data.backup.BackupWriteCoordinator
import com.loc.benimdunyam.data.local.entity.PersonSpecialMemoryEntity
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.remote.firebase.archive.CloudArchiveCrypto
import com.loc.benimdunyam.domain.model.JournalMood
import com.loc.benimdunyam.domain.sync.JournalSyncChangePolicy
import com.loc.benimdunyam.domain.sync.RemoteJournalEventPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Ekrana yalnızca sayılar ve doğrulama özeti gider; anı içeriği aktarılmaz. */
data class SafeMergePreview(
    val fingerprint: String,
    val totalEvents: Int,
    val eligible: Int,
    val existingOrPending: Int,
    val remoteDeletes: Int,
    val staleCatalog: Int,
    val missingOrConflictingRelations: Int,
    val importableWithMood: Int,
    val pendingMoodFinishes: Int,
    val remoteDeleteConflicts: Int,
    val batchLimit: Int = 5,
    /** Faz C: yalnızca diğer cihazda düzenlenmiş, güvenle uygulanabilecek anılar. */
    val remoteEdits: Int = 0,
    /** Faz C: iki tarafta da değişmiş, kullanıcı kararı bekleyen anılar. */
    val editConflicts: Int = 0,
    /** Aynı sürümde birden fazla farklı bulut olayı olan anılar (atlanır). */
    val inconsistentHeads: Int = 0
)

/** Faz C: uygulanan uzak düzenleme sayısı. */
data class RemoteEditResult(val updatedJournals: Int)

/** Tek onayda en fazla beş anı ve bunların ana Room ilişkileri eklenir. */
data class SafeMergeResult(
    val insertedJournals: Int,
    val restoredMoods: Int,
    val pendingMoodFinishes: Int
)

/** Tamamlanan ruh hali sayısı ve güvenlik nedeniyle bekleyen kalan işaretler. */
data class MoodMergeRecovery(val completed: Int, val remaining: Int)

/** Şifresi çözülmüş kayıtlar yalnızca işlemin geçici belleğinde tutulur. */
private data class MergeRemoteEvent(
    val documentId: String,
    val journalId: String,
    val operation: String,
    val revision: Long,
    val changeRecordedAt: Long,
    val journal: JournalEntryEntity?,
    val mentions: List<JournalPersonMentionEntity>
)

private data class MergePlan(
    val summary: SafeMergePreview,
    val catalog: SyncDependencyCatalog,
    val eligible: List<MergeRemoteEvent>,
    val updates: List<MergeRemoteEvent> = emptyList()
)

private data class RemoteEventSet(
    val latest: List<MergeRemoteEvent>,
    val token: String,
    val inconsistentJournals: Int
)

/**
 * Faz 2F.6 - KULLANICI ONAYLI, YALNIZCA YENİ ANI EKLEYEN birleştirme.
 *
 * Güvenlik sınırları:
 * 1) Her olay ve ilişki kataloğu Firestore SUNUCUSUNDAN alınır ve AES-GCM doğrulanır.
 * 2) Anı veya silme geçmişi yerelde varsa UZAK VERİ hiçbir zaman baskın sayılmaz.
 * 3) Bir anının kişi/dönem/bölüm bağımlılıkları ana Room transaction'ında eklenir.
 * 4) Ruh hali gereken anılar ana Room'a, KURTARMA İŞARETİYLE birlikte eklenir;
 *    ruh hali ikinci Room'a yalnızca BOŞ yere uygulanır (mevcut değer ezilmez).
 * 5) İki ayrı veritabanı TEK transaction olamaz: uygulama kapanırsa işaret kalır,
 *    kullanıcı daha sonra "yarım kalanları tamamla" ile devam edebilir.
 * 6) Fotoğraflar ve kullanıcıya ait mevcut hiçbir kayıt silinmez/değiştirilmez.
 * 7) Önizlemeden sonra bulut içeriği değişirse kullanıcı onayı geçersiz olur.
 *
 * Tam çift yönlü senkronizasyon değildir. DELETE ve çakışma çözümü KAPALIDIR.
 */
@Singleton
class SafeJournalMergeRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val workspace: ActiveWorkspaceSession,
    private val database: AppDatabase,
    private val journalDao: JournalDao,
    private val eventDao: JournalSyncEventDao,
    private val personDao: PersonDao,
    private val seasonDao: LifeSeasonDao,
    private val episodeDao: LifeEpisodeDao,
    private val mentionDao: JournalPersonMentionDao,
    private val specialDao: PersonSpecialMemoryDao,
    private val pendingMoodDao: PendingMoodMergeDao,
    private val moodRepository: JournalMoodRepository,
    private val writeCoordinator: BackupWriteCoordinator
) {
    private val mutex = Mutex()
    private val gson = Gson()

    private fun requireAccount(uid: String) {
        check(uid.isNotBlank() && auth.currentUser?.uid == uid &&
                workspace.isReadyFor(uid) && !workspace.needsRestart()) {
            "Firebase hesabı ile etkin yerel arşiv uyuşmuyor."
        }
    }

    suspend fun preview(uid: String, password: CharArray): SafeMergePreview = mutex.withLock {
        withContext(Dispatchers.IO) { prepare(uid, password).summary }
    }

    /** Kullanıcının önceki onayla hazırladığı fakat bitmemiş ruh hali kayıtları. */
    suspend fun pendingMoodCount(uid: String): Int = withContext(Dispatchers.IO) {
        requireAccount(uid)
        pendingMoodDao.pendingCount(uid)
    }

    /**
     * Daha önce ONAYLANMIŞ içe aktarmanın yarım kalan ruh hallerini tamamlar.
     * Ağ, parola veya sunucu gerekli değildir; kaynak ruh hali ve sürümü ana Room'dadır.
     * Kaydı olmayan veya cihazda sonradan değişmiş anıya DOKUNMAZ.
     */
    suspend fun resumePendingMoods(uid: String): MoodMergeRecovery = mutex.withLock {
        withContext(Dispatchers.IO) {
            requireAccount(uid)
            writeCoordinator.withExclusiveRestore { finishPendingMoods(uid) }
        }
    }

    /**
     * Önizleme onayı kalıcı değildir. Sunucu her defasında yeniden okunur ve
     * aynı parmak izi bulunamazsa hiçbir YENİ anı aktarılmaz.
     *
     * Önce ana Room'a anı + kişi/dönem/bölüm + ruh hali işareti ATOMİK yazılır;
     * ardından ruh hali Room'una eksikse eklenir. İkinci adım yarım kalırsa
     * birinci adım GERİ ALINMAZ; kalıcı işaret kurtarmayı mümkün kılar.
     */
    suspend fun importMissing(
        uid: String,
        password: CharArray,
        expectedFingerprint: String,
        userConfirmed: Boolean
    ): SafeMergeResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(userConfirmed) { "İçe aktarma için açık onay gerekiyor." }
            require(expectedFingerprint.matches(Regex("[a-f0-9]{64}"))) {
                "Önizleme doğrulama anahtarı geçersiz."
            }
            requireAccount(uid)
            val plan = prepare(uid, password)
            check(plan.summary.fingerprint == expectedFingerprint) {
                "Bulut olayları veya katalog değişmiş. Yeniden önizleme yap."
            }
            val batch = plan.eligible.take(MAX_IMPORT_PER_CONFIRMATION)
            require(batch.isNotEmpty()) { "Güvenle eklenebilecek yeni anı bulunamadı." }

            writeCoordinator.withExclusiveRestore {
                requireAccount(uid)
                check(pendingMoodDao.pendingCount(uid) == 0) {
                    "Önce yarım kalan ruh hali aktarımlarını kontrol et ve tamamla."
                }
                // ÇOK ÖNEMLİ: Ağ erişimi transaction DIŞINDADIR. Ana Room işlemi kısa tutulur.
                database.withTransaction {
                    requireAccount(uid)
                    for (remote in batch) {
                        check(isSafeNewRecord(uid, remote, plan.catalog)) {
                            "Yerel kayıtlar değişti. Yeni anılar eklenmeden işlem durduruldu."
                        }
                    }
                    for (remote in batch) {
                        val journal = requireNotNull(remote.journal)
                        val catalog = plan.catalog
                        val season = catalog.seasons.firstOrNull { it.id == journal.seasonId }
                        val episode = catalog.episodes.firstOrNull { it.id == journal.episodeId }
                        val people = remote.mentions.map { mention ->
                            catalog.people.first { it.id == mention.personId }
                        }
                        // Var olan bağımlılıkları asla REPLACE etmeyiz.
                        if (season != null && seasonDao.getSeasonById(season.id) == null) {
                            seasonDao.insertSeason(season)
                        }
                        if (episode != null && episodeDao.getEpisodeById(episode.id) == null) {
                            episodeDao.insertEpisode(episode)
                        }
                        people.distinctBy { it.id }.forEach { person ->
                            val sameName = personDao.getPersonByName(person.name)
                            check(sameName == null || sameName.id == person.id) {
                                "Farklı kimlikli kişiler aynı ada sahip. Tüm işlem geri alındı."
                            }
                            if (personDao.getPersonById(person.id) == null) {
                                personDao.insertPerson(person)
                            }
                        }
                        journalDao.insertJournal(journal)
                        remote.mentions.forEach { mentionDao.insertMention(it) }
                        catalog.specialMemories.filter { it.journalId == journal.id }
                            .forEach { specialDao.insert(it) }

                        // Uzak kaydı tekrar PENDING olarak buluta yüklemiyoruz.
                        eventDao.save(JournalSyncEventEntity(
                            journalId = journal.id,
                            ownerUid = uid,
                            changeType = JournalSyncChangePolicy.UPSERT,
                            state = "SYNCED",
                            changeVersion = remote.revision,
                            updatedAtMillis = remote.changeRecordedAt
                        ))

                        // Ruh hali varsa işareti ANININ EKLENDİĞİ AYNI transaction'a koy.
                        catalog.moods.firstOrNull { it.journalId == journal.id }?.let { mood ->
                            pendingMoodDao.insert(PendingMoodMergeEntity(
                                journalId = journal.id,
                                ownerUid = uid,
                                moodName = mood.moodName,
                                moodUpdatedAt = mood.updatedAt,
                                remoteRevision = remote.revision,
                                remoteChangedAt = remote.changeRecordedAt
                            ))
                        }
                    }
                    requireAccount(uid)
                }

                // Ana Room artık commit edildi. Sonraki farklı DB işlemi hata verirse
                // kullanıcıya "işlem tamamen geri alındı" DEMEYİZ.
                val completed = try {
                    finishPendingMoods(uid).completed
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Hata yüzünden kaydedilmiş anıyı veya işareti silmiyoruz.
                    // Bir sonraki "Yarım kalanları tamamla" işlemi yeniden dener.
                    requireAccount(uid)
                    0
                }
                SafeMergeResult(
                    insertedJournals = batch.size,
                    restoredMoods = completed,
                    pendingMoodFinishes = pendingMoodDao.pendingCount(uid)
                )
            }
        }
    }

    /**
     * İki Room dosyası arasında tekrarlanabilir bir tamamlama (saga/reconciliation).
     * Sadece bu hesaba ait, hâlâ SYNCED olan ve sürümü değişmemiş anılarda çalışır.
     * Farklı veya yeni oluşturulmuş yerel ruh hali asla EZİLMEZ.
     */
    private suspend fun finishPendingMoods(uid: String): MoodMergeRecovery {
        requireAccount(uid)
        var finished = 0
        for (pending in pendingMoodDao.pendingForAccount(uid)) {
            requireAccount(uid)
            val sameUnchangedImport = database.withTransaction {
                val currentStage = pendingMoodDao.find(pending.journalId)
                val journal = journalDao.getJournalById(pending.journalId)
                val sync = eventDao.findByJournalId(pending.journalId)
                currentStage == pending && journal != null &&
                        sync?.ownerUid == uid &&
                        sync.state == "SYNCED" &&
                        sync.changeType == JournalSyncChangePolicy.UPSERT &&
                        sync.changeVersion == pending.remoteRevision &&
                        sync.updatedAtMillis == pending.remoteChangedAt
            }
            if (!sameUnchangedImport) continue

            val insertedOrSame = moodRepository.importMoodWithoutOverwrite(
                uid, JournalMoodRecord(pending.journalId,
                    pending.moodName, pending.moodUpdatedAt)
            )
            if (!insertedOrSame) continue // Kullanıcının ruh hali değişmiş; koru.

            // Tekrarlı çağrıda doğru işareti kaldır; başka sürümü asla temizleme.
            database.withTransaction {
                val stage = pendingMoodDao.find(pending.journalId)
                val sync = eventDao.findByJournalId(pending.journalId)
                if (stage == pending && sync?.ownerUid == uid &&
                    sync.state == "SYNCED" && sync.changeVersion == pending.remoteRevision &&
                    journalDao.getJournalById(pending.journalId) != null) {
                    finished += pendingMoodDao.deleteIfSame(
                        pending.journalId, uid, pending.remoteRevision
                    )
                }
            }
        }
        requireAccount(uid)
        return MoodMergeRecovery(finished, pendingMoodDao.pendingCount(uid))
    }

    /** Şifreli kataloğu parçalarıyla birlikte ve yalnızca gerçek sunucudan okur. */
    private suspend fun readCatalog(uid: String, password: CharArray): Pair<SyncDependencyCatalog, String> {
        val doc = firestore.collection("users").document(uid)
            .collection("sync_dependency_snapshots")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1).get(Source.SERVER).await().documents.firstOrNull()
            ?: error("Önce Faz 2F.5'te şifreli ilişki kataloğu oluşturmalısın.")
        requireAccount(uid)
        check(doc.getLong("formatVersion") == 1L && doc.getString("ownerUid") == uid)
        val count = doc.getLong("partCount")?.toInt() ?: error("Katalog parça sayısı eksik.")
        val length = doc.getLong("encodedLength")?.toInt() ?: error("Katalog boyutu eksik.")
        require(count in 1..80 && length in 1..8_000_000) { "Katalog boyutu geçersiz." }
        val encoded = StringBuilder(length)
        for (i in 0 until count) {
            requireAccount(uid)
            val part = doc.reference.collection("parts")
                .document("part_${i.toString().padStart(4, '0')}")
                .get(Source.SERVER).await()
            check(part.exists() && part.getLong("index") == i.toLong()) {
                "Katalog parçası eksik veya sırası yanlış."
            }
            encoded.append(part.getString("data") ?: error("Katalog parçası boş."))
            require(encoded.length <= 8_000_000) { "Katalog sınırı aşıldı." }
        }
        check(encoded.length == length) { "Katalog uzunluğu uyuşmuyor." }
        val bytes = Base64.decode(encoded.toString(), Base64.NO_WRAP)
        val raw = try {
            check(sha256Hex(bytes) == doc.getString("sha256")) { "Katalog bütünlüğü bozuk." }
            CloudArchiveCrypto.decrypt(bytes, password)
        } finally { bytes.fill(0) }
        val catalog = gson.fromJson(raw, SyncDependencyCatalog::class.java)
            ?: error("Katalog okunamadı.")
        catalog.validatedIndex(uid)
        check(catalog.capturedAtMillis == doc.getLong("capturedAtMillis")) {
            "Katalog zaman bilgisi uyuşmuyor."
        }
        return catalog to "${doc.id}:${doc.getString("sha256")}" // İçeriği değil, özeti saklarız.
    }

    /** Değiştirilemeyen olay belgelerini 50'şer okuyarak TAM koleksiyonu inceler. */
    private suspend fun readEvents(uid: String, password: CharArray): RemoteEventSet {
        val latest = linkedMapOf<String, MergeRemoteEvent>()
        // Faz C: aynı sürümde çelişen olaylar yalnızca O anıyı atlatır;
        // önceden tüm birleştirmeyi kalıcı olarak durduruyordu.
        val inconsistent = mutableSetOf<String>()
        val digest = MessageDigest.getInstance("SHA-256")
        var cursor: DocumentSnapshot? = null
        var processed = 0
        var totalPlainChars = 0
        while (true) {
            requireAccount(uid)
            val base = firestore.collection("users").document(uid)
                .collection("journal_change_events")
                .orderBy("uploadedAt", Query.Direction.ASCENDING).limit(50)
            val page = (if (cursor == null) base else base.startAfter(cursor))
                .get(Source.SERVER).await()
            if (page.documents.isEmpty()) break
            for (doc in page.documents) {
                check(++processed <= MAX_EVENTS) { "2.000 olay sınırı aşıldı; kısmi aktarım yapılmaz." }
                val encoded = doc.getString("encryptedPayload") ?: error("Olay içeriği eksik.")
                require(encoded.length in 51..600_000) { "Şifreli olay boyutu geçersiz." }
                digest.update(doc.id.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
                digest.update(encoded.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                val raw = try { CloudArchiveCrypto.decrypt(bytes, password) }
                finally { bytes.fill(0) }
                totalPlainChars += raw.length
                check(totalPlainChars <= 12_000_000) { "Toplam olay boyutu güvenlik sınırını aşıyor." }
                val event = decodeEvent(uid, doc, JSONObject(raw))
                val old = latest[event.journalId]
                when {
                    old == null || event.revision > old.revision -> {
                        latest[event.journalId] = event
                        inconsistent.remove(event.journalId)
                    }
                    event.revision == old.revision && event != old ->
                        inconsistent += event.journalId
                }
            }
            cursor = page.documents.last()
            if (page.size() < 50) break
        }
        requireAccount(uid)
        return RemoteEventSet(
            latest = latest.values.filter { it.journalId !in inconsistent },
            token = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) },
            inconsistentJournals = inconsistent.size
        )
    }

    /** JSON alanları ve belge kimliği, Faz 2F.3 gönderici sözleşmesiyle eşleşmelidir. */
    private fun decodeEvent(uid: String, doc: DocumentSnapshot, json: JSONObject): MergeRemoteEvent {
        check(doc.getLong("formatVersion") == 1L && json.getInt("schema") == 1)
        val id = json.getString("journalId")
        val operation = json.getString("operation")
        val revision = json.getLong("revision")
        RemoteJournalEventPolicy.verify(
            expectedUid = uid, documentId = doc.id,
            headerUid = doc.getString("ownerUid"), headerHash = doc.getString("journalHash"),
            headerOperation = doc.getString("operation"), headerRevision = doc.getLong("revision"),
            payloadUid = json.getString("ownerUid"), payloadJournalId = id,
            payloadOperation = operation, payloadRevision = revision
        )
        val changeTime = json.getLong("changeRecordedAt")
        require(changeTime > 0) { "Bulut olay tarihi geçersiz." }
        if (operation == JournalSyncChangePolicy.DELETE) {
            check(!json.has("journal")) { "Silme olayı anı metni taşıyamaz." }
            return MergeRemoteEvent(doc.id, id, operation, revision, changeTime, null, emptyList())
        }
        val value = json.getJSONObject("journal")
        require(value.getString("id") == id) { "Anı kimliği uyuşmuyor." }
        val journal = JournalEntryEntity(
            id = id,
            title = value.getString("title"),
            content = value.getString("content"),
            entryDate = value.getLong("entryDate"),
            createdAt = value.getLong("createdAt"),
            updatedAt = value.getLong("updatedAt"),
            seasonId = value.optString("seasonId").takeUnless { it.isBlank() || it == "null" },
            episodeId = value.optString("episodeId").takeUnless { it.isBlank() || it == "null" }
        )
        require(id.length in 1..128 && journal.title.length <= 1000 &&
                journal.content.length <= 300_000 && journal.createdAt > 0 && journal.updatedAt > 0) {
            "Bulut anısı geçersiz boyut veya tarih içeriyor."
        }
        val mentionArray = json.getJSONArray("mentions")
        require(mentionArray.length() <= 500) { "Bir anıdaki kişi bağlantıları sınırı aşıyor." }
        val mentions = ArrayList<JournalPersonMentionEntity>()
        repeat(mentionArray.length()) { index ->
            val item = mentionArray.getJSONObject(index)
            fun nullableOffset(key: String): Int? = if (item.isNull(key) || !item.has(key)) null else item.getInt(key)
            mentions += JournalPersonMentionEntity(
                journalId = id,
                personId = item.getString("personId"),
                mentionIndex = item.getInt("mentionIndex"),
                startOffset = nullableOffset("startOffset"),
                endOffset = nullableOffset("endOffset")
            )
        }
        check(mentions.distinctBy { it.personId }.size == mentions.size && mentions.all {
            it.personId.isNotBlank() && it.mentionIndex >= 0 &&
                    ((it.startOffset == null && it.endOffset == null) ||
                            (it.startOffset != null && it.endOffset != null &&
                                    it.startOffset >= 0 && it.endOffset >= it.startOffset &&
                                    it.endOffset <= journal.content.length))
        }) { "Kişi etiketleri geçersiz." }
        return MergeRemoteEvent(doc.id, id, operation, revision, changeTime, journal, mentions)
    }

    /**
     * PREVIEW ile TRANSACTION içinde aynı denetim yürütülür.
     * Bir çakışma fark edilirse mevcut kişiyi/dönemi sessizce değiştirmeyiz.
     */
    /**
     * Faz C: yalnızca diğer cihazda düzenlenmiş bir anının uygulanabilirliği.
     *
     * Yeni anı kurallarından farkı: yerelde zaten var olan kişi / dönem / bölüm
     * için katalogla birebir eşitlik ARANMAZ, çünkü bunlar değiştirilmez; yalnızca
     * anı satırı ve etiketleri güncellenir. Yerelde olmayan bağımlılıklar katalogdan
     * yeni anı kurallarıyla (aynı adlı farklı kişi yok vb.) eklenir.
     */
    private suspend fun isSafeRemoteEdit(
        remote: MergeRemoteEvent, catalog: SyncDependencyCatalog
    ): Boolean {
        val journal = remote.journal ?: return false
        if (remote.operation != JournalSyncChangePolicy.UPSERT ||
            remote.changeRecordedAt > catalog.capturedAtMillis ||
            journal.id !in catalog.journalIds) return false
        // Yarım kalmış ruh hali aktarımı sürüme bağlıdır; önce o tamamlanmalı.
        if (pendingMoodDao.find(journal.id) != null) return false
        // Etiketler katalogla birebir aynı olmalı (diğer cihaz arada yine değiştirmediyse).
        val catalogMentions = catalog.mentions.filter { it.journalId == journal.id }
        if (catalogMentions.toSet() != remote.mentions.toSet()) return false
        if (journal.seasonId != null && seasonDao.getSeasonById(journal.seasonId) == null &&
            catalog.seasons.none { it.id == journal.seasonId }) return false
        if (journal.episodeId != null) {
            val episode = episodeDao.getEpisodeById(journal.episodeId)
                ?: catalog.episodes.firstOrNull { it.id == journal.episodeId }
                ?: return false
            if (episode.seasonId != journal.seasonId) return false
        }
        for (mention in remote.mentions) {
            if (personDao.getPersonById(mention.personId) != null) continue
            val person = catalog.people.firstOrNull { it.id == mention.personId } ?: return false
            val sameName = personDao.getPersonByName(person.name)
            if (sameName != null && sameName.id != person.id) return false
        }
        return true
    }

    /**
     * Faz C: önizlemede bulunan uzak düzenlemeleri uygular (en çok beş anı).
     * Bulut değişmişse (parmak izi) veya yerel kayıt arada düzenlenmişse durur.
     * Kullanıcının bu cihazdaki gönderilmemiş değişikliği ASLA ezilmez: sınıflama
     * transaction içinde yeniden yapılır ve yalnızca SYNCED kayıtlar güncellenir.
     */
    suspend fun applyRemoteEdits(
        uid: String,
        password: CharArray,
        expectedFingerprint: String,
        userConfirmed: Boolean
    ): RemoteEditResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(userConfirmed) { "Uzak düzenlemeleri uygulamak için açık onay gerekiyor." }
            require(expectedFingerprint.matches(Regex("[a-f0-9]{64}"))) {
                "Önizleme doğrulama anahtarı geçersiz."
            }
            requireAccount(uid)
            val plan = prepare(uid, password)
            check(plan.summary.fingerprint == expectedFingerprint) {
                "Bulut olayları veya katalog değişmiş. Yeniden önizleme yap."
            }
            val batch = plan.updates.take(MAX_IMPORT_PER_CONFIRMATION)
            if (batch.isEmpty()) return@withContext RemoteEditResult(0)

            writeCoordinator.withExclusiveRestore {
                requireAccount(uid)
                database.withTransaction {
                    requireAccount(uid)
                    for (remote in batch) {
                        val localEvent = eventDao.findByJournalId(remote.journalId)
                        val decision = RemoteEditPolicy.classify(
                            localJournalExists = journalDao.getJournalById(remote.journalId) != null,
                            localState = localEvent?.state,
                            localChangeType = localEvent?.changeType,
                            localVersion = localEvent?.changeVersion,
                            remoteRevision = remote.revision
                        )
                        check(decision == RemoteEditDecision.APPLY &&
                                isSafeRemoteEdit(remote, plan.catalog)) {
                            "Yerel kayıtlar değişti. Uzak düzenleme uygulanmadan işlem durduruldu."
                        }
                    }
                    val catalog = plan.catalog
                    for (remote in batch) {
                        val journal = requireNotNull(remote.journal)
                        catalog.seasons.firstOrNull { it.id == journal.seasonId }?.let { season ->
                            if (seasonDao.getSeasonById(season.id) == null) seasonDao.insertSeason(season)
                        }
                        catalog.episodes.firstOrNull { it.id == journal.episodeId }?.let { episode ->
                            if (episodeDao.getEpisodeById(episode.id) == null) episodeDao.insertEpisode(episode)
                        }
                        for (mention in remote.mentions) {
                            if (personDao.getPersonById(mention.personId) != null) continue
                            val person = catalog.people.first { it.id == mention.personId }
                            val sameName = personDao.getPersonByName(person.name)
                            check(sameName == null || sameName.id == person.id) {
                                "Farklı kimlikli kişiler aynı ada sahip. Tüm işlem geri alındı."
                            }
                            personDao.insertPerson(person)
                        }
                        RemoteJournalEditWriter.apply(
                            journalDao = journalDao,
                            mentionDao = mentionDao,
                            specialDao = specialDao,
                            eventDao = eventDao,
                            uid = uid,
                            journal = journal,
                            mentions = remote.mentions,
                            specialMemories = catalog.specialMemories.filter { it.journalId == journal.id },
                            remoteRevision = remote.revision,
                            remoteChangedAt = remote.changeRecordedAt
                        )
                    }
                    requireAccount(uid)
                }
                RemoteEditResult(batch.size)
            }
        }
    }

    private suspend fun isSafeNewRecord(
        uid: String, remote: MergeRemoteEvent, catalog: SyncDependencyCatalog
    ): Boolean {
        val journal = remote.journal ?: return false
        if (remote.operation != JournalSyncChangePolicy.UPSERT ||
            remote.changeRecordedAt > catalog.capturedAtMillis ||
            journal.id !in catalog.journalIds) return false
        if (journalDao.getJournalById(journal.id) != null ||
            eventDao.findByJournalId(journal.id) != null ||
            pendingMoodDao.find(journal.id) != null) return false
        // Başka kayıttan kalan ruh hali varsa asla üzerine yazmayız.
        if (moodRepository.getMood(journal.id) != null) return false
        val mood = catalog.moods.firstOrNull { it.journalId == journal.id }
        if (mood != null && (mood.updatedAt <= 0 ||
                    mood.updatedAt > catalog.capturedAtMillis ||
                    JournalMood.entries.none { it.name == mood.moodName })) return false

        // Olayın kişi bağlantıları katalogla TAM eşleşmelidir.
        val catalogMentions = catalog.mentions.filter { it.journalId == journal.id }
        if (catalogMentions.toSet() != remote.mentions.toSet()) return false
        val season = catalog.seasons.firstOrNull { it.id == journal.seasonId }
        if (journal.seasonId != null && season == null) return false
        val episode = catalog.episodes.firstOrNull { it.id == journal.episodeId }
        if (journal.episodeId != null && (episode == null || episode.seasonId != journal.seasonId)) return false
        if (season != null) {
            val existing = seasonDao.getSeasonById(season.id)
            // Yerel kapak fotoğrafı varsa farklı cihazın boş resim yolu ile ezmeyiz.
            if (existing != null && existing.copy(coverImagePath = null) != season) return false
        }
        if (episode != null && episodeDao.getEpisodeById(episode.id)?.let { it != episode } == true) return false
        for (mention in remote.mentions) {
            val person = catalog.people.firstOrNull { it.id == mention.personId } ?: return false
            val existing = personDao.getPersonById(person.id)
            if (existing != null && existing.copy(profileImagePath = null, coverImagePath = null) != person) return false
            // Farklı kimlikte fakat aynı isimde kişi oluşturarak yanlış etiketlemeyiz.
            val sameName = personDao.getPersonByName(person.name)
            if (sameName != null && sameName.id != person.id) return false
        }
        val special = catalog.specialMemories.filter { it.journalId == journal.id }
        if (special.any { memory -> remote.mentions.none { it.personId == memory.personId } }) return false
        return true
    }

    private suspend fun prepare(uid: String, password: CharArray): MergePlan {
        require(password.size in 12..128) { "Aktarım şifresi 12–128 karakter olmalı." }
        requireAccount(uid)
        val (catalog, catalogToken) = readCatalog(uid, password)
        val remoteSet = readEvents(uid, password)
        val latest = remoteSet.latest
        val eventsToken = remoteSet.token
        val eligible = mutableListOf<MergeRemoteEvent>()
        val updates = mutableListOf<MergeRemoteEvent>()
        var editConflicts = 0
        var existing = 0
        var deletes = 0
        var stale = 0
        var missing = 0
        var withMood = 0
        var deleteConflicts = 0
        for (remote in latest) {
            requireAccount(uid)
            if (remote.operation == JournalSyncChangePolicy.DELETE) {
                deletes++
                if (journalDao.getJournalById(remote.journalId) != null ||
                    eventDao.findByJournalId(remote.journalId)?.state ==
                    JournalSyncChangePolicy.PENDING) deleteConflicts++
                continue // Faz 2F.8'e kadar otomatik DELETE yok.
            }
            if (remote.changeRecordedAt > catalog.capturedAtMillis) { stale++; continue }
            val localJournal = journalDao.getJournalById(remote.journalId)
            val localEvent = eventDao.findByJournalId(remote.journalId)
            if (localJournal != null || localEvent != null) {
                // Faz C: cihazda zaten olan anının buluttaki düzenlemesi.
                when (RemoteEditPolicy.classify(
                    localJournalExists = localJournal != null,
                    localState = localEvent?.state,
                    localChangeType = localEvent?.changeType,
                    localVersion = localEvent?.changeVersion,
                    remoteRevision = remote.revision
                )) {
                    RemoteEditDecision.APPLY ->
                        if (isSafeRemoteEdit(remote, catalog)) updates += remote else missing++
                    RemoteEditDecision.CONFLICT ->
                        if (localJournal != null && localEvent != null &&
                            remote.revision >= localEvent.changeVersion &&
                            localJournal != remote.journal) editConflicts++ else existing++
                    else -> existing++
                }
                continue
            }
            if (!isSafeNewRecord(uid, remote, catalog)) { missing++; continue }
            eligible += remote
            if (catalog.moods.any { it.journalId == remote.journalId }) withMood++
        }
        requireAccount(uid)
        val fingerprint = sha256Hex("$uid|$catalogToken|$eventsToken".toByteArray(Charsets.UTF_8))
        return MergePlan(
            SafeMergePreview(fingerprint, latest.size, eligible.size, existing,
                deletes, stale, missing, withMood,
                pendingMoodDao.pendingCount(uid), deleteConflicts,
                MAX_IMPORT_PER_CONFIRMATION,
                remoteEdits = updates.size,
                editConflicts = editConflicts,
                inconsistentHeads = remoteSet.inconsistentJournals),
            catalog, eligible, updates
        )
    }

    private fun sha256Hex(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(value)
            .joinToString("") { "%02x".format(it.toInt() and 255) }

    private companion object {
        const val MAX_EVENTS = 2_000
        const val MAX_IMPORT_PER_CONFIRMATION = 5
    }
}
