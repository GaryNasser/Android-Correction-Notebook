package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.remote.network.authResponseCount
import com.github.garynasser.correction_notebook.data.remote.network.selectRefreshToken
import com.github.garynasser.correction_notebook.data.remote.network.shouldClearTokensAfterRefresh
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthenticationNetworkPolicyTest {
    @Test
    fun rotatedRefreshTokenReplacesCurrentToken() {
        assertEquals("refresh-new", selectRefreshToken("refresh-old", " refresh-new "))
    }

    @Test
    fun missingRotatedRefreshTokenKeepsCurrentToken() {
        assertEquals("refresh-old", selectRefreshToken("refresh-old", "  "))
        assertEquals("refresh-old", selectRefreshToken("refresh-old", null))
    }

    @Test
    fun responseCountIncludesPriorAuthenticationAttempts() {
        val first = unauthorizedResponse()
        val second = unauthorizedResponse(priorResponse = first)

        assertEquals(1, authResponseCount(first))
        assertEquals(2, authResponseCount(second))
    }

    @Test
    fun onlyCredentialRejectionClearsStoredTokens() {
        assertEquals(true, shouldClearTokensAfterRefresh(401))
        assertEquals(false, shouldClearTokensAfterRefresh(500))
    }

    private fun unauthorizedResponse(priorResponse: Response? = null): Response {
        return Response.Builder()
            .request(Request.Builder().url("https://example.com/protected").build())
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(priorResponse)
            .build()
    }
}
