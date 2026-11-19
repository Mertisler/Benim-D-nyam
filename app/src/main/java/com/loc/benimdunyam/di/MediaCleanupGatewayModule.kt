package com.loc.benimdunyam.di

import com.loc.benimdunyam.data.sync.media.FirebaseCallableMediaCleanupGateway
import com.loc.benimdunyam.data.sync.media.VerifiedMediaCleanupGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Faz 2G.4B — Gerçek silme ancak Cloud Functions doğrulama ve admin kanıtıyla mümkündür.
 * Sunucudaki BD_CLEANUP_ENABLED=false ise bu gateway tüm adayları kilitli tutar.
 * Veriler veya kullanıcı başka hesaba geçtiğinde çağrılar engellenir.
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaCleanupGatewayModule {
    @Provides
    @Singleton
    fun provideVerifiedMediaCleanupGateway(): VerifiedMediaCleanupGateway =
        FirebaseCallableMediaCleanupGateway(region = "europe-west1")
}
