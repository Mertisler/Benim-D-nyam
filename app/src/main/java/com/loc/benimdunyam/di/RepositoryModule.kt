package com.loc.benimdunyam.di

import com.loc.benimdunyam.data.repository.BackupRepositoryImpl
import com.loc.benimdunyam.data.repository.JournalRepositoryImpl
import com.loc.benimdunyam.data.repository.LifeEpisodeRepositoryImpl
import com.loc.benimdunyam.data.repository.LifeSeasonRepositoryImpl
import com.loc.benimdunyam.data.repository.PersonRepositoryImpl
import com.loc.benimdunyam.domain.repository.BackupRepository
import com.loc.benimdunyam.domain.repository.JournalRepository
import com.loc.benimdunyam.domain.repository.LifeEpisodeRepository
import com.loc.benimdunyam.domain.repository.LifeSeasonRepository
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

    @Binds
    @Singleton
    abstract fun bindBackupRepository(
        backupRepositoryImpl: BackupRepositoryImpl
    ): BackupRepository

    @Binds
    @Singleton
    abstract fun bindLifeSeasonRepository(
        lifeSeasonRepositoryImpl: LifeSeasonRepositoryImpl
    ): LifeSeasonRepository

    @Binds
    @Singleton
    abstract fun bindLifeEpisodeRepository(
        lifeEpisodeRepositoryImpl: LifeEpisodeRepositoryImpl
    ): LifeEpisodeRepository


}