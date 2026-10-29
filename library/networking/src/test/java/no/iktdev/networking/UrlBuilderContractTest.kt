package no.iktdev.networking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlBuilderContractTest {
    @Test
    fun appendsPathSegmentsWithoutChangingHostOrPort() {
        val builder = UrlBuilder("https://example.test:8443/api/")
            .with("v1", "/catalog/items/")

        assertEquals("https://example.test:8443/api/v1/catalog/items", builder.toUrl())
        assertEquals(builder.toUrl(), builder.asURL().toString())
    }

    @Test
    fun recognizesProtocolsWithoutCaseSensitivity() {
        assertTrue(UrlBuilder("HTTPS://example.test").isHttps("https://example.test/path"))
        assertTrue(UrlBuilder("http://example.test").isHttp("HTTP://example.test/path"))
        assertFalse(UrlBuilder("http://example.test").isHttps("http://example.test/path"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedProtocol() {
        UrlBuilder("ftp://example.test/resource")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUrlWithoutAuthority() {
        UrlBuilder("https:/missing-host")
    }
}
