package com.github.garynasser.correction_notebook.di

import android.content.Context
import com.github.garynasser.correction_notebook.data.local.StudyPreferencesManager
import com.github.garynasser.correction_notebook.data.repository.ArticleRepository
import com.github.garynasser.correction_notebook.data.repository.StudySessionRepository
import com.github.garynasser.correction_notebook.data.repository.TodoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HomeModule {

    @Provides
    @Singleton
    fun provideStudyPreferencesManager(
        @ApplicationContext context: Context
    ): StudyPreferencesManager {
        return StudyPreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideTodoRepository(
        @ApplicationContext context: Context
    ): TodoRepository {
        return TodoRepository(context)
    }

    @Provides
    @Singleton
    fun provideStudySessionRepository(
        @ApplicationContext context: Context
    ): StudySessionRepository {
        return StudySessionRepository(context)
    }

    @Provides
    @Singleton
    fun provideArticleRepository(): ArticleRepository {
        return ArticleRepository()
    }
}
