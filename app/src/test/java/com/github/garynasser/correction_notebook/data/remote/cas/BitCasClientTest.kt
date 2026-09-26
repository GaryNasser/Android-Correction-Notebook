package com.github.garynasser.correction_notebook.data.remote.cas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BitCasClientTest {

    @Test
    fun `bad request and unauthorized indicate rejected credentials`() {
        assertTrue(isCasCredentialFailureStatus(400))
        assertTrue(isCasCredentialFailureStatus(401))
    }

    @Test
    fun `server and network gateway responses do not reject saved credentials`() {
        assertFalse(isCasCredentialFailureStatus(403))
        assertFalse(isCasCredentialFailureStatus(500))
        assertFalse(isCasCredentialFailureStatus(502))
    }
}
