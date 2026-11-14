package com.loc.benimdunyam.di

import com.loc.benimdunyam.data.remote.firebase.archive.FirebaseCloudArchiveRepository
import com.loc.benimdunyam.domain.repository.CloudArchiveRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FirebaseArchiveModule {
    @Binds
    @Singleton
    abstract fun bindCloudArchiveRepository(impl: FirebaseCloudArchiveRepository): CloudArchiveRepository
}
