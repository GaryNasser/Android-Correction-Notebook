package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.remote.api.VideoApiService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class YanhePortalApiContractTest {
    @Test fun portalCallsUseNewEndpointsAndCourseScopedReplayId() = runBlocking {
        val requests = mutableListOf<Request>()
        val network = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("QA")
                .body("""{"code":1000,"params":{"result":{"total":0,"data":[]}}}""".toResponseBody()).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://aita.yanhekt.cn/").client(network)
            .addConverterFactory(GsonConverterFactory.create()).build().create(VideoApiService::class.java)
        try {
            val result = api.getPrivateCourseList("Bearer test", 2, 100)
            assertEquals(1000, result.code)
            assertNotNull(result.data!!.asJsonObject.get("result"))
            api.getCourseSession("Bearer test", 12)
            api.getCourseSessionDetail("Bearer test", 34, 12)
            api.getYanheUser("Bearer test")
            api.getCourseList("Bearer test", null, 1, 16, "matrix")
            assertEquals(listOf("/vlabpassportapi/v1/account-profile/course", "/courseapi/v3/multi-search/get-course-detail",
                "/courseapi/v3/portal-home-setting/get-sub-info", "/userapi/v1/infosimple",
                "/courseapi/v3/portal-home-setting/get-all-list"), requests.map { it.url.encodedPath })
            assertTrue(requests.all { it.url.host == "aita.yanhekt.cn" && it.header("Authorization") == "Bearer test" })
            assertEquals("1", requests[0].url.queryParameter("force_mycourse"))
            assertEquals("2", requests[0].url.queryParameter("nowpage"))
            assertEquals("100", requests[0].url.queryParameter("per-page"))
            assertEquals("34", requests[2].url.queryParameter("sub_id"))
            assertEquals("12", requests[2].url.queryParameter("course_id"))
            assertEquals("multi", requests[4].url.queryParameter("type"))
            assertEquals("21", requests[4].url.queryParameter("tenant"))
            assertEquals("matrix", requests[4].url.queryParameter("search"))
        } finally { network.dispatcher.executorService.shutdownNow(); network.connectionPool.evictAll() }
    }
}
