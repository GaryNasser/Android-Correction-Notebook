package com.github.garynasser.correction_notebook.ui.update

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.appupdate.AppVersionInfo
import com.github.garynasser.correction_notebook.data.repository.AppUpdateRepository
import com.github.garynasser.correction_notebook.utils.isRemoteVersionNewer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        _uiState.update {
            it.copy(
                isChecking = true,
                snackbarMessage = null
            )
        }
        viewModelScope.launch {
            try {
                val latest = appUpdateRepository.getLatestVersion()
                val hasUpdate = isRemoteVersionNewer(
                    remoteVersionName = latest.latestVersionName,
                    currentVersionName = _uiState.value.currentVersionName
                )
                _uiState.update {
                    it.copy(
                        availableUpdate = latest.takeIf { hasUpdate },
                        snackbarMessage = if (!hasUpdate && !silent) "当前已是最新版本" else null
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (silent) null else (error.message ?: "检查更新失败")
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(isChecking = false)
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(availableUpdate = null) }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun reportDownloadFailure(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
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
