package com.github.garynasser.correction_notebook.data.repository

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.ai.AiCredentialCipher
import com.github.garynasser.correction_notebook.data.local.ai.AiDatabase
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderRepositoryFlowTest {
    private lateinit var database: AiDatabase
    private lateinit var repository: ProviderRepository
    private val gson = Gson()

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AiDatabase::class.java).build()
        repository = ProviderRepository(database.aiProviderDao(), AiCredentialCipher(context))
    }

    @After
    fun teardown() { database.close() }

    @Test
    fun activatingAMissingProviderFailsWithoutClearingTheCurrentProvider() = runBlocking {
        val first = save("First")
        val second = save("Second", active = false)
        val failure = runCatching { repository.activateProvider(second + 100) }.exceptionOrNull()
        assertNotNull("A missing provider must not be treated as successfully activated", failure)
        assertEquals(first, repository.getActiveProvider()?.id)
        assertEquals(1, repository.observeProviders().first().count { it.isActive })
    }

    @Test
    fun deletingTheActiveProviderActivatesTheLatestRemainingProvider() = runBlocking {
        val first = save("First")
        val second = save("Second")
        assertEquals(second, repository.getActiveProvider()?.id)
        repository.deleteProvider(second)
        assertEquals(first, repository.getActiveProvider()?.id)
        assertEquals(1, repository.countProviders())
        repository.deleteProvider(first)
        assertNull(repository.getActiveProvider())
        assertEquals(0, repository.countProviders())
    }

    @Test
    fun updatingADeletedProviderCannotRecreateItOrReplaceTheActiveOne() = runBlocking {
        val first = save("First")
        val second = save("Second", active = false)
        val record = requireNotNull(repository.getProviderById(second))
        repository.deleteProvider(second)
        assertTrue(runCatching { repository.saveProvider(record.copy(name = "Recreated")) }.isFailure)
        assertEquals(first, repository.getActiveProvider()?.id)
        assertEquals(1, repository.countProviders())
    }

    private suspend fun save(name: String, active: Boolean = true): Long = repository.saveProvider(
        AiProviderConfigMapper.toRecord(AiProviderForm(name = name, baseUrl = "https://unused.invalid/v1",
            apiKey = "qa-key", model = "qa-model", isActive = active), gson)
    )
}
