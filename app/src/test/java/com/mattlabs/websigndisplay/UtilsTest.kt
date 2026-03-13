package com.mattlabs.websigndisplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for pure logic used in FullscreenActivity and SettingsActivity.
 *
 * These tests do not require a device or emulator — run with:
 *   ./gradlew test
 */
class UtilsTest {

    // -- URL scheme prefix logic --
    // FullscreenActivity prefixes bare domains with "http://" before loading.

    @Test
    fun `url without scheme gets http prefix`() {
        val url = "example.com"
        val result = prefixUrl(url)
        assertEquals("http://example.com", result)
    }

    @Test
    fun `url with http scheme is unchanged`() {
        val url = "http://example.com"
        val result = prefixUrl(url)
        assertEquals("http://example.com", result)
    }

    @Test
    fun `url with https scheme is unchanged`() {
        val url = "https://example.com/sign?board=1"
        val result = prefixUrl(url)
        assertEquals("https://example.com/sign?board=1", result)
    }

    @Test
    fun `url with subdomain and no scheme gets http prefix`() {
        val url = "signs.example.com/menu"
        val result = prefixUrl(url)
        assertEquals("http://signs.example.com/menu", result)
    }

    // -- Reload interval clamping --
    // SettingsActivity rejects values < 1; FullscreenActivity clamps via coerceAtLeast(1).

    @Test
    fun `reload interval of 1 is accepted as-is`() {
        assertEquals(1, clampInterval(1))
    }

    @Test
    fun `reload interval of 10 is accepted as-is`() {
        assertEquals(10, clampInterval(10))
    }

    @Test
    fun `reload interval of 0 is clamped to 1`() {
        assertEquals(1, clampInterval(0))
    }

    @Test
    fun `reload interval of negative number is clamped to 1`() {
        assertEquals(1, clampInterval(-5))
    }

    // -- URL configured check --
    // FullscreenActivity opens Settings immediately if the URL is empty (signURL.isEmpty()).

    @Test
    fun `unconfigured empty url is not considered configured`() {
        assertFalse(isUrlConfigured(""))
    }

    @Test
    fun `url with only whitespace is not considered configured`() {
        assertFalse(isUrlConfigured("   "))
    }

    @Test
    fun `valid url is considered configured`() {
        assertTrue(isUrlConfigured("example.com"))
    }

    @Test
    fun `valid url with scheme is considered configured`() {
        assertTrue(isUrlConfigured("http://signs.example.com/board"))
    }

    // -- Helpers that mirror FullscreenActivity logic --

    private fun prefixUrl(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "http://$url"
        } else {
            url
        }
    }

    private fun clampInterval(value: Int): Int = value.coerceAtLeast(1)

    private fun isUrlConfigured(url: String): Boolean = url.isNotBlank()
}
