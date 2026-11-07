package com.loc.benimdunyam.domain.usecase

import com.loc.benimdunyam.domain.model.PeopleNetworkSnapshot
import com.loc.benimdunyam.domain.repository.PeopleNetworkRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObservePeopleNetworkUseCase @Inject constructor(
    private val repository: PeopleNetworkRepository,
    private val observeJournalsUseCase: ObserveJournalsUseCase,
    private val observeLifeSeasonsUseCase: ObserveLifeSeasonsUseCase
) {
    operator fun invoke(): Flow<PeopleNetworkSnapshot> = combine(
        repository.observePeople(),
        repository.observeMentions(),
        observeJournalsUseCase(),
        observeLifeSeasonsUseCase()
    ) { people, mentions, journals, seasons ->
        PeopleNetworkSnapshot(
            people = people,
            mentions = mentions,
            journals = journals,
            seasons = seasons
        )
    }
}
