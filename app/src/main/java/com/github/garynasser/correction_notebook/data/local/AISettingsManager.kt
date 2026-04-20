package com.github.garynasser.correction_notebook.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore("ai_settings")

@Singleton
class AISettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val AI_ENABLED_KEY = booleanPreferencesKey("ai_enabled")
        private val API_BASE_URL_KEY = stringPreferencesKey("api_base_url")
        private val API_KEY_KEY = stringPreferencesKey("api_key")
        private val AI_MODEL_KEY = stringPreferencesKey("ai_model")

        const val DEFAULT_API_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }

    val aiEnabled: Flow<Boolean> = context.aiDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[AI_ENABLED_KEY] ?: false }

    val apiBaseUrl: Flow<String> = context.aiDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[API_BASE_URL_KEY] ?: DEFAULT_API_URL }

    val apiKey: Flow<String> = context.aiDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[API_KEY_KEY] ?: "" }

    val aiModel: Flow<String> = context.aiDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[AI_MODEL_KEY] ?: DEFAULT_MODEL }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.aiDataStore.edit { prefs ->
            prefs[AI_ENABLED_KEY] = enabled
        }
    }

    suspend fun setApiBaseUrl(url: String) {
        context.aiDataStore.edit { prefs ->
            prefs[API_BASE_URL_KEY] = url
        }
    }

    suspend fun setApiKey(key: String) {
        context.aiDataStore.edit { prefs ->
            prefs[API_KEY_KEY] = key
        }
    }

    suspend fun setAiModel(model: String) {
        context.aiDataStore.edit { prefs ->
            prefs[AI_MODEL_KEY] = model
        }
    }
}
