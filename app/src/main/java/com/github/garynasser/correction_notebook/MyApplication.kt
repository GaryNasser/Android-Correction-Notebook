package com.github.garynasser.correction_notebook

import android.app.Application
import com.github.garynasser.correction_notebook.data.repository.AiSettingsMigrationCoordinator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application() {
    @Inject
    lateinit var aiSettingsMigrationCoordinator: AiSettingsMigrationCoordinator

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            aiSettingsMigrationCoordinator.migrateIfNeeded()
        }
    }
}
