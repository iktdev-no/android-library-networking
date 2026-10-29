package no.iktdev.networking.download

import kotlinx.coroutines.runBlocking
import no.iktdev.networking.models.DownloadListener
import no.iktdev.networking.models.DownloadState
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadClientContractTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
    fun downloadsToTargetAndReportsCompletion() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Length", "6")
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Disposition", "attachment; filename=hello.txt")
        ) // HEAD metadata
        server.enqueue(MockResponse().setBody("hello!")) // GET content

        val listener = RecordingListener()
        val client = DownloadClient(listener)
        val target = File(temporaryFolder.root, "nested/hello.txt")
        try {
            client.start(server.url("/hello.txt").toString(), target).join()

            assertEquals("hello!", target.readText())
            assertEquals(DownloadState.Completed, client.state)
            assertEquals(target, listener.completedFile)
            assertEquals("hello.txt", listener.remoteInfo?.fileName)
            assertEquals(6L, listener.remoteInfo?.fileSize)
            assertTrue(listener.progress.any { it.first == 6L && it.third == 100 })
        } finally {
            client.close()
        }
    }

    @Test
    fun resumesPartFileUsingRangeAndReplacesTargetOnlyAfterSuccess() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Length", "6")
                .setHeader("Accept-Ranges", "bytes")
        ) // HEAD metadata
        server.enqueue(
            MockResponse().setResponseCode(206)
                .setHeader("Content-Range", "bytes 3-5/6")
                .setBody("lo!")
        ) // remaining bytes

        val listener = RecordingListener()
        val client = DownloadClient(listener)
        val target = File(temporaryFolder.root, "hello.txt").apply { writeText("previous") }
        File(temporaryFolder.root, "hello.txt.part").writeText("hel")
        try {
            client.start(server.url("/hello.txt").toString(), target).join()

            assertEquals("hello!", target.readText())
            assertTrue(!File(temporaryFolder.root, "hello.txt.part").exists())
            val headRequest = server.takeRequest()
            assertEquals("HEAD", headRequest.method)
            assertNull(headRequest.getHeader("Range"))
            val getRequest = server.takeRequest()
            assertEquals("GET", getRequest.method)
            assertEquals("bytes=3-", getRequest.getHeader("Range"))
            assertEquals(DownloadState.Completed, client.state)
        } finally {
            client.close()
        }
    }

    @Test
    fun failedHttpDownloadKeepsExistingTargetAndReportsFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Length", "3"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))

        val listener = RecordingListener()
        val client = DownloadClient(listener)
        val target = File(temporaryFolder.root, "existing.bin").apply { writeText("old") }
        try {
            client.start(server.url("/file").toString(), target).join()

            assertEquals("old", target.readText())
            assertEquals(DownloadState.Failed, client.state)
            assertNotNull(listener.failure)
            assertTrue(listener.failure?.message.orEmpty().contains("500"))
        } finally {
            client.close()
        }
    }

    private class RecordingListener : DownloadListener {
        var completedFile: File? = null
        var remoteInfo: no.iktdev.networking.download.core.RemoteFileInformationData? = null
        var failure: Throwable? = null
        val progress = mutableListOf<Triple<Long, Long, Int?>>()

        override fun onRemoteFileInfo(remoteFileInformation: no.iktdev.networking.download.core.RemoteFileInformationData?) {
            remoteInfo = remoteFileInformation
        }

        override fun onProgress(downloadedBytes: Long, totalBytes: Long, percent: Int?) {
            progress += Triple(downloadedBytes, totalBytes, percent)
        }

        override fun onCompleted(targetFile: File) {
            completedFile = targetFile
        }

        override fun onFailed(targetFile: File, error: Throwable) {
            failure = error
        }
    }
}
