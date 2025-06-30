package no.iktdev.networking

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import kotlinx.coroutines.runBlocking
import no.iktdev.networking.rest.DefaultClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class DefaultClientHttpTest {
    data class TestData(val message: String)

    private lateinit var server: MockWebServer
    private lateinit var client: DefaultClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = DefaultClient().usingUrl(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testGet() = runBlocking {
        val responseBody = """{"message":"hello"}"""
        server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val response = client.get<TestData>()
        assertEquals(200, response.status)
        assertNotNull(response.result)
        assertEquals("hello", response.result?.message)
    }

    @Test
    fun testPost() = runBlocking {
        val responseBody = """{"message":"created"}"""
        server.enqueue(MockResponse().setBody(responseBody).setResponseCode(201))

        val response = client.post<TestData>(TestData("create"))
        assertEquals(201, response.status)
        assertNotNull(response.result)
        assertEquals("created", response.result?.message)
    }

    @Test
    fun testDelete() = runBlocking {
        val responseBody = """{"message":"deleted"}"""
        server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val response = client.delete<TestData>()
        assertEquals(200, response.status)
        assertNotNull(response.result)
        assertEquals("deleted", response.result?.message)
    }
}