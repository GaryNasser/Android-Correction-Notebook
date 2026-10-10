package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import com.github.garynasser.correction_notebook.di.NetworkModule
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class BitShareEndpointTest {
    @Test
    fun publicDownloadsUseTheOfficialHttpsRouteAndPort() {
        val api = NetworkModule.provideBitShareApiService(OkHttpClient())
        val url = api.downloadFile("file with space").request().url
        assertEquals("https", url.scheme)
        assertEquals("app.bitshare.com.cn", url.host)
        assertEquals(10043, url.port)
        assertEquals("/api/public/files/file%20with%20space/download", url.encodedPath)
    }

    @Test
    fun folderBrowserLinkKeepsTheFolderIdAsOneEncodedQueryValue() {
        val id = "folder/1 & next=2"
        val url = BitShareApiService.folderPageUrl(id).toHttpUrl()
        assertEquals("https://app.bitshare.com.cn:10043/", url.newBuilder().query(null).build().toString())
        assertEquals(id, url.queryParameter("folder"))
        assertEquals(setOf("folder"), url.queryParameterNames)
    }
}
