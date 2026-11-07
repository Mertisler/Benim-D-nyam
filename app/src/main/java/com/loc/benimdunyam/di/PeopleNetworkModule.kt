package com.loc.benimdunyam.di

import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.dao.PeopleNetworkDao
import com.loc.benimdunyam.data.repository.PeopleNetworkRepositoryImpl
import com.loc.benimdunyam.domain.repository.PeopleNetworkRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PeopleNetworkModule {

    @Binds
    abstract fun bindPeopleNetworkRepository(
        implementation: PeopleNetworkRepositoryImpl
    ): PeopleNetworkRepository

    companion object {
        @Provides
        fun providePeopleNetworkDao(database: AppDatabase): PeopleNetworkDao =
            database.peopleNetworkDao()
    }
}
