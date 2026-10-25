@file:OptIn(ExperimentalStdlibApi::class)

package no.iktdev.networking.download

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import no.iktdev.networking.client.Http
import no.iktdev.networking.download.core.DownloadReportData
import no.iktdev.networking.download.core.RemoteFileInformation
import no.iktdev.networking.download.core.RemoteFileInformationData
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import kotlin.math.roundToInt

/**
 * @param cacheDir where it can be internal cache or external
 */
class DownloadClient(val context: Context, val scope: CoroutineScope, val eventListener: DownloadEvents? = null) {
    var downloadClientCacheDirName = "iktdev_download_cache"
    private var downloadJob: Job? = null
    private var isPaused = false
    private var downloadedBytes = 0L
    val bufferSize = 8 * 1024
    private var isFailed = false
    private var isCanceled = false

    private var remoteFileInformationData: RemoteFileInformationData? = null
    private var cachedProgress: Int = 0

    var cacheDirectory: File? = null

    init {
        require(scope.coroutineContext[CoroutineDispatcher.Key] == Dispatchers.IO) {
            "DownloadManager requires a CoroutineScope with Dispatchers.IO"
        }
    }

    private fun getCachedFile(fileName: String, suffix: String): File {
        val cacheDir = if (cacheDirectory == null) { context.cacheDir } else cacheDirectory

        val myCacheDir = File(cacheDir, downloadClientCacheDirName).also {
            if (!it.exists()) {
                it.mkdir()
            }
        }
        return File(myCacheDir, "$fileName.$suffix")
    }


    private fun resumeAbandonedDownload(client: Http, report: File) {
        client.http.setRequestProperty("Range", "bytes=$downloadedBytes-")
    }

