package com.github.garynasser.correction_notebook.data.remote.eclass

import com.github.garynasser.correction_notebook.data.model.auth.UserCredential
import com.github.garynasser.correction_notebook.data.model.home.DeadlineItem
import com.github.garynasser.correction_notebook.data.model.home.parseDeadline
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import com.github.garynasser.correction_notebook.data.remote.network.awaitResponse
import com.github.garynasser.correction_notebook.di.BasicRetrofit
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.CookieManager
import java.net.CookiePolicy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EclassRemoteDataSource @Inject constructor(
    private val cas: BitCasClient,
    @BasicRetrofit private val network: OkHttpClient
) {
    private val mutex = Mutex()
    private var signedIn: Pair<String, OkHttpClient>? = null

    suspend fun fetch(credential: UserCredential): List<DeadlineItem> = mutex.withLock {
        val client = signedIn?.takeIf { it.first == credential.studentId }?.second
            ?: createClient()
        if (!isAuthenticated(client)) {
            establishSession(client, credential)
            if (!isAuthenticated(client)) error("学习平台登录未完成，请重新登录学校账号")
        }
        signedIn = credential.studentId to client
        val courses = get(client, "/api/my-courses").get("courses")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: error("学习平台没有返回课程列表")
        val semaphore = Semaphore(3)
        coroutineScope {
            courses.map { element -> async { semaphore.withPermit {
                val course = element.asJsonObject
                val cid = course.get("id")?.asLong?.takeIf { it > 0 } ?: error("学习平台课程编号无效")
                val name = course.get("name")?.asString.orEmpty()
                val activities = get(client, "/api/courses/$cid/activities?sub_course_id=0")
                    .get("activities")?.takeIf { it.isJsonArray }?.asJsonArray ?: error("学习平台作业列表格式异常")
                val homework = activities.filter { it.isJsonObject && it.asJsonObject.get("type")?.asString == "homework" }
                    .map { row ->
                        val aid = row.asJsonObject.get("id")?.asLong ?: error("学习平台作业编号无效")
                        parseDeadline(get(client, "/api/activities/$aid"), cid, name, "homework")
                    }
                val exams = get(client, "/api/courses/$cid/exams?fields=id,title,type,start_time,end_time&no-intercept=true")
                    .get("exams")?.takeIf { it.isJsonArray }?.asJsonArray ?: error("学习平台考试列表格式异常")
                homework + exams.map { parseDeadline(it.asJsonObject, cid, name, "exam") }
            } } }.awaitAll().flatten().distinctBy { it.id }
                .sortedWith(compareBy<DeadlineItem> { it.deadline ?: Long.MAX_VALUE }.thenBy { it.id })
        }
    }

    private fun createClient(): OkHttpClient = network.newBuilder()
        .cookieJar(JavaNetCookieJar(CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)))
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            require(url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
                url.host in setOf("zy-eclass.bit.edu.cn", "zy-identity.bit.edu.cn", "sso.bit.edu.cn", "login.bit.edu.cn")) {
                "已阻止非学校学习平台地址"
            }
            chain.proceed(chain.request())
        }.build()

    private suspend fun isAuthenticated(client: OkHttpClient): Boolean =
        client.newCall(Request.Builder().url("$ORIGIN/api/profile").build()).awaitResponse { response ->
            if (response.code in setOf(401, 403) || response.request.url.host != "zy-eclass.bit.edu.cn") false
            else if (!response.isSuccessful) error("学习平台暂时无法连接：${response.code}")
            else runCatching { JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject.get("active")?.asBoolean == true }
                .getOrDefault(false)
        }

    private suspend fun establishSession(client: OkHttpClient, credential: UserCredential) {
        val service = client.newCall(Request.Builder().url("$ORIGIN/login").build()).awaitResponse { response ->
            val target = response.request.url
            if (target.scheme != "https" || target.host != "sso.bit.edu.cn" || target.port != 443) {
                error("学习平台未返回学校统一认证入口")
            }
            target.queryParameter("service")?.toHttpUrl() ?: error("学习平台未提供统一认证回调")
        }
        require(service.scheme == "https" && service.host == "zy-identity.bit.edu.cn" && service.port == 443 &&
            service.encodedPath == "/auth/realms/bit/broker/cas-client/endpoint" &&
            service.username.isEmpty() && service.password.isEmpty()) { "学习平台统一认证回调异常" }
        val ticket = cas.getServiceTicketFor(credential.studentId, credential.password, service.toString())
        client.newCall(Request.Builder().url(service.newBuilder().addQueryParameter("ticket", ticket).build()).build())
            .awaitResponse { response -> if (!response.isSuccessful) error("学习平台登录失败：${response.code}") }
    }

    private suspend fun get(client: OkHttpClient, path: String): JsonObject =
        client.newCall(Request.Builder().url(ORIGIN + path).header("Referer", "$ORIGIN/").build()).awaitResponse { response ->
            if (response.code in setOf(401, 403)) {
                signedIn = null
                error("学习平台登录已过期，请重新同步")
            }
            if (!response.isSuccessful || response.request.url.host != "zy-eclass.bit.edu.cn") {
                error("学习平台读取失败：${response.code}，已保留上次数据")
            }
            runCatching { JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject }
                .getOrElse { error("学习平台响应格式异常，已保留上次数据") }
        }

    companion object { const val ORIGIN = "https://zy-eclass.bit.edu.cn" }
}
