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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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
        return File(myCacheDir, "$fileName.$suffix").also {
            if (it.exists()) {
                it.delete()
            }
            it.createNewFile()
        }
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
        return (((downloadedLength.toDouble()) / remoteLength) * 100).roundToInt()
    }


    fun start(url: String, fileName: String): Job {
        val job = scope.launch {
            var report: DownloadReportData? = null
            val downloadReport = getCachedFile("$fileName-report", "json").also {
                report = getDownloadReport(it)?.also { drd ->
                    downloadedBytes = drd.bytesReceived.toLong()
                }
                if (!it.exists()) {
                    it.createNewFile()
                }
            }

            remoteFileInformationData = RemoteFileInformation().getRemoteFileInformationData(url).also {
                eventListener?.onDownloadInformationObtained(it)
            }

            val client = Http.getHttpByUrl(url)

            if (report != null && downloadedBytes > 0 && report?.remoteLength == remoteFileInformationData?.fileSize) {
                Log.i(this::class.java.simpleName, "Found abandoned download, resuming")
                resumeAbandonedDownload(client, downloadReport)
            }
            var downloadStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            var tempFile: File? = null
            try {
                tempFile = getCachedFile(fileName, "temp").also { tmpFile ->
                    if (tmpFile.exists() && (report == null || downloadedBytes == 0L)) {
                        tmpFile.delete()
                    }
                    if (!tmpFile.exists()) {
                        tmpFile.createNewFile()
                    }
                }
                Log.i(this::class.simpleName, "Downloading into: ${tempFile.absolutePath}")
                client.http.connect()
                downloadStream = client.http.inputStream
                outputStream = FileOutputStream(tempFile, true)

                val buffer = ByteArray(bufferSize)
                var bytesRead: Int = 0

                withContext(Dispatchers.Main) {
                    eventListener?.onDownloadStarted(fileName, tempFile)
                }

                while (isActive && downloadStream.read(buffer).also { bytesRead = it } != -1) {
                    if (isPaused) {
                        delay(500)
                        continue
                    }
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

            } catch (e: Exception) {
                if (e is CancellationException) {
                    isCanceled = true
                    withContext(Dispatchers.Main) {
                        eventListener?.onDownloadCanceled(fileName, tempFile)
                    }
                } else {
                    e.printStackTrace()
                    isFailed = true
                    withContext(Dispatchers.Main) {
                        eventListener?.onDownloadFailed(fileName, tempFile)
                    }
                }
            } finally {
                downloadStream?.close()
                outputStream?.close()
                client.http.disconnect()
                if (!isFailed && !isCanceled && tempFile != null) {
                    withContext(Dispatchers.Main) {
                        eventListener?.onDownloadCompleted(fileName, tempFile)
                    }
                } else if (isCanceled) {
                    eventListener?.onDownloadCanceled(fileName, tempFile)
                } else {
                    eventListener?.onDownloadFailed(fileName, tempFile)
                    tempFile?.also { if (it.exists()) it.delete() }
                    downloadReport.also { if (it.exists()) it.delete() }
                }
            }
            writeReport(downloadReport, tempFile, remoteFileInformationData?.fileSize ?: -1)

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

    suspend fun directDownload(url: String, progress: (Int) -> Unit): ByteArray? {
        return scope.async {
            val client = Http.getHttpByUrl(url).also {
                it.http.connect()
            }
            var previousDownloadedProgress = 0
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
                    downloadedBytes += bytesRead

                    contentSize?.let { size ->
                        val downloadedProgress = (downloadedBytes.toDouble() / size).roundToInt()
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