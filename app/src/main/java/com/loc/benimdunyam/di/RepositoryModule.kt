package com.loc.benimdunyam.di

import com.loc.benimdunyam.data.repository.JournalRepositoryImpl
import com.loc.benimdunyam.data.repository.PersonRepositoryImpl
import com.loc.benimdunyam.domain.repository.JournalRepository
import com.loc.benimdunyam.domain.repository.PersonRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindJournalRepository(
        journalRepositoryImpl: JournalRepositoryImpl
    ): JournalRepository

    @Binds
    @Singleton
    abstract fun bindPersonRepository(
        personRepositoryImpl: PersonRepositoryImpl
    ): PersonRepository
}