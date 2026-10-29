package no.iktdev.networking

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import kotlinx.coroutines.runBlocking
import no.iktdev.networking.rest.DefaultClient
import no.iktdev.networking.rest.HttpConnectionConstructionException
import no.iktdev.networking.rest.ReadObjectException

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertThrows

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
    fun httpErrorPreservesStatusAndErrorBody() = runBlocking {
        val errorBody = """{"error":"Authentication required"}"""
        server.enqueue(MockResponse().setBody(errorBody).setResponseCode(401))

        val response = client.get<TestData>()

        assertEquals(401, response.status)
        assertNull(response.result)
        assertEquals(errorBody, response.error)
    }

    @Test
    fun forbiddenResponsePreservesStatus() = runBlocking {
        server.enqueue(MockResponse().setBody("Access denied").setResponseCode(403))

        val response = client.get<TestData>()

        assertEquals(403, response.status)
        assertEquals("Access denied", response.error)
    }

    @Test
    fun noContentIsAValidSuccessfulResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val response = client.delete<TestData>()

        assertEquals(204, response.status)
        assertNull(response.result)
        assertNull(response.error)
    }

    @Test
    fun malformedSuccessfulBodyThrowsDecodeExceptionWithHttpContext() {
        server.enqueue(MockResponse().setBody("not-json").setResponseCode(200))

        val exception = assertThrows(ReadObjectException::class.java) {
            runBlocking { client.get<TestData>() }
        }

        assertEquals(200, exception.responseStatus)
        assertEquals("not-json", exception.responseBody)
        assertNotNull(exception.cause)
    }

    @Test
    fun missingBaseUrlThrowsConnectionConstructionException() {
        val exception = assertThrows(HttpConnectionConstructionException::class.java) {
            runBlocking { DefaultClient().get<TestData>() }
        }

        assertNotNull(exception.request)
    }

    @Test
    fun bearerTokenIsSentOverHttp() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"message":"ok"}""").setResponseCode(200))
        client.withBearerToken("test-token").get<TestData>()

        assertEquals("Bearer test-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun requestBodyUsesUtf8() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"message":"ok"}""").setResponseCode(200))

        client.post<TestData>(TestData("Blåbær 😅"))

        assertEquals(
            """{"message":"Blåbær 😅"}""",
            server.takeRequest().body.readUtf8(),
        )
    }

    @Test
    fun testGetWithQueryParameters() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"message":"hello"}""").setResponseCode(200))

        val response = client
            .withPaths("/v1/catalog/genre")
            .withQueryParams(
                "genreId" to 42L,
                "offset" to 0L,
                "limit" to 100,
            )
            .get<TestData>()

        assertEquals(200, response.status)
        assertEquals("/v1/catalog/genre?genreId=42&offset=0&limit=100", server.takeRequest().path)
    }

    @Test
    fun testGetQueryParametersAreEncodedAndNullsOmitted() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"message":"hello"}""").setResponseCode(200))

        client
            .withPaths("/catalog/genre")
            .withQueryParams(
                "search" to "space & slash/",
                "optional" to null,
            )
            .withQueryParam("tag", "one")
            .withQueryParam("tag", "two words")
            .get<TestData>()

        assertEquals(
            "/catalog/genre?search=space+%26+slash%2F&tag=one&tag=two+words",
            server.takeRequest().path,
        )
    }

    @Test
    fun queryParametersDoNotLeakToTheNextPath() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"message":"first"}""").setResponseCode(200))
        server.enqueue(MockResponse().setBody("""{"message":"second"}""").setResponseCode(200))

        client
            .withPaths("/catalog/genre")
            .withQueryParams("genreId" to 42L)
            .get<TestData>()

        client
            .withPaths("/catalog/all")
            .get<TestData>()

        assertEquals("/catalog/genre?genreId=42", server.takeRequest().path)
        assertEquals("/catalog/all", server.takeRequest().path)
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
