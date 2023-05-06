package no.iktdev.networking

import junit.framework.TestCase

class UrlBuilderTest : TestCase() {
    val expectedDomainAddress = "http://iktdev.no"

    fun testCorrectDomainSet() {
        val builder = UrlBuilder(expectedDomainAddress)
        assertEquals(expectedDomainAddress, builder.url)
    }

    fun testCorrectPathsToUrl() {
        val builder = UrlBuilder(expectedDomainAddress).with(arrayListOf("one", "two", "three"))
        assertEquals("$expectedDomainAddress/one/two/three", builder.toUrl().toString())

    }

    fun testPersistentMultiPathsToUrl() {
        val builder = UrlBuilder("$expectedDomainAddress/one/two/three").with(arrayListOf("one", "two", "three"))
        assertEquals("$expectedDomainAddress/one/two/three/one/two/three", builder.toUrl().toString())

    }

    fun testPathPersists() {
        val builder = UrlBuilder("$expectedDomainAddress/one").with(arrayListOf())
        assertEquals("$expectedDomainAddress/one", builder.toUrl().toString())
    }

    fun testSinglePathOnHttps() {
        val excpected = "https://streamit.ls1.skjonborg.no/api/catalog"
        val builder = UrlBuilder("https://streamit.ls1.skjonborg.no/api/").with(arrayListOf("catalog"))
        val url = builder.toUrl()
        val URL = builder.asURL()
        assertEquals(excpected, url)
        assertEquals(excpected, URL.toString())
    }
}