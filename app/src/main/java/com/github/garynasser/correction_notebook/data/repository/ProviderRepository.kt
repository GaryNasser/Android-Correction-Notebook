package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.ai.AiCredentialCipher
import com.github.garynasser.correction_notebook.data.local.ai.AiProviderDao
import com.github.garynasser.correction_notebook.data.local.ai.AiProviderEntity
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class ProviderRecord(
    val id: Long,
    val name: String,
    val type: AIProviderType,
    val baseUrl: String,
    val apiKey: String,
    val defaultModel: String,
    val customHeadersJson: String,
    val temperature: Double?,
    val maxTokens: Int?,
    val contextMessageLimit: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Singleton
class ProviderRepository @Inject constructor(
    private val dao: AiProviderDao,
    private val credentialCipher: AiCredentialCipher
) {
    fun observeProviders(): Flow<List<ProviderRecord>> =
        dao.observeProviders().map { providers -> providers.map(::toRecord) }

    fun observeActiveProvider(): Flow<ProviderRecord?> =
        dao.observeActiveProvider().map { it?.let(::toRecord) }

    suspend fun getActiveProvider(): ProviderRecord? = dao.getActiveProvider()?.let(::toRecord)

    suspend fun getProviderById(providerId: Long): ProviderRecord? =
        dao.getProviderById(providerId)?.let(::toRecord)

    suspend fun saveProvider(record: ProviderRecord): Long {
        val now = System.currentTimeMillis()
        val entity = AiProviderEntity(
            id = record.id,
            name = record.name,
            type = record.type,
            baseUrl = record.baseUrl,
            apiKeyEncrypted = credentialCipher.encrypt(record.apiKey),
            defaultModel = record.defaultModel,
            customHeadersJson = record.customHeadersJson,
            temperature = record.temperature,
            maxTokens = record.maxTokens,
            contextMessageLimit = record.contextMessageLimit.coerceIn(1, 60),
            isActive = record.isActive,
            createdAt = if (record.createdAt == 0L) now else record.createdAt,
            updatedAt = now
        )
        val providerId = if (record.id == 0L) dao.insertProvider(entity) else {
            dao.updateProvider(entity)
            record.id
        }
        if (record.isActive) {
            dao.activateProvider(providerId, now)
        }
        return providerId
    }

    suspend fun activateProvider(providerId: Long) {
        dao.activateProvider(providerId)
    }

    suspend fun deleteProvider(providerId: Long) {
        dao.deleteProviderById(providerId)
    }

    suspend fun countProviders(): Int = dao.countProviders()

    private fun toRecord(entity: AiProviderEntity): ProviderRecord {
        return ProviderRecord(
            id = entity.id,
            name = entity.name,
            type = entity.type,
            baseUrl = entity.baseUrl,
            apiKey = credentialCipher.decrypt(entity.apiKeyEncrypted),
            defaultModel = entity.defaultModel,
            customHeadersJson = entity.customHeadersJson,
            temperature = entity.temperature,
            maxTokens = entity.maxTokens,
            contextMessageLimit = entity.contextMessageLimit,
            isActive = entity.isActive,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
