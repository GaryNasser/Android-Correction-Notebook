package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.utils.compareVersionNames
import com.github.garynasser.correction_notebook.utils.isRemoteVersionNewer
import com.github.garynasser.correction_notebook.utils.versionNameToCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionNameComparatorTest {
    @Test
    fun treatsVPrefixAndMissingPatchAsEqual() {
        assertEquals(0, compareVersionNames("v1.0.0", "1.0"))
    }

    @Test
    fun detectsNewerPatchRelease() {
        assertTrue(isRemoteVersionNewer("v1.0.1", "1.0"))
    }

    @Test
    fun doesNotTreatOlderReleaseAsNewer() {
        assertFalse(isRemoteVersionNewer("v0.9.9", "1.0"))
    }

    @Test
    fun detectsReleaseTagsWithProductPrefix() {
        assertTrue(isRemoteVersionNewer("BITStudy-v1.3.0", "1.2.9"))
    }

    @Test
    fun ignoresPrereleaseSuffixWhenCoreVersionMatches() {
        assertEquals(0, compareVersionNames("1.3.0-beta1", "1.3.0"))
    }

    @Test
    fun convertsPrefixedVersionNameToComparableCode() {
        assertEquals(1_003_000L, versionNameToCode("BITStudy-v1.3.0"))
    }
}
