package com.loc.benimdunyam.domain.model

/** Veritabanındaki kişi ve ilişki verilerinin yalın karşılıkları. */
data class NetworkPerson(
    val id: String,
    val name: String,
    val profileImagePath: String?
)

data class NetworkMention(
    val journalId: String,
    val personId: String
)

/** Tek Flow zincirinde elde edilen veri anlık görüntüsü. */
data class PeopleNetworkSnapshot(
    val people: List<NetworkPerson>,
    val mentions: List<NetworkMention>,
    val journals: List<JournalEntry>,
    val seasons: List<LifeSeason>
)
