package no.iktdev.networking

import kotlinx.coroutines.runBlocking
import no.iktdev.networking.rest.DefaultClient
import no.iktdev.networking.rest.HttpConnectionConstructionException
import no.iktdev.networking.rest.ReadObjectException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class DefaultClientContractTest {
    data class Payload(val value: String)

    private lateinit var server: MockWebServer
    private lateinit var client: DefaultClient

    @Before
    fun startServer() {
        server = MockWebServer().also { it.start() }
        client = DefaultClient().usingUrl(server.url("/").toString())
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun requestBuilderAppliesVerbBodyAndBearerAuthorization() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"value":"saved"}"""))

        val response = client.withPaths("v1", "items")
            .withBearerToken("secret")
            .put<Payload>(Payload("Blåbær"))

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v1/items", request.path)
        assertEquals("Bearer secret", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Content-Type"))
        assertEquals("""{"value":"Blåbær"}""", request.body.readUtf8())
        assertEquals("saved", response.result?.value)
    }

    @Test
    fun nonSuccessResponseIsReturnedWithOriginalStatusAndErrorBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("temporarily unavailable"))

        val response = client.get<Payload>()

        assertEquals(503, response.status)
        assertNull(response.result)
        assertEquals("temporarily unavailable", response.error)
    }

    @Test
    fun emptySuccessfulResponseDoesNotAttemptJsonDecoding() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val response = client.delete<Payload>()

        assertEquals(204, response.status)
        assertNull(response.result)
        assertNull(response.error)
    }

    @Test
    fun invalidSuccessfulJsonIncludesResponseContext() {
        server.enqueue(MockResponse().setBody("not-json"))

        val error = assertThrows(ReadObjectException::class.java) {
            runBlocking { client.get<Payload>() }
        }

        assertEquals(200, error.responseStatus)
        assertEquals("not-json", error.responseBody)
        assertNotNull(error.cause)
    }

    @Test
    fun missingBaseUrlHasTypedConstructionFailure() {
        val error = assertThrows(HttpConnectionConstructionException::class.java) {
            runBlocking { DefaultClient().get<Payload>() }
        }

        assertNotNull(error.message)
    }

    @Test
    fun securityConfigurationKeepsDefaultClientTypeWhenChained() {
        val original = DefaultClient()

        val configured: DefaultClient = original
            .withAnyTrust()
            .withDefaultTrust()
            .withPinnedPublicKey("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")

        assertSame(original, configured)
    }
}
