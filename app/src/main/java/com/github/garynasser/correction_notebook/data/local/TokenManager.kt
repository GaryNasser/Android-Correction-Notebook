package com.github.garynasser.correction_notebook.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("user_prefs")

@Singleton
class TokenManager@Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")

        private val YANHE_LOGIN_TOKEN_KEY = stringPreferencesKey("yanhe_login_key")
    }

    val accessToken: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[ACCESS_TOKEN_KEY]
        }

    val refreshToken: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[REFRESH_TOKEN_KEY]
        }

    val yanheLoginToken: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[YANHE_LOGIN_TOKEN_KEY]
        }

    suspend fun saveLoginTokens(access: String, refresh: String) {
        Log.d("AppLifecycle", "Refresh saved: $refresh")
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = access
            preferences[REFRESH_TOKEN_KEY] = refresh
        }
    }

    suspend fun saveYanheLoginTokens(token: String) {
        context.dataStore.edit { preferences ->
            preferences[YANHE_LOGIN_TOKEN_KEY] = token
        }
    }

    suspend fun updateAccessToken(access: String) {
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = access
        }
    }

    suspend fun removeLoginToken() {
        Log.d("AppLifecycle", "Token Cleared")
        context.dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN_KEY)
            preferences.remove(REFRESH_TOKEN_KEY)
        }
    }

    suspend fun removeYanheLoginToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(YANHE_LOGIN_TOKEN_KEY)
        }
    }

    suspend fun getAccessKey(): String? {
        return accessToken.first()
    }

    suspend fun getRefreshToken(): String? {
        return refreshToken.first()
    }

    suspend fun getYanheLoginToken(): String? {
        return yanheLoginToken.first()
    }
}