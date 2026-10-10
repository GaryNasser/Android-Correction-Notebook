package com.github.garynasser.correction_notebook.data.repository

import android.content.ContextWrapper
import android.content.Intent
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import androidx.room.Room
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFileDetail
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSearchResult
import com.github.garynasser.correction_notebook.ui.screens.knowledgebase.KnowledgeBaseViewModel
import com.github.garynasser.correction_notebook.ui.screens.knowledgebase.openFileExternally
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary
import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.ResponseBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okio.buffer
import okio.source
import okio.BufferedSource
import java.util.concurrent.atomic.AtomicInteger

class KnowledgeBaseDownloadTest {
    private lateinit var database: KnowledgeBaseDatabase
    private lateinit var directory: File
    private lateinit var context: ContextWrapper
    private lateinit var repository: KnowledgeBaseRepository
    private val detail = BitShareFileDetail(
        "remote-file", "Lecture notes", "Notes.txt", "txt", null, null,
        "text/plain", 4L, null, 0
    )
    private val payload = byteArrayOf(1, 2, 3, 4)

    @Before
    fun setup() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val base = File(target.filesDir, "knowledge_base").apply { mkdirs() }
        directory = File.createTempFile("download-qa-", "", base).apply {
            delete()
            mkdir()
        }
        context = object : ContextWrapper(target) {
            override fun getFilesDir(): File = directory
        }
        database = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        repository = KnowledgeBaseRepository(database.knowledgeBaseDao(), KnowledgeBaseFileStorage(context), context)
    }

    @After
    fun cleanup() {
        database.close()
        directory.deleteRecursively()
    }

    @Test
    fun cancellationAtEndOfCopyLeavesNoUnindexedFileAndRetrySucceeds() = runBlocking {
        var closed = false
        val importing = async(Dispatchers.Default) {
            val job = currentCoroutineContext()[Job]!!
            val input = object : ByteArrayInputStream(payload) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return super.read(buffer, offset, length).also { if (it == -1) job.cancel() }
                }
                override fun close() { closed = true; super.close() }
            }
            repository.importDownloadedFile(detail, KnowledgeBaseRepository.ROOT_FOLDER_ID, input)
        }
        importing.join()
        assertTrue(importing.isCancelled)
        assertTrue(closed)
        assertTrue("Cancelled import must remove its file before returning", savedFiles().isEmpty())
        assertTrue(database.knowledgeBaseDao().getAllFiles().isEmpty())
        repository.importDownloadedFile(detail, KnowledgeBaseRepository.ROOT_FOLDER_ID, ByteArrayInputStream(payload)).getOrThrow()
        val saved = database.knowledgeBaseDao().getAllFiles().single()
        assertArrayEquals(payload, File(saved.localPath).readBytes())
        assertEquals("Notes.txt", saved.displayName)
        assertEquals(1, savedFiles().size)
    }

    @Test
    fun failedCopyRemovesPartialFileWithoutChangingExistingNotes() = runBlocking {
        repository.importDownloadedFile(detail, KnowledgeBaseRepository.ROOT_FOLDER_ID, ByteArrayInputStream(payload)).getOrThrow()
        var closed = false
        val input = object : InputStream() {
            var reads = 0
            override fun read(): Int = throw IOException("broken stream")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (reads++ > 0) throw IOException("broken stream")
                buffer[offset] = 9
                return 1
            }
            override fun close() { closed = true }
        }
        assertTrue(repository.importDownloadedFile(detail, KnowledgeBaseRepository.ROOT_FOLDER_ID, input).isFailure)
        assertTrue(closed)
        val saved = database.knowledgeBaseDao().getAllFiles().single()
        assertArrayEquals(payload, File(saved.localPath).readBytes())
        assertEquals(1, savedFiles().size)
    }

    @Test
    fun cancellationInterruptsAStalledDownloadBodyAndRemovesPartialFile() = checkHttpCancellation(afterHeaders = true)

    @Test
    fun cancellationInterruptsADownloadWaitingForHeaders() = checkHttpCancellation(afterHeaders = false)

    @Test
    fun failedPublicDownloadDoesNotRepeatTheRequestOrTryAnIntranetAddress() = runBlocking {
        var requests = 0
        var consumes = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            assertEquals("app.bitshare.com.cn", chain.request().url.host)
            assertEquals(10043, chain.request().url.port)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable")
                .body("unavailable".toResponseBody()).build()
        }.build()
        try {
            val remote = BitShareRepository(service(client, BitShareApiService.BASE_URL), client)
            val result = remote.downloadFile(detail.id) { consumes++ }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("HTTP 503"))
            assertEquals(1, requests)
            assertEquals(0, consumes)
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun fullRemoteDownloadImportsReadableContentAndResolvesDuplicateNames() = runBlocking {
        val bytes = ByteArray(65_537) { (it % 251).toByte() }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("/api/public/files/file%20with%20space/download", chain.request().url.encodedPath)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(bytes.toResponseBody("text/plain".toMediaType())).build()
        }.build()
        val remote = BitShareRepository(service(client), client)
        try {
            repeat(2) {
                remote.downloadFile("file with space") { body ->
                    repository.importDownloadedFile(detail, KnowledgeBaseRepository.ROOT_FOLDER_ID, body.byteStream()).getOrThrow()
                }.getOrThrow()
            }
            val saved = database.knowledgeBaseDao().getAllFiles()
            assertEquals(2, saved.size)
            assertEquals(2, saved.map { it.displayName }.distinct().size)
            saved.forEach {
                assertArrayEquals(bytes, File(it.localPath).readBytes())
                assertEquals(bytes.size.toLong(), it.sizeBytes)
                assertEquals("bitshare", it.sourceType)
                assertEquals(detail.id, it.sourceFileId)
                var launched: Intent? = null
                val openingContext = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
                    override fun startActivity(intent: Intent) { launched = intent }
                }
                openFileExternally(openingContext, KnowledgeBaseFileSummary(
                    it.id, it.folderId, it.displayName, it.localPath, it.mimeType, it.sizeBytes,
                    it.sourceType, it.sourceTitle, it.courseId, it.courseName, emptyList(), it.downloadedAt
                ))
                val chooser = requireNotNull(launched)
                val intent = requireNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
                assertEquals(Intent.ACTION_VIEW, intent.action)
                assertEquals("text/plain", intent.type)
                assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                val uri = requireNotNull(intent.data)
                context.contentResolver.openInputStream(uri)!!.use { input -> assertArrayEquals(bytes, input.readBytes()) }
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(it.displayName, cursor.getString(0))
                }
            }
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun htmlJsonAndEmptyResponsesAreRejectedBeforeImport() = runBlocking {
        listOf("text/html" to "<html>error</html>", "application/json" to "{}", "text/plain" to "").forEach { (type, text) ->
            var requests = 0
            var consumes = 0
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                requests++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(text.toResponseBody(type.toMediaType())).build()
            }.build()
            val remote = BitShareRepository(service(client), client)
            try {
                assertTrue(remote.downloadFile(detail.id) { consumes++ }.isFailure)
                assertEquals(0, consumes)
                assertEquals(1, requests)
                assertTrue(savedFiles().isEmpty())
                assertTrue(database.knowledgeBaseDao().getAllFiles().isEmpty())
            } finally {
                client.dispatcher.executorService.shutdownNow()
                client.connectionPool.evictAll()
            }
        }
    }

    @Test
    fun importFailureDoesNotRetryTheDownloadOrHideTheOriginalError() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(payload.toResponseBody("text/plain".toMediaType())).build()
        }.build()
        val remote = BitShareRepository(service(client), client)
        val failure = IOException("No storage space")
        try {
            val result = remote.downloadFile<Unit>(detail.id) { throw failure }
            assertTrue(result.exceptionOrNull() === failure)
            assertEquals(1, requests)
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun cancelFromViewModelReleasesTheBusyStateAndAllowsRetry() = checkViewModelDownload(cancel = true)

    @Test
    fun closingTheDetailDoesNotCancelABackgroundDownload() = checkViewModelDownload(cancel = false)

    @Test
    fun clearingTheViewModelStopsItsDownloadAndCleansUp() = checkViewModelDownload(cancel = true, clear = true)

    private fun checkViewModelDownload(cancel: Boolean, clear: Boolean = false) = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val downloads = AtomicInteger()
        val closes = AtomicInteger()
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { cancelled.countDown() }
        }).addInterceptor { chain ->
            val body = if (!chain.request().url.encodedPath.endsWith("/download")) {
                """{"id":"remote-file","title":"Lecture notes","original_name":"Notes.txt","extension":"txt","mime_type":"text/plain","size":4}"""
                    .toResponseBody("application/json".toMediaType())
            } else if (downloads.incrementAndGet() == 1) {
                val input = object : ByteArrayInputStream(payload) {
                    var first = true
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (first) {
                            first = false
                            entered.countDown()
                            check(release.await(10, TimeUnit.SECONDS))
                        }
                        return super.read(buffer, offset, length)
                    }
                    override fun close() { closes.incrementAndGet(); super.close() }
                }
                object : ResponseBody() {
                    private val source = input.source().buffer()
                    override fun contentType(): MediaType = "text/plain".toMediaType()
                    override fun contentLength(): Long = payload.size.toLong()
                    override fun source(): BufferedSource = source
                }
            } else payload.toResponseBody("text/plain".toMediaType())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        }.build()
        val remote = BitShareRepository(service(client), client)
        val model = KnowledgeBaseViewModel(repository, remote, StudySetRepository(database.knowledgeBaseDao()))
        val store = ViewModelStore().apply { put("download", model) }
        val collector = launch(Dispatchers.Default) { model.uiState.collect { } }
        val result = BitShareSearchResult(detail.id, detail.title, detail.originalName, detail.extension, 4L, 0, null, "file")
        try {
            withContext(Dispatchers.Main) {
                model.downloadSearchResultToFolder(result, null)
                model.downloadSearchResultToFolder(result, null)
            }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            withTimeout(3_000) { while (model.uiState.value.activeDownloadId != detail.id) delay(10) }
            assertEquals(1, downloads.get())
            if (clear) {
                withContext(Dispatchers.Main) { store.clear() }
                assertTrue(cancelled.await(2, TimeUnit.SECONDS))
                withTimeout(3_000) { while (closes.get() == 0 || savedFiles().isNotEmpty()) delay(10) }
            } else if (cancel) {
                withContext(Dispatchers.Main) { model.cancelRemoteDownload() }
                withTimeout(3_000) { while (model.uiState.value.activeDownloadId != null) delay(10) }
                assertEquals("已取消下载", model.uiState.value.snackbarMessage)
                assertTrue(cancelled.await(1, TimeUnit.SECONDS))
                assertTrue(savedFiles().isEmpty())
                assertTrue(database.knowledgeBaseDao().getAllFiles().isEmpty())
                withContext(Dispatchers.Main) { model.downloadRemoteFileToFolder(null) }
            } else {
                withContext(Dispatchers.Main) { model.dismissRemoteDetail() }
                withTimeout(3_000) { while (model.uiState.value.selectedRemoteDetail != null) delay(10) }
                assertEquals(detail.id, model.uiState.value.activeDownloadId)
                assertEquals(1L, cancelled.count)
                release.countDown()
            }
            if (!clear) {
                withTimeout(3_000) { while (model.uiState.value.activeDownloadId != null || model.uiState.value.selectedTabIndex != 0 ||
                    model.uiState.value.snackbarMessage != "已保存到 知识库根目录") delay(10) }
                val saved = database.knowledgeBaseDao().getAllFiles().single()
                assertArrayEquals(payload, File(saved.localPath).readBytes())
                assertEquals(if (cancel) 2 else 1, downloads.get())
                assertEquals(1, savedFiles().size)
            } else assertTrue(database.knowledgeBaseDao().getAllFiles().isEmpty())
            assertEquals(1, closes.get())
        } finally {
            release.countDown()
            withContext(Dispatchers.Main) { store.clear() }
            collector.cancelAndJoin()
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    private fun service(client: OkHttpClient, url: String = "http://127.0.0.1/"): BitShareApiService =
        Retrofit.Builder().baseUrl(url).client(client).addConverterFactory(GsonConverterFactory.create()).build()
            .create(BitShareApiService::class.java)

    private fun checkHttpCancellation(afterHeaders: Boolean) = runBlocking { supervisorScope {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        var connection: Socket? = null
        val worker = thread(isDaemon = true, name = "knowledge-base-download-test") {
            runCatching {
                server.accept().use { socket ->
                    connection = socket
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    if (afterHeaders) {
                        socket.getOutputStream().write(
                            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 100\r\nConnection: close\r\n\r\nnotes".toByteArray()
                        )
                        socket.getOutputStream().flush()
                    }
                    entered.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
        }
        val stalledHost = "127.0.0.1"
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { if (call.request().url.host == stalledHost) cancelled.countDown() }
            override fun callFailed(call: Call, ioe: IOException) { if (call.request().url.host == stalledHost) finished.countDown() }
            override fun callEnd(call: Call) { if (call.request().url.host == stalledHost) finished.countDown() }
        }).build()
        val remote = BitShareRepository(service(client, "http://127.0.0.1:${server.localPort}/"), client)
        val download = async(Dispatchers.IO) {
            remote.downloadFile(detail.id) { body ->
                repository.importDownloadedFile(detail, KnowledgeBaseRepository.ROOT_FOLDER_ID, body.byteStream()).getOrThrow()
            }.getOrThrow()
        }
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            if (afterHeaders) withTimeout(3_000) {
                while (savedFiles().isEmpty()) kotlinx.coroutines.delay(10)
            }
            download.cancel()
            assertTrue("Download must stop without waiting for the remote server", withTimeoutOrNull(2_000) { download.join(); true } == true)
            assertTrue("Cancellation must abort the active socket", cancelled.await(1, TimeUnit.SECONDS))
            assertTrue("Response connection must be released", finished.await(1, TimeUnit.SECONDS))
            assertTrue(savedFiles().isEmpty())
            assertTrue(database.knowledgeBaseDao().getAllFiles().isEmpty())
        } finally {
            release.countDown()
            connection?.close()
            server.close()
            download.cancel()
            withContext(kotlinx.coroutines.NonCancellable) { download.join() }
            worker.join(1_000)
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    } }

    private fun savedFiles(): List<File> = directory.walkTopDown().filter { it.isFile }.toList()
}
