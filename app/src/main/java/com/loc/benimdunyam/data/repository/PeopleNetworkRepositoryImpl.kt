package com.loc.benimdunyam.data.repository

import com.loc.benimdunyam.data.local.dao.PeopleNetworkDao
import com.loc.benimdunyam.domain.model.NetworkMention
import com.loc.benimdunyam.domain.model.NetworkPerson
import com.loc.benimdunyam.domain.repository.PeopleNetworkRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PeopleNetworkRepositoryImpl @Inject constructor(
    private val dao: PeopleNetworkDao
) : PeopleNetworkRepository {

    override fun observePeople(): Flow<List<NetworkPerson>> =
        dao.observePeople().map { rows ->
            rows.map { NetworkPerson(it.id, it.name, it.profileImagePath) }
        }

    override fun observeMentions(): Flow<List<NetworkMention>> =
        dao.observeMentions().map { rows ->
            rows.map { NetworkMention(it.journalId, it.personId) }
        }
}
