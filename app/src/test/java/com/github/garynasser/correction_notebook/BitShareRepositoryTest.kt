package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import com.github.garynasser.correction_notebook.data.repository.BitShareRepository
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class BitShareRepositoryTest {
    @Test
    fun newFileDetailUsesNameForDisplayAndDownloadedFileName() = withResponse(
        """{"id":"file","name":"lecture.pdf","extension":"pdf","size":42,"download_count":7}"""
    ) { repository ->
        val detail = repository.getFileDetail("file").getOrThrow()
        assertEquals("lecture.pdf", detail.title)
        assertEquals("lecture.pdf", detail.originalName)
        assertEquals(42L, detail.sizeBytes)
        assertEquals(7, detail.downloadCount)
    }

    @Test
    fun legacyDetailStillKeepsItsOriginalFileName() = withResponse(
        """{"id":"file","title":"Lecture","original_name":"Lecture.txt","size":12}"""
    ) { repository ->
        val detail = repository.getFileDetail("file").getOrThrow()
        assertEquals("Lecture", detail.title)
        assertEquals("Lecture.txt", detail.originalName)
    }

    @Test
    fun publicSearchInfersMissingExtensionFromNameAndKeepsPagination() = withResponse(
        """{"items":[{"id":"file","name":"Lecture.PDF","entity_type":"file","size":42}],"page":1,"page_size":1,"total":2}"""
    ) { repository ->
        val page = repository.searchFiles("Lecture").getOrThrow()
        assertEquals("pdf", page.items.single().extension)
        assertEquals("Lecture.PDF", page.items.single().originalName)
        assertEquals(2, page.nextPage)
    }

    private fun withResponse(json: String, check: suspend (BitShareRepository) -> Unit) = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("app.bitshare.com.cn", chain.request().url.host)
            assertEquals(10043, chain.request().url.port)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val service = Retrofit.Builder().baseUrl(BitShareApiService.BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(BitShareApiService::class.java)
        try {
            check(BitShareRepository(service, client))
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }
}
