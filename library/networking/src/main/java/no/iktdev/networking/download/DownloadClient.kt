package no.iktdev.networking.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.iktdev.networking.download.core.RemoteFileInformation
import no.iktdev.networking.models.DownloadListener
import no.iktdev.networking.models.DownloadState
import no.iktdev.networking.rest.DefaultClient
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection

class DownloadClient(
    private val listener: DownloadListener
) : DefaultClient() {

    private val lock = Any()
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var state: DownloadState = DownloadState.NotStarted
        private set

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    private var downloadJob: Job? = null
    private var pauseSignal: CompletableDeferred<Unit>? = null
    private var cancelRequested = false

    private val bufferSize = 64 * 1024

    // Keep fluent calls typed as DownloadClient, including the security configuration.
    override fun usingUrl(url: String): DownloadClient =
        apply { super.usingUrl(url) }

    override fun withTimeout(timeout: Int): DownloadClient =
        apply { super.withTimeout(timeout) }

    override fun withBearerToken(token: String): DownloadClient =
        apply { super.withBearerToken(token) }

    override fun withPinnedPublicKey(sha256Key: String): DownloadClient =
        apply { super.withPinnedPublicKey(sha256Key) }

    override fun withDefaultTrust(): DownloadClient =
        apply { super.withDefaultTrust() }

    override fun withAnyTrust(): DownloadClient =
        apply { super.withAnyTrust() }

    override fun withPaths(vararg part: String): DownloadClient =
        apply { super.withPaths(*part) }

    override fun withQueryParams(
        vararg params: Pair<String, Any?>
    ): DownloadClient =
        apply { super.withQueryParams(*params) }

    /**
     * Starts a download to targetFile.
     *
     * An existing target is replaced only after the download succeeds.
     * An interrupted download is retained in targetFile.part for resuming.
     */
    suspend fun start(url: String, targetFile: File): Job {
        require(url.isNotBlank()) { "URL cannot be blank" }

        val job = synchronized(lock) {
            check(downloadJob?.isActive != true) {
                "A download is already active on this client"
            }

            cancelRequested = false
            pauseSignal = null
            setStateLocked(DownloadState.Running)

            downloadScope.launch(start = CoroutineStart.LAZY) {
                val partFile = targetFile.partFile()

                try {
                    withContext(Dispatchers.IO) {
                        targetFile.parentFile?.let {
                            if (!it.exists() && !it.mkdirs()) {
                                throw IOException("Could not create directory: $it")
                            }
                        }

                        listener.onStarted(targetFile)

                        // Metadata is best-effort; the actual GET determines the
                        // response status and the authoritative range information.
                        val remoteInfo = try {
                            currentCoroutineContext().ensureActive()
                            RemoteFileInformation()
                                .getRemoteFileInformationData(this@DownloadClient.usingUrl(url))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }

                        listener.onRemoteFileInfo(remoteInfo)

                        downloadToPartFile(url, partFile, remoteInfo?.fileSize)

                        currentCoroutineContext().ensureActive()
                        checkNotCanceled()

                        // Rename only after the part file has completed successfully.
                        if (targetFile.exists() && !targetFile.delete()) {
                            throw IOException("Could not replace target file: $targetFile")
                        }

                        if (!partFile.renameTo(targetFile)) {
                            throw IOException(
                                "Could not rename ${partFile.absolutePath} to ${targetFile.absolutePath}"
                            )
                        }
                    }

                    setState(DownloadState.Completed)
                    listener.onCompleted(targetFile)
                } catch (e: CancellationException) {
                    setState(DownloadState.Canceled)
                    listener.onCanceled(partFile)
                    throw e
                } catch (e: Exception) {
                    setState(DownloadState.Failed)
                    listener.onFailed(targetFile, e)
                    throw e
                } finally {
                    activeConnection?.disconnect()
                    activeConnection = null

                    synchronized(lock) {
                        pauseSignal?.complete(Unit)
                        pauseSignal = null
                        downloadJob = null
                    }
                }
            }.also {
                downloadJob = it
            }
        }

        job.start()
        return job
    }

    fun pause() {
        synchronized(lock) {
            if (state != DownloadState.Running || downloadJob?.isActive != true) {
                return
            }

            pauseSignal = CompletableDeferred()
            setStateLocked(DownloadState.Paused)
        }
    }

    fun resume() {
        synchronized(lock) {
            if (state != DownloadState.Paused) return

            pauseSignal?.complete(Unit)
            pauseSignal = null
            setStateLocked(DownloadState.Running)
        }
    }

    fun cancel() {
        val job = synchronized(lock) {
            cancelRequested = true
            pauseSignal?.complete(Unit)
            pauseSignal = null
            downloadJob
        }

        // Disconnecting helps unblock a pending network read.
        activeConnection?.disconnect()
        job?.cancel(CancellationException("Download canceled"))
    }

    /**
     * Cancels this client's active download and its internal scope.
     * Call when the DownloadClient will no longer be used.
     */
    fun close() {
        cancel()
        downloadScope.cancel()
    }

    private suspend fun downloadToPartFile(
        url: String,
        partFile: File,
        expectedTotalBytes: Long?
    ) {
        partFile.parentFile?.let {
            if (!it.exists() && !it.mkdirs()) {
                throw IOException("Could not create directory: $it")
            }
        }

        val existingBytes = if (partFile.isFile) partFile.length() else 0L
        val headers = if (existingBytes > 0L) {
            mapOf("Range" to "bytes=$existingBytes-")
        } else {
            emptyMap()
        }

        // This calls DefaultClient.openStream(), which calls BasicClient.asConnection().
        // TLS trust, public-key pinning, bearer auth and timeouts are therefore inherited.
        val connection = usingUrl(url).openStream(headers)
        activeConnection = connection

        try {
            val statusCode = connection.responseCode
            val append: Boolean
            val responseStart: Long
            val responseTotal: Long?

            when (statusCode) {
                HttpURLConnection.HTTP_OK -> {
                    // Server ignored Range (or this is a fresh download).
                    // Restart rather than append a full response to a partial file.
                    append = false
                    responseStart = 0L
                    responseTotal = connection.contentLengthLong
                        .takeIf { it >= 0L }
                }

                HttpURLConnection.HTTP_PARTIAL -> {
                    if (existingBytes <= 0L) {
                        throw IOException("Server returned 206 without a Range request")
                    }

                    val contentRange = connection.getHeaderField("Content-Range")
                        ?: throw IOException("206 response did not include Content-Range")

                    val parsed = parseContentRange(contentRange)
                        ?: throw IOException("Invalid Content-Range: $contentRange")

                    responseStart = parsed.first
                    responseTotal = parsed.second

                    if (responseStart != existingBytes) {
                        throw IOException(
                            "Range mismatch: requested byte $existingBytes, " +
                                    "server started at $responseStart"
                        )
                    }

                    append = true
                }

                else -> {
                    val errorBody = runCatching {
                        connection.errorStream?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()

                    throw IOException(
                        "Download failed with HTTP $statusCode" +
                                (errorBody?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
                    )
                }
            }

            val totalBytes = responseTotal
                ?: expectedTotalBytes?.takeIf { it >= 0L }

            if (append && totalBytes != null && existingBytes > totalBytes) {
                throw IOException(
                    "Partial file is larger than remote file ($existingBytes > $totalBytes)"
                )
            }

            var downloadedBytes = if (append) existingBytes else 0L
            val buffer = ByteArray(bufferSize)

            connection.inputStream.use { input ->
                FileOutputStream(partFile, append).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        checkNotCanceled()
                        awaitIfPaused()

                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) break
                        if (bytesRead == 0) continue

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val percent = totalBytes
                            ?.takeIf { it > 0L }
                            ?.let {
                                ((downloadedBytes.toDouble() / it) * 100.0)
                                    .toInt()
                                    .coerceIn(0, 100)
                            }

                        listener.onProgress(
                            downloadedBytes,
                            totalBytes ?: -1L,
                            percent
                        )
                    }

                    output.fd.sync()
                }
            }

            if (totalBytes != null && downloadedBytes != totalBytes) {
                throw IOException(
                    "Incomplete download: received $downloadedBytes of $totalBytes bytes"
                )
            }
        } finally {
            connection.disconnect()
            if (activeConnection === connection) {
                activeConnection = null
            }
        }
    }

    private suspend fun awaitIfPaused() {
        while (true) {
            val signal = synchronized(lock) {
                if (state == DownloadState.Paused) pauseSignal else null
            } ?: return

            signal.await()
            currentCoroutineContext().ensureActive()
            checkNotCanceled()
        }
    }

    private fun checkNotCanceled() {
        if (cancelRequested) {
            throw CancellationException("Download canceled")
        }
    }

    private fun setState(newState: DownloadState) {
        synchronized(lock) {
            setStateLocked(newState)
        }
    }

    private fun setStateLocked(newState: DownloadState) {
        state = newState
        listener.onStateChanged(newState)
    }

    /**
     * Parses "bytes START-END/TOTAL".
     * A total of "*" is allowed but means the total is unknown.
     */
    private fun parseContentRange(value: String): Pair<Long, Long?>? {
        val match = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""")
            .matchEntire(value.trim())
            ?: return null

        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull()

        if (end < start) return null
        if (total != null && end >= total) return null

        return start to total
    }

    private fun File.partFile(): File =
        File(parentFile, "$name.part")
}