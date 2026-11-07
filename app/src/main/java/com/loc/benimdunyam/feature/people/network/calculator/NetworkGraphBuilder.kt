package com.loc.benimdunyam.feature.people.network.calculator

import com.loc.benimdunyam.domain.model.PeopleNetworkSnapshot
import com.loc.benimdunyam.feature.people.network.model.NetworkGraphEdge
import com.loc.benimdunyam.feature.people.network.model.NetworkGraphNode
import com.loc.benimdunyam.feature.people.network.model.PeopleNetworkGraph

/**
 * Kişi çiftlerini bir günlükte bir kere sayar. Yeni tablo veya N+1 sorgusu yoktur.
 * Sezon eşleştirmesinde günlüğün kaydedilmiş seasonId alanı esas alınır.
 */
object NetworkGraphBuilder {
    const val MAX_VISIBLE_PEOPLE = 48

    private data class PairKey(val first: String, val second: String)

    fun build(
        snapshot: PeopleNetworkSnapshot,
        seasonId: String? = null,
        onlyWithMemories: Boolean = true,
        maxVisiblePeople: Int = MAX_VISIBLE_PEOPLE,
        prioritizedNameQuery: String = ""
    ): PeopleNetworkGraph {
        require(maxVisiblePeople > 0)

        val people = snapshot.people.distinctBy { it.id }
        val peopleById = people.associateBy { it.id }
        val validJournals = snapshot.journals.asSequence()
            .filter { seasonId == null || it.seasonId == seasonId }
            .distinctBy { it.id }
            .associateBy { it.id }

        val peoplePerJournal = mutableMapOf<String, MutableSet<String>>()
        for (mention in snapshot.mentions) {
            if (mention.journalId in validJournals && mention.personId in peopleById) {
                peoplePerJournal.getOrPut(mention.journalId) { linkedSetOf() }
                    .add(mention.personId)
            }
        }

        // Önce yalnızca anı sayıları hesaplanır. Kalabalık günlüklerde bütün
        // kişi çiftlerini üretmek O(N²) maliyetine neden olabileceğinden,
        // bağlantıları sadece ekranda göstereceğimiz kişiler için çıkarıyoruz.
        val memoriesPerPerson = mutableMapOf<String, Int>()
        for ((_, participants) in peoplePerJournal) {
            for (id in participants) {
                memoriesPerPerson[id] = (memoriesPerPerson[id] ?: 0) + 1
            }
        }

        val eligible = people.filter { !onlyWithMemories || (memoriesPerPerson[it.id] ?: 0) > 0 }
        val ranked = eligible.sortedWith(
            compareByDescending<com.loc.benimdunyam.domain.model.NetworkPerson> {
                memoriesPerPerson[it.id] ?: 0
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy { it.id }
        )
        // Arama; ilk 48'e girmemiş kişileri de haritaya taşıyabilir.
        val searchMatches = if (prioritizedNameQuery.isBlank()) emptyList()
        else ranked.filter { it.name.contains(prioritizedNameQuery.trim(), ignoreCase = true) }
        val visible = (searchMatches + ranked).distinctBy { it.id }.take(maxVisiblePeople)
        val visibleIds = visible.map { it.id }.toSet()

        val sharedJournalsByPair = mutableMapOf<PairKey, MutableList<String>>()
        for ((journalId, participants) in peoplePerJournal) {
            val ids = participants.filter { it in visibleIds }.sorted()
            for (i in ids.indices) {
                for (j in (i + 1) until ids.size) {
                    val pair = PairKey(ids[i], ids[j])
                    sharedJournalsByPair.getOrPut(pair) { mutableListOf() }.add(journalId)
                }
            }
        }
        val degrees = mutableMapOf<String, Int>()
        for ((pair, ids) in sharedJournalsByPair) {
            if (ids.isEmpty()) continue
            degrees[pair.first] = (degrees[pair.first] ?: 0) + 1
            degrees[pair.second] = (degrees[pair.second] ?: 0) + 1
        }

        val nodes = visible.map { person ->
            NetworkGraphNode(
                id = person.id,
                name = person.name,
                profileImagePath = person.profileImagePath,
                memoryCount = memoriesPerPerson[person.id] ?: 0,
                connectionCount = degrees[person.id] ?: 0
            )
        }
        val edges = sharedJournalsByPair.entries.asSequence()
            .filter { (pair, _) -> pair.first in visibleIds && pair.second in visibleIds }
            .map { (pair, ids) ->
                NetworkGraphEdge(pair.first, pair.second, ids.distinct().sorted())
            }
            .sortedWith(compareByDescending<NetworkGraphEdge> { it.sharedMemoryCount }
                .thenBy { it.firstPersonId }
                .thenBy { it.secondPersonId })
            .toList()

        return PeopleNetworkGraph(
            nodes = nodes,
            edges = edges,
            journalsById = validJournals,
            totalPeople = people.size,
            eligiblePeople = eligible.size,
            hiddenByLimit = eligible.size - nodes.size,
            totalMentionsInScope = peoplePerJournal.values.sumOf { it.size }
        )
    }
}
