package com.github.garynasser.correction_notebook.data.remote.cas

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

internal fun ssoResponseFixture(request: Request): Response? {
    val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
    return when {
        request.url.encodedPath == "/auth-protocol-core/login" -> {
            val service = "https://jxzxehall.bit.edu.cn/auth-protocol-core/loginSuccess?sessionToken=qa-test"
            val location = "https://sso.bit.edu.cn/cas/login".toHttpUrl().newBuilder().addQueryParameter("service", service).build()
            builder.code(302).header("Location", location.toString()).body("".toResponseBody()).build()
        }
        request.url.encodedPath in setOf("/auth-protocol-core/loginSuccess", "/jwapp/sys/xsfacx/*default/index.do",
            "/jwapp/sys/funauthapp/api/getAppConfig/xsfacx-4766859113956613.do") -> builder.body("{}".toResponseBody()).build()
        request.url.encodedPath.startsWith("/cas/api/protected/user/findCaptchaCount/") ->
            builder.body("""{"code":200,"data":{"captchaInvisible":false}}""".toResponseBody()).build()
        request.url.encodedPath == "/cas/login" && request.method == "GET" -> builder.body(
            "<form action='${request.url}'></form><div id='login-page-flowkey'>execution-qa</div><div id='login-croypto'>MDEyMzQ1Njc4OWFiY2RlZg==</div>".toResponseBody()
        ).build()
        request.url.encodedPath == "/cas/login" && request.method == "POST" -> {
            val form = request.body as FormBody
            val id = (0 until form.size).single { form.name(it) == "username" }.let(form::value)
            val callback = request.url.queryParameter("service")!!.toHttpUrl().newBuilder().addQueryParameter("ticket", "ST-$id").build()
            builder.code(302).header("Location", callback.toString()).body("".toResponseBody()).build()
        }
        else -> null
    }
}
