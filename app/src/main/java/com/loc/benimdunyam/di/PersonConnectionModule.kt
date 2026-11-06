package com.loc.benimdunyam.di

import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.dao.PersonConnectionDao
import com.loc.benimdunyam.data.repository.PersonConnectionRepositoryImpl
import com.loc.benimdunyam.domain.repository.PersonConnectionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PersonConnectionModule {

    @Binds
    abstract fun bindPersonConnectionRepository(
        implementation: PersonConnectionRepositoryImpl
    ): PersonConnectionRepository

    companion object {
        @Provides
        fun providePersonConnectionDao(database: AppDatabase): PersonConnectionDao =
            database.personConnectionDao()
    }
}
