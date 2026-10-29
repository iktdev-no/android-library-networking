package no.iktdev.networking.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import no.iktdev.networking.rest.DefaultClient
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.URL
import kotlin.math.roundToInt

class ByteDownloadClient(
    private val client: DefaultClient,
) {
    suspend fun download(
        url: String,
        progress: (Int) -> Unit = {},
    ): ByteArray = withContext(Dispatchers.IO) {
        require(url.isNotBlank()) { "URL must not be blank" }

        val parsedUrl = URL(url)
        require(parsedUrl.protocol in listOf("http", "https")) {
            "URL must use HTTP or HTTPS: $url"
        }

        // DefaultClient.openStream() uses the URL configured on the client.
        client.usingUrl(url)
        val connection = client.openStream()

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                }
                throw IOException(
                    buildString {
                        append("HTTP $status while downloading $url")
                        if (!errorBody.isNullOrBlank()) append(": $errorBody")
                    }
                )
            }

            val totalBytes = connection.contentLengthLong

            connection.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var downloadedBytes = 0L
                    var previousProgress = -1

                    while (true) {
                        currentCoroutineContext().ensureActive()

                        val count = input.read(buffer)
                        if (count == -1) break

                        output.write(buffer, 0, count)
                        downloadedBytes += count

                        if (totalBytes > 0L) {
                            val percent = (downloadedBytes.toDouble() / totalBytes * 100)
                                .roundToInt()
                                .coerceIn(0, 100)

                            if (percent > previousProgress) {
                                previousProgress = percent
                                withContext(Dispatchers.Main) {
                                    progress(percent)
                                }
                            }
                        }
                    }

                    if (totalBytes > 0L && downloadedBytes < totalBytes) {
                        throw EOFException(
                            "Download ended at $downloadedBytes of $totalBytes bytes"
                        )
                    }

                    output.toByteArray()
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
