package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.remote.ai.resolveAnthropicModelUrls
import org.junit.Assert.assertEquals
import org.junit.Test

class AnthropicCompatibleAdapterTest {
    @Test
    fun deepSeekAnthropicBaseUrlAlsoTriesProviderRootModelsEndpoint() {
        val urls = resolveAnthropicModelUrls("https://api.deepseek.com/anthropic")

        assertEquals(
            listOf(
                "https://api.deepseek.com/anthropic/models",
                "https://api.deepseek.com/models"
            ),
            urls
        )
    }

    @Test
    fun deepSeekV1AnthropicBaseUrlAlsoTriesV1AndRootModelsEndpoints() {
        val urls = resolveAnthropicModelUrls("https://api.deepseek.com/v1/anthropic")

        assertEquals(
            listOf(
                "https://api.deepseek.com/v1/anthropic/models",
                "https://api.deepseek.com/v1/models",
                "https://api.deepseek.com/models"
            ),
            urls
        )
    }

    @Test
    fun deepSeekAnthropicV1MessagesRequestUrlAlsoTriesProviderRootModelsEndpoint() {
        val urls = resolveAnthropicModelUrls("https://api.deepseek.com/anthropic/v1/messages")

        assertEquals(
            listOf(
                "https://api.deepseek.com/anthropic/v1/models",
                "https://api.deepseek.com/models"
            ),
            urls
        )
    }

    @Test
    fun deepSeekAnthropicMessagesRequestUrlAlsoTriesProviderRootModelsEndpoint() {
        val urls = resolveAnthropicModelUrls("https://api.deepseek.com/anthropic/messages")

        assertEquals(
            listOf(
                "https://api.deepseek.com/anthropic/models",
                "https://api.deepseek.com/models"
            ),
            urls
        )
    }

    @Test
    fun officialAnthropicBaseUrlKeepsV1ModelsEndpoint() {
        val urls = resolveAnthropicModelUrls("https://api.anthropic.com/v1")

        assertEquals(listOf("https://api.anthropic.com/v1/models"), urls)
    }

    @Test
    fun officialAnthropicMessagesRequestUrlKeepsV1ModelsEndpoint() {
        val urls = resolveAnthropicModelUrls("https://api.anthropic.com/v1/messages")

        assertEquals(listOf("https://api.anthropic.com/v1/models"), urls)
    }
}
