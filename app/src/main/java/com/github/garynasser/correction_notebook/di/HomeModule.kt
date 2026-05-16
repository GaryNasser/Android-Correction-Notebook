package com.github.garynasser.correction_notebook.di

import android.content.Context
import com.github.garynasser.correction_notebook.data.local.StudyPreferencesManager
import com.github.garynasser.correction_notebook.data.remote.api.ArticleApiService
import com.github.garynasser.correction_notebook.data.repository.ArticleRepository
import com.github.garynasser.correction_notebook.data.repository.CourseLearningRepository
import com.github.garynasser.correction_notebook.data.repository.IcsImportRepository
import com.github.garynasser.correction_notebook.data.repository.ScheduleRepository
import com.github.garynasser.correction_notebook.data.repository.StudySessionRepository
import com.github.garynasser.correction_notebook.data.repository.TodoHistoryRepository
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
    fun provideTodoHistoryRepository(
        @ApplicationContext context: Context
    ): TodoHistoryRepository {
        return TodoHistoryRepository(context)
    }

    @Provides
    @Singleton
    fun provideCourseLearningRepository(
        @ApplicationContext context: Context
    ): CourseLearningRepository {
        return CourseLearningRepository(context)
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
    fun provideScheduleRepository(
        @ApplicationContext context: Context
    ): ScheduleRepository {
        return ScheduleRepository(context)
    }

    @Provides
    @Singleton
    fun provideIcsImportRepository(
        @ApplicationContext context: Context,
        scheduleRepository: ScheduleRepository
    ): IcsImportRepository {
        return IcsImportRepository(context, scheduleRepository)
    }

    @Provides
    @Singleton
    fun provideArticleRepository(
        articleApiService: ArticleApiService
    ): ArticleRepository {
        return ArticleRepository(articleApiService)
    }
}
