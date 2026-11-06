package com.loc.benimdunyam.di

import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.dao.PersonSpecialMemoryDao
import com.loc.benimdunyam.data.repository.PersonSpecialMemoryRepositoryImpl
import com.loc.benimdunyam.domain.repository.PersonSpecialMemoryRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PersonSpecialMemoryModule {
    @Binds
    abstract fun bindRepository(
        impl: PersonSpecialMemoryRepositoryImpl
    ): PersonSpecialMemoryRepository

    companion object {
        @Provides
        fun provideDao(database: AppDatabase): PersonSpecialMemoryDao =
            database.personSpecialMemoryDao()
    }
}
