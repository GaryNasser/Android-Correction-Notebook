package com.github.garynasser.correction_notebook.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.garynasser.correction_notebook.data.local.ai.AiCredentialCipher
import com.github.garynasser.correction_notebook.data.local.ai.AiDatabase
import com.github.garynasser.correction_notebook.data.local.ai.AiProviderDao
import com.github.garynasser.correction_notebook.data.local.ai.ChatMessageDao
import com.github.garynasser.correction_notebook.data.local.ai.ChatSessionDao
import com.github.garynasser.correction_notebook.data.local.ai.UserMemoryDao
import com.github.garynasser.correction_notebook.data.repository.ChatSessionRepository
import com.github.garynasser.correction_notebook.data.repository.MemoryRepository
import com.github.garynasser.correction_notebook.data.repository.ProviderRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiPersistenceModule {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `ai_provider` ADD COLUMN `temperature` REAL")
            db.execSQL("ALTER TABLE `ai_provider` ADD COLUMN `maxTokens` INTEGER")
            db.execSQL("ALTER TABLE `ai_provider` ADD COLUMN `contextMessageLimit` INTEGER NOT NULL DEFAULT 12")
        }
    }

    @Provides
    @Singleton
    fun provideAiDatabase(
        @ApplicationContext context: Context
    ): AiDatabase {
        return Room.databaseBuilder(
            context,
            AiDatabase::class.java,
            "ai.db"
        )
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    @Singleton
    fun provideAiProviderDao(database: AiDatabase): AiProviderDao = database.aiProviderDao()

    @Provides
    @Singleton
    fun provideChatSessionDao(database: AiDatabase): ChatSessionDao = database.chatSessionDao()

    @Provides
    @Singleton
    fun provideChatMessageDao(database: AiDatabase): ChatMessageDao = database.chatMessageDao()

    @Provides
    @Singleton
    fun provideUserMemoryDao(database: AiDatabase): UserMemoryDao = database.userMemoryDao()

    @Provides
    @Singleton
    fun provideProviderRepository(
        dao: AiProviderDao,
        credentialCipher: AiCredentialCipher
    ): ProviderRepository = ProviderRepository(dao, credentialCipher)

    @Provides
    @Singleton
    fun provideChatSessionRepository(
        sessionDao: ChatSessionDao,
        messageDao: ChatMessageDao
    ): ChatSessionRepository = ChatSessionRepository(sessionDao, messageDao)

    @Provides
    @Singleton
    fun provideMemoryRepository(
        userMemoryDao: UserMemoryDao
    ): MemoryRepository = MemoryRepository(userMemoryDao)
}
