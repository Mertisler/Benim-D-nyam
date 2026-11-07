package com.loc.benimdunyam.feature.people.network.model

import com.loc.benimdunyam.domain.model.JournalEntry

data class NetworkGraphNode(
    val id: String,
    val name: String,
    val profileImagePath: String?,
    val memoryCount: Int,
    val connectionCount: Int,
    val x: Float = 0f,
    val y: Float = 0f
)

data class NetworkGraphEdge(
    val firstPersonId: String,
    val secondPersonId: String,
    val sharedJournalIds: List<String>
) {
    val sharedMemoryCount: Int get() = sharedJournalIds.size

    fun otherPersonId(personId: String): String? = when (personId) {
        firstPersonId -> secondPersonId
        secondPersonId -> firstPersonId
        else -> null
    }
}

data class PeopleNetworkGraph(
    val nodes: List<NetworkGraphNode> = emptyList(),
    val edges: List<NetworkGraphEdge> = emptyList(),
    val journalsById: Map<String, JournalEntry> = emptyMap(),
    val totalPeople: Int = 0,
    val eligiblePeople: Int = 0,
    val hiddenByLimit: Int = 0,
    val totalMentionsInScope: Int = 0
) {
    fun node(personId: String): NetworkGraphNode? =
        nodes.firstOrNull { it.id == personId }

    fun edgesFor(personId: String): List<NetworkGraphEdge> =
        edges.filter { it.firstPersonId == personId || it.secondPersonId == personId }
            .sortedWith(compareByDescending<NetworkGraphEdge> { it.sharedMemoryCount }
                .thenBy { it.otherPersonId(personId).orEmpty() })
}
