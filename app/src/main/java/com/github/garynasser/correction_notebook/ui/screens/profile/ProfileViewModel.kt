package com.github.garynasser.correction_notebook.ui.screens.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.repository.AuthRepository
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore("profile_prefs")

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val authStateManager: AuthStateManager
) : ViewModel() {

    companion object {
        private val API_KEY_KEY = stringPreferencesKey("api_key")
    }

    // API Key state
    val apiKey: StateFlow<String> = context.profileDataStore.data
        .map { prefs -> prefs[API_KEY_KEY] ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // Auth state
    val authState: StateFlow<AuthState> = authStateManager.authState

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setApiKey(key: String) {
        viewModelScope.launch {
            context.profileDataStore.edit { prefs ->
                prefs[API_KEY_KEY] = key
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.logout()
                authStateManager.updateState(AuthState.Unauthenticated)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
