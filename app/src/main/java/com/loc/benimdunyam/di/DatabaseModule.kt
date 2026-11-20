package com.loc.benimdunyam.di

import android.content.Context
import androidx.room.Room
import com.loc.benimdunyam.data.local.AppDatabase
import com.loc.benimdunyam.data.local.isolation.ActiveWorkspaceSession
import com.loc.benimdunyam.data.local.dao.JournalSyncEventDao
import com.loc.benimdunyam.data.local.dao.PendingMoodMergeDao
import com.loc.benimdunyam.data.local.dao.JournalDao
import com.loc.benimdunyam.data.local.dao.JournalPersonMentionDao
import com.loc.benimdunyam.data.local.dao.LifeEpisodeDao
import com.loc.benimdunyam.data.local.dao.LifeSeasonDao
import com.loc.benimdunyam.data.local.dao.PersonDao
import com.loc.benimdunyam.data.local.dao.TrashedJournalDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        session: ActiveWorkspaceSession
    ): AppDatabase {

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            session.databaseNames()[0]
        )
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
    }

    /** Ayrı ruh hali Room'u için atomik kurtarma işareti DAO'su. */
    @Provides
    fun providePendingMoodMergeDao(database: AppDatabase): PendingMoodMergeDao =
        database.pendingMoodMergeDao()

    /** Yerel günlük işlemlerinin atomik kuyruk kaydı için kullanılan DAO. */
    @Provides
    fun provideJournalSyncEventDao(database: AppDatabase): JournalSyncEventDao =
        database.journalSyncEventDao()

    @Provides
    @Singleton
    fun provideJournalDao(database: AppDatabase): JournalDao {
        return database.journalDao()
    }

    @Provides
    @Singleton
    fun providePersonDao(database: AppDatabase): PersonDao {
        return database.personDao()
    }

    @Provides
    @Singleton
    fun provideJournalPersonMentionDao(database: AppDatabase): JournalPersonMentionDao {
        return database.journalPersonMentionDao()
    }

    @Provides
    @Singleton
    fun provideLifeSeasonDao(database: AppDatabase): LifeSeasonDao {
        return database.lifeSeasonDao()
    }

    /** Faz B: "Son silinenler". */
    @Provides
    fun provideTrashedJournalDao(database: AppDatabase): TrashedJournalDao =
        database.trashedJournalDao()

    @Provides
    @Singleton
    fun provideLifeEpisodeDao(database: AppDatabase): LifeEpisodeDao {
        return database.lifeEpisodeDao()
    }

}