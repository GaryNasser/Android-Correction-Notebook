package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.home.DeadlineItem
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.data.repository.DeadlineRepository
import com.github.garynasser.correction_notebook.data.repository.YanheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class DeadlineUiState(
    val items: List<DeadlineItem> = emptyList(),
    val updatedAt: Long? = null,
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DeadlineViewModel @Inject constructor(
    private val repository: DeadlineRepository,
    private val yanhe: YanheRepository,
    private val auth: AuthStateManager
) : ViewModel() {
    private val state = MutableStateFlow(DeadlineUiState())
    val uiState = state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            yanhe.sessionRevision.collectLatest {
                refreshJob?.cancel()
                state.value = DeadlineUiState(isSignedIn = yanhe.getStudentCredential() != null)
                try {
                    repository.cached()?.let { saved ->
                        if (!state.value.isLoading && state.value.updatedAt == null) {
                            state.value = state.value.copy(items = saved.items, updatedAt = saved.updatedAt)
                        }
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { state.value = state.value.copy(error = "上次 DDL 数据读取失败，请重新同步") }
            }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        if (yanhe.getStudentCredential() == null) {
            viewModelScope.launch { auth.onCasLoginRequired() }
            return
        }
        refreshJob = viewModelScope.launch {
            val version = yanhe.sessionVersion
            state.value = state.value.copy(isLoading = true, error = null)
            try {
                val snapshot = repository.refresh()
                state.value = state.value.copy(items = snapshot.items, updatedAt = snapshot.updatedAt)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (version == yanhe.sessionVersion) state.value = state.value.copy(error = error.message ?: "DDL 同步失败，已保留上次数据")
            }
            finally { if (version == yanhe.sessionVersion) state.value = state.value.copy(isLoading = false) }
        }
    }
}
