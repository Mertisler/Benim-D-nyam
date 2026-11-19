package com.loc.benimdunyam.domain.sync

/**
 * Faz 2F.5 için saf Kotlin güvenlik ve veri kapsamı kararları.
 * Bu sınıfta Firebase veya Room bulunmaz; testler hızlı ve tekrarlanabilirdir.
 */
data class SyncRelationIndex(
    val journalIds: Set<String>,
    val seasonIds: Set<String>,
    val episodesToSeason: Map<String, String>,
    val personIds: Set<String>,
    val moodJournalIds: Set<String>
)

/** Şifreli olaydan okunan, günlük metnini içermeyen ilişki özeti. */
data class SyncRemoteFact(
    val journalId: String,
    val revision: Long,
    val operation: String,
    val seasonId: String?,
    val episodeId: String?,
    val personIds: Set<String>,
    val recordedAtMillis: Long
)

data class SyncCoverageResult(
    val missingRelations: Int,
    val oldCatalog: Boolean,
    val canBeAutoMerged: Boolean = false
)

object FullSyncCoveragePolicy {
    /** Aynı olay sürümü iki farklı işlemle gelirse otomatik seçim yapmak tehlikelidir. */
    fun selectLatest(facts: List<SyncRemoteFact>): List<SyncRemoteFact> {
        val latest = linkedMapOf<String, SyncRemoteFact>()
        for (fact in facts) {
            require(fact.journalId.isNotBlank() && fact.revision > 0) { "Geçersiz bulut olayı." }
            require(fact.operation == "UPSERT" || fact.operation == "DELETE") { "Bilinmeyen olay türü." }
            val existing = latest[fact.journalId]
            if (existing == null || fact.revision > existing.revision) {
                latest[fact.journalId] = fact
            } else if (fact.revision == existing.revision && fact != existing) {
                error("Aynı anı sürümünde çakışan bulut olayları var.")
            }
        }
        return latest.values.toList()
    }

    /** Katalog bütünlüğünü doğrular. Eksik kişileri sessizce atlayıp içe aktarma yapmayız. */
    fun inspect(
        fact: SyncRemoteFact,
        index: SyncRelationIndex,
        catalogTimeMillis: Long
    ): SyncCoverageResult {
        if (fact.operation == "DELETE") return SyncCoverageResult(0, fact.recordedAtMillis > catalogTimeMillis)
        var missing = 0
        if (fact.journalId !in index.journalIds) missing++
        if (fact.seasonId != null && fact.seasonId !in index.seasonIds) missing++
        if (fact.episodeId != null && index.episodesToSeason[fact.episodeId] != fact.seasonId) missing++
        missing += fact.personIds.count { it !in index.personIds }
        return SyncCoverageResult(missing, fact.recordedAtMillis > catalogTimeMillis)
    }

    /** Veritabanı anahtarlarında boş ve yinelenen kimliklere izin vermeyiz. */
    fun <T> uniqueIds(items: List<T>, id: (T) -> String): Set<String> {
        val ids = items.map(id)
        require(ids.none(String::isBlank)) { "Boş kayıt kimliği tespit edildi." }
        require(ids.toSet().size == ids.size) { "Yinelenen kayıt kimliği tespit edildi." }
        return ids.toSet()
    }
}