    private fun getDownloadReport(reportFile: File): DownloadReportData? {
        return if (reportFile.exists()) {
            try {
                Gson().fromJson(reportFile.readText(), DownloadReportData::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                reportFile.delete()
                null
            }
        } else null
    }

    fun getProgress(downloadedLength: Long, remoteLength: Long): Int {
        if (remoteLength <= 0L) return 0
        return (((downloadedLength.toDouble()) / remoteLength) * 100).roundToInt().coerceIn(0, 100)
    }


    @Synchronized
    fun start(url: String, fileName: String): Job {
        check(downloadJob?.isActive != true) { "A download is already active on this client" }
        downloadedBytes = 0L
        isPaused = false
        isFailed = false
        isCanceled = false
        cachedProgress = 0
        remoteFileInformationData = null
        val job = scope.launch {
            var report: DownloadReportData? = null
            val downloadReport = getCachedFile("$fileName-report", "json").also {
                report = getDownloadReport(it)?.also { drd ->
                    downloadedBytes = drd.bytesReceived.toLong()
                }
            }

            remoteFileInformationData = runCatching {
                RemoteFileInformation().getRemoteFileInformationData(url)
            }.getOrNull().also {
                eventListener?.onDownloadInformationObtained(it)
            }

            val client = Http.getHttpByUrl(url)

            var canResume = false
            var downloadStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            var tempFile: File? = null
            try {
                tempFile = getCachedFile(fileName, "temp").also { tmpFile ->
                    canResume = report != null && downloadedBytes > 0L &&
                        report?.remoteLength == remoteFileInformationData?.fileSize &&
                        tmpFile.isFile && tmpFile.length() == downloadedBytes
                    if (!canResume) {
                        downloadedBytes = 0L
                        tmpFile.delete()
                        downloadReport.delete()
                    }
                    tmpFile.parentFile?.mkdirs()
                    if (!tmpFile.exists()) tmpFile.createNewFile()
                }
                if (canResume) {
                    Log.i(this::class.java.simpleName, "Found abandoned download, resuming")
                    resumeAbandonedDownload(client, downloadReport)
                }
                Log.i(this::class.simpleName, "Downloading into: ${tempFile.absolutePath}")
                client.http.connect()
                if (canResume && client.http.responseCode != 206) {
                    // Some servers ignore Range. Restart from byte zero instead of appending
                    // a full response to the partial file.
                    downloadedBytes = 0L
                    FileOutputStream(tempFile, false).close()
                    canResume = false
                }
                downloadStream = client.http.inputStream
                outputStream = FileOutputStream(tempFile, canResume)

                val buffer = ByteArray(bufferSize)
                var bytesRead: Int = 0

                withContext(Dispatchers.Main) {
                    eventListener?.onDownloadStarted(fileName, tempFile)
                }

                while (isActive) {
                    if (isPaused) {
                        delay(500)
                        continue
                    }
                    bytesRead = downloadStream.read(buffer)
                    if (bytesRead == -1) break
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    // Log.d(this::class.simpleName, "$downloadedBytes/${remoteFileInformationData?.fileSize ?: 0} (Downloaded/Remote) ")

                    val progress = getProgress(downloadedBytes, remoteFileInformationData?.fileSize ?: 0)
                    if (progress > cachedProgress) {
                       // Log.d(this::class.simpleName, "Downloaded $progress% of $fileName")
                        cachedProgress = progress
                        withContext(Dispatchers.Main) {
                            eventListener?.onDownloadProgress(fileName, progress)
                        }
                    }
                    yield()
                }
                if (!isActive) throw CancellationException()
                val expectedBytes = remoteFileInformationData?.fileSize ?: 0L
                if (expectedBytes > 0L && downloadedBytes < expectedBytes) {
                    throw EOFException("Download ended at $downloadedBytes of $expectedBytes bytes")
                }

            } catch (e: Exception) {
                if (e is CancellationException) {
                    isCanceled = true
                } else {
                    e.printStackTrace()
                    isFailed = true
                }
            } finally {
                withContext(NonCancellable) {
                    downloadStream?.close()
                    outputStream?.close()
                    client.http.disconnect()
                    if (!isFailed && !isCanceled && tempFile != null) {
                        withContext(Dispatchers.Main) {
                            eventListener?.onDownloadCompleted(fileName, tempFile)
                        }
                    } else if (isCanceled) {
                        withContext(Dispatchers.Main) {
                            eventListener?.onDownloadCanceled(fileName, tempFile)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            eventListener?.onDownloadFailed(fileName, tempFile)
                        }
                    }
                    if (isFailed) {
                        tempFile?.also { if (it.exists()) it.delete() }
                        downloadReport.also { if (it.exists()) it.delete() }
                    }
                }
            }
            if (isCanceled && tempFile != null) {
                writeReport(downloadReport, tempFile, remoteFileInformationData?.fileSize ?: -1)
            } else if (!isFailed) {
                downloadReport.delete()
            }

        }.also { downloadJob = it }
        return job
    }

    private fun writeReport(reportFile: File, outFile: File?, remoteLength: Long) {
        val report = DownloadReportData(
            outFile = outFile?.absolutePath,
            bytesReceived = this.downloadedBytes,
            remoteLength = remoteLength
        )
        reportFile.writeText(Gson().toJson(report))
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun cancel() {
        downloadJob?.cancel()
        downloadJob = null
    }

    suspend fun directDownload(url: URL, progress: (Int) -> Unit = {}): ByteArray? {
        return directDownload(url.toString(), progress)
    }


    suspend fun directDownload(url: String, progress: (Int) -> Unit = {}): ByteArray? {
        Log.i(this::class.simpleName, "directDownload url: $url")
        if (url.isNullOrBlank()) {
            throw RuntimeException("Provided url is null or blank!")
        } else if (!url.contains("http")) {
            throw RuntimeException("Provided url does not contain http or https: $url")
        }
        return scope.async {
            val client = Http.getHttpByUrl(url).also {
                it.http.connect()
            }
            var previousDownloadedProgress = 0
            var currentDownloadedBytes = 0L
            val contentSize = RemoteFileInformation().getSizeFromHeader(client.http)
            var inputStream: InputStream? = null
            var outputStream: ByteArrayOutputStream? = null

            try {
                inputStream = client.http.inputStream
                outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(bufferSize)
                var bytesRead: Int = 0

                while (isActive && inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    currentDownloadedBytes += bytesRead

                    contentSize?.let { size ->
                        val downloadedProgress = ((currentDownloadedBytes.toDouble() / size) * 100).roundToInt().coerceIn(0, 100)
                        if (downloadedProgress > previousDownloadedProgress) {
                            withContext(Dispatchers.Main) {
                                progress(downloadedProgress)
                            }
                            previousDownloadedProgress = downloadedProgress
                        }
                    }


                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                client.http.disconnect()
                inputStream?.close()
                outputStream?.close()
            }
            outputStream?.toByteArray()
        }.await()
    }


}
