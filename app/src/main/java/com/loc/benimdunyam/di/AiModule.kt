package com.loc.benimdunyam.di

import com.loc.benimdunyam.data.ai.AiConsentSource
import com.loc.benimdunyam.data.ai.AiConsentStore
import com.loc.benimdunyam.data.ai.AiGateway
import com.loc.benimdunyam.data.ai.AiResultStore
import com.loc.benimdunyam.data.ai.FirebaseAiGateway
import com.loc.benimdunyam.data.ai.RoomAiResultStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Yapay zekâ katmanı: sunucu kapısı, izin ve önbellek. Bölge functions/ai/index.js ile aynı. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    abstract fun bindResultStore(impl: RoomAiResultStore): AiResultStore

    @Binds
    abstract fun bindConsentSource(impl: AiConsentStore): AiConsentSource

    @Binds
    abstract fun bindInsightStore(impl: com.loc.benimdunyam.data.ai.insight.RoomMemoryInsightStore): com.loc.benimdunyam.data.ai.insight.MemoryInsightStore

    @Binds
    abstract fun bindAnalysisScheduler(impl: com.loc.benimdunyam.data.ai.insight.WorkManagerMemoryAnalysisScheduler): com.loc.benimdunyam.data.ai.insight.MemoryAnalysisScheduler

    @Binds
    abstract fun bindAnalysisSources(impl: com.loc.benimdunyam.data.ai.insight.DefaultMemoryAnalysisSources): com.loc.benimdunyam.data.ai.insight.MemoryAnalysisSources

    @Binds
    abstract fun bindAskHistory(impl: com.loc.benimdunyam.data.ai.history.RoomAskHistoryStore): com.loc.benimdunyam.data.ai.history.AskHistoryStore

    @Binds
    abstract fun bindAskSources(impl: com.loc.benimdunyam.feature.ask.DefaultAskSources): com.loc.benimdunyam.feature.ask.AskSources

    companion object {
        @Provides
        @Singleton
        fun provideAiGateway(): AiGateway = FirebaseAiGateway(region = "europe-west1")
    }
}
