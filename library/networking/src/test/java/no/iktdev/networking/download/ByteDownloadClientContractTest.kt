package no.iktdev.networking.download

import kotlinx.coroutines.runBlocking
import no.iktdev.networking.rest.DefaultClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ByteDownloadClientContractTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun downloadsChunkedResponseWhenLengthIsUnknown() = runBlocking {
        server.enqueue(MockResponse().setChunkedBody("chunked payload", 4))

        val bytes = ByteDownloadClient(DefaultClient())
            .download(server.url("/payload").toString())

        assertArrayEquals("chunked payload".toByteArray(), bytes)
    }

    @Test
    fun includesHttpStatusAndErrorBodyWhenDownloadFails() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))

        val error = assertThrows(IOException::class.java) {
            runBlocking {
                ByteDownloadClient(DefaultClient())
                    .download(server.url("/missing").toString())
            }
        }

        assertTrue(error.message.orEmpty().contains("HTTP 404"))
        assertTrue(error.message.orEmpty().contains("missing"))
    }

    @Test
    fun rejectsBlankUrlBeforeOpeningConnection() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ByteDownloadClient(DefaultClient()).download(" ") }
        }

        assertTrue(error.message.orEmpty().contains("blank"))
    }
}
