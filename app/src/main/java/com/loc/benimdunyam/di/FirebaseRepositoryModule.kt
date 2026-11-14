package com.loc.benimdunyam.di

import com.loc.benimdunyam.data.remote.firebase.FirebaseCloudAuthRepository
import com.loc.benimdunyam.data.remote.firebase.FirebaseCloudConnectionRepository
import com.loc.benimdunyam.domain.repository.CloudAuthRepository
import com.loc.benimdunyam.domain.repository.CloudConnectionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindCloudAuthRepository(impl: FirebaseCloudAuthRepository): CloudAuthRepository

    @Binds
    @Singleton
    abstract fun bindConnectionRepository(impl: FirebaseCloudConnectionRepository): CloudConnectionRepository
}
