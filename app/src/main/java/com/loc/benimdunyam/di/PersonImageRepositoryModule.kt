package com.loc.benimdunyam.di

import com.loc.benimdunyam.data.repository.PersonImageRepositoryImpl
import com.loc.benimdunyam.domain.repository.PersonImageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PersonImageRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPersonImageRepository(
        implementation: PersonImageRepositoryImpl
    ): PersonImageRepository
}
