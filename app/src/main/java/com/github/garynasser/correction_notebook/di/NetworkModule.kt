package com.github.garynasser.correction_notebook.di

import android.content.Context
import android.util.Log
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.remote.api.AIApiService
import com.github.garynasser.correction_notebook.data.remote.api.ArticleApiService
import com.github.garynasser.correction_notebook.data.remote.api.AuthApiService
import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import com.github.garynasser.correction_notebook.data.remote.api.VideoApiService
import com.github.garynasser.correction_notebook.data.remote.network.AuthInterceptor
import com.github.garynasser.correction_notebook.data.remote.network.TokenAuthenticator
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.utils.BitShareNetworkDetector
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BitShareRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val BASE_URL = "http://bit-study.sadatlab.asia/"
    private const val BIT_SHARE_BASE_URL = "https://app.bitshare.com.cn/"

    // 辅助方法：创建一个日志拦截器
    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            Log.d("OkHttp", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager {
        return TokenManager(context)
    }

    @Provides
    @Singleton
    fun provideBitShareNetworkDetector(@ApplicationContext context: Context): BitShareNetworkDetector {
        return BitShareNetworkDetector(context)
    }

    @Provides
    @Singleton
    @BasicRetrofit
    fun provideBasicOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(createLoggingInterceptor()) // 添加日志
            .build()
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
    fun provideVideoApiService(): VideoApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://cbiz.yanhekt.cn/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(VideoApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideArticleApiService(@BasicRetrofit okHttpClient: OkHttpClient): ArticleApiService {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ArticleApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideBitShareApiService(
        @BasicRetrofit okHttpClient: OkHttpClient,
        networkDetector: BitShareNetworkDetector
    ): BitShareApiService {
        // 根据网络环境自动选择 URL
        val baseUrl = networkDetector.getBitShareBaseUrl()
        Log.d("NetworkModule", "BITShare using URL: $baseUrl")

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BitShareApiService::class.java)
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
            .addInterceptor(createLoggingInterceptor())
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

    @Provides
    @Singleton
    fun provideAISettingsManager(@ApplicationContext context: Context): AISettingsManager {
        return AISettingsManager(context)
    }

    @Provides
    @Singleton
    fun provideAIApiService(@BasicRetrofit okHttpClient: OkHttpClient): AIApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/v1/") // Base URL, actual URL is passed dynamically
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AIApiService::class.java)
    }
}
