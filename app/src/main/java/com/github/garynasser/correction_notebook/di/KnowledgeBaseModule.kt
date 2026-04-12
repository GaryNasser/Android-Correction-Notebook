package com.github.garynasser.correction_notebook.di

import android.content.Context
import androidx.room.Room
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDao
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDatabase
import com.github.garynasser.correction_notebook.data.repository.BitShareRepository
import com.github.garynasser.correction_notebook.data.repository.KnowledgeBaseRepository
import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object KnowledgeBaseModule {

    @Provides
    @Singleton
    fun provideKnowledgeBaseDatabase(
        @ApplicationContext context: Context
    ): KnowledgeBaseDatabase {
        return Room.databaseBuilder(
            context,
            KnowledgeBaseDatabase::class.java,
            "knowledge_base.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideKnowledgeBaseDao(
        database: KnowledgeBaseDatabase
    ): KnowledgeBaseDao = database.knowledgeBaseDao()

    @Provides
    @Singleton
    fun provideKnowledgeBaseRepository(
        dao: KnowledgeBaseDao,
        storage: com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
    ): KnowledgeBaseRepository {
        return KnowledgeBaseRepository(dao, storage)
    }

    @Provides
    @Singleton
    fun provideBitShareRepository(
        apiService: BitShareApiService
    ): BitShareRepository {
        return BitShareRepository(apiService)
    }
}
