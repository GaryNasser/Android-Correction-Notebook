package com.github.garynasser.correction_notebook.ui.update

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.appupdate.AppVersionInfo
import com.github.garynasser.correction_notebook.data.repository.AppUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUpdateUiState(
    val currentVersionName: String = "",
    val currentVersionCode: Long = 0L,
    val isChecking: Boolean = false,
    val availableUpdate: AppVersionInfo? = null,
    val snackbarMessage: String? = null
)

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AppUpdateUiState(
            currentVersionName = readCurrentVersionName(),
            currentVersionCode = readCurrentVersionCode()
        )
    )
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdates(silent: Boolean) {
        if (_uiState.value.isChecking) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true)
            runCatching {
                appUpdateRepository.getLatestVersion()
            }.onSuccess { latest ->
                val hasUpdate = latest.latestVersionCode > _uiState.value.currentVersionCode
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    availableUpdate = latest.takeIf { hasUpdate },
                    snackbarMessage = if (!hasUpdate && !silent) "当前已是最新版本" else null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    snackbarMessage = if (silent) null else (error.message ?: "检查更新失败")
                )
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.value = _uiState.value.copy(availableUpdate = null)
    }

    fun consumeSnackbarMessage() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    private fun readCurrentVersionName(): String {
        val packageInfo = context.packageManager.getPackageInfoCompat(context.packageName)
        return packageInfo.versionName ?: ""
    }

    private fun readCurrentVersionCode(): Long {
        val packageInfo = context.packageManager.getPackageInfoCompat(context.packageName)
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }
}

private fun PackageManager.getPackageInfoCompat(packageName: String) =
    getPackageInfo(packageName, 0)
