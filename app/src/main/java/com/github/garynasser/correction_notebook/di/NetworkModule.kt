package com.github.garynasser.correction_notebook.di

import android.content.Context
import android.util.Log
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.remote.api.AuthApiService
import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import com.github.garynasser.correction_notebook.data.remote.api.VideoApiService
import com.github.garynasser.correction_notebook.data.remote.network.AuthInterceptor
import com.github.garynasser.correction_notebook.data.remote.network.TokenAuthenticator
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.utils.BitShareNetworkDetector
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
    private const val BASE_URL = "http://10.0.2.2:5678/"

    // BITShare 服务器地址常量
    private const val BITSHARE_INTRANET_URL = "http://10.170.35.57:8890/"
    private const val BITSHARE_INTERNET_URL = "https://app.bitshare.com.cn/"

    // 辅助方法：创建一个日志拦截器
    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            // 使用特定的 TAG 方便在 Logcat 中过滤
            Log.d("OkHttp", message)
        }.apply {
            // Level.BODY 会打印请求和响应的所有细节（包括 URL, Method, Body, Header）
            // 如果觉得内容太多，可以改为 Level.BASIC 或 Level.HEADERS
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

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
//            .authenticator(TokenAuthenticator(
//                tokenManager, authApiService, authStateManager
//            ))
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
