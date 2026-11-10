package com.loc.benimdunyam.feature.people.detail.presentation

/** Saf Kotlin: Android, Room ya da Compose bağımlılığı yok. */
data class PersonMentionSnapshot(
    val personId: String,
    val name: String
)

data class JournalMentionSnapshot(
    val journalId: String,
    val mentionedPeople: List<PersonMentionSnapshot>
)

data class PersonCoMentionSummary(
    val personId: String,
    val name: String,
    val sharedMemoryCount: Int
)

/**
 * Birlikte görünen kişiler her günlük için en fazla bir kez sayılır.
 * Buradaki "ortak" sayısı, gerçek hayattaki ilişki yakınlığını ölçmez.
 *
 * Hesaplamada kaynağı, profilin zaten gözlemlediği kişiye ait günlüklerdir.
 * Böylece boş bir bağlantı ağı yüzünden yanlışlıkla "ortak kişi yok" denmez.
 */
object PersonCoMentionCalculator {
    fun calculate(
        currentPersonId: String,
        journalMentions: List<JournalMentionSnapshot>
    ): List<PersonCoMentionSummary> {
        val journalIdsByPerson = linkedMapOf<String, MutableSet<String>>()
        val namesByPerson = linkedMapOf<String, String>()

        journalMentions.forEach { journal ->
            if (journal.journalId.isBlank()) return@forEach
            journal.mentionedPeople
                .asSequence()
                .filter { it.personId.isNotBlank() && it.personId != currentPersonId }
                .distinctBy { it.personId }
                .forEach { person ->
                    journalIdsByPerson.getOrPut(person.personId) { linkedSetOf() }
                        .add(journal.journalId)
                    if (person.name.isNotBlank()) namesByPerson[person.personId] = person.name
                }
        }

        return journalIdsByPerson.map { (id, journalIds) ->
            PersonCoMentionSummary(
                personId = id,
                name = namesByPerson[id].orEmpty().ifBlank { "İsimsiz kişi" },
                sharedMemoryCount = journalIds.size
            )
        }.sortedWith(
            compareByDescending<PersonCoMentionSummary> { it.sharedMemoryCount }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.personId }
        )
    }
}
