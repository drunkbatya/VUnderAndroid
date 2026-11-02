package com.drunkbatya.vunder.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    private fun version(text: String) = requireNotNull(ReleaseVersion.parse(text)) { text }

    @Test
    fun releaseTagsCompareNumerically() {
        assertTrue(version("1.10.0") > version("1.9.9"))
        assertTrue(version("2.0.0") > version("1.99.99"))
        assertTrue(version("1.2.4") > version("1.2.3"))
        assertTrue(version("0.0.0") < version("0.0.1"))
        assertEquals(0, version("1.2").compareTo(version("1.2.0")))
        assertEquals(0, version("1.2.3").compareTo(version("1.2.3")))
        assertNull(ReleaseVersion.parse("v1.2.3"))
        assertNull(ReleaseVersion.parse("1.2.3-beta"))
        assertNull(ReleaseVersion.parse(""))
    }
}
