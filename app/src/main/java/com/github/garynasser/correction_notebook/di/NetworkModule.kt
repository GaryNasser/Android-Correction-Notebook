package com.github.garynasser.correction_notebook.di

import android.content.Context
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.remote.api.AuthApiService
import com.github.garynasser.correction_notebook.data.remote.network.AuthInterceptor
import com.github.garynasser.correction_notebook.data.remote.network.TokenAuthenticator
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BasicRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val BASE_URL = "https://auth-api.free.beeceptor.com";

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager {
        return TokenManager(context)
    }

    @Provides
    @Singleton
    @BasicRetrofit
    fun provideBasicOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    fun provideAuthApiService(@BasicRetrofit okHttpClient: OkHttpClient): AuthApiService {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    @AuthRetrofit
    fun provideBusinessOkHttpClient(
        tokenManager: TokenManager,
        authApiService: AuthApiService,
        authStateManager: AuthStateManager
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .authenticator(TokenAuthenticator(
                tokenManager, authApiService, authStateManager
            ))
            .build()
    }

    @Provides
    @Singleton
    @AuthRetrofit
    fun provideRetrofit(@AuthRetrofit okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}