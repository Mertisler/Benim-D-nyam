package com.loc.benimdunyam.domain.repository

import com.loc.benimdunyam.domain.model.NetworkMention
import com.loc.benimdunyam.domain.model.NetworkPerson
import kotlinx.coroutines.flow.Flow

interface PeopleNetworkRepository {
    fun observePeople(): Flow<List<NetworkPerson>>
    fun observeMentions(): Flow<List<NetworkMention>>
}
