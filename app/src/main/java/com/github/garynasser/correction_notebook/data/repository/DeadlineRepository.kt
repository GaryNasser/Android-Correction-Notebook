package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.garynasser.correction_notebook.data.model.home.DeadlineItem
import com.github.garynasser.correction_notebook.data.remote.eclass.EclassRemoteDataSource
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.deadlines by preferencesDataStore("deadline_prefs")

data class DeadlineSnapshot(val owner: String, val items: List<DeadlineItem>, val updatedAt: Long)

@Singleton
class DeadlineRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val remote: EclassRemoteDataSource,
    private val yanhe: YanheRepository
) {
    @Inject constructor(@ApplicationContext context: Context, remote: EclassRemoteDataSource, yanhe: YanheRepository) :
        this(context.deadlines, remote, yanhe)
    private val key = stringPreferencesKey("snapshot")
    private val gson = Gson()

    suspend fun cached(): DeadlineSnapshot? {
        val owner = owner() ?: return null
        val json = dataStore.data.first()[key] ?: return null
        return runCatching {
            gson.fromJson(json, DeadlineSnapshot::class.java).also { snapshot ->
                require(snapshot.items.all { it.id.isNotBlank() && it.title.isNotBlank() })
            }
        }.getOrNull()?.takeIf { it.owner == owner }
    }

    suspend fun refresh(): DeadlineSnapshot {
        val version = yanhe.sessionVersion
        val credential = yanhe.getStudentCredential() ?: error("请先登录学校账号")
        val owner = owner() ?: error("请先登录学校账号")
        val items = remote.fetch(credential)
        return yanhe.withSession(version) {
            val snapshot = DeadlineSnapshot(owner, items, System.currentTimeMillis())
            dataStore.edit { it[key] = gson.toJson(snapshot) }
            snapshot
        }
    }

    private fun owner(): String? = yanhe.getStudentCredential()?.studentId?.let { id ->
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
