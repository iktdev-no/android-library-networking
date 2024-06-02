package no.iktdev.networking.download

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
class DownloadClient(val eventListener: DownloadEvents? = null) {
    private var downloadJob: Job? = null
    private var isPaused = false
    private var downloadedBytes = 0L
    val bufferSize = 8 * 1024
    private var isFailed = false

    private var remoteFileInformationData: RemoteFileInformationData? = null
    private var cachedProgress: Int = 0

    var cacheDirectory: File? = null
    private fun getCachedFile(fileName: String, suffix: String): File {
        val cachedDirector = this.cacheDirectory
        return if (cachedDirector != null && cachedDirector.exists()) {
            File.createTempFile(fileName, suffix, cachedDirector)
        } else {
            File.createTempFile(fileName, suffix)
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
        val job = CoroutineScope(Dispatchers.IO + Job()).launch {
            var report: DownloadReportData? = null
            val downloadReport = getCachedFile("$fileName-report", ".json").also {
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
                tempFile = getCachedFile(fileName, ".temp").also { tmpFile ->
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
                }

            } catch (e: Exception) {
                e.printStackTrace()
                isFailed = true
                withContext(Dispatchers.Main) {
                    eventListener?.onDownloadFailed(fileName, tempFile)
                }
            } finally {
                downloadStream?.close()
                outputStream?.close()
                client.http.disconnect()
                if (!isFailed && tempFile != null) {
                    withContext(Dispatchers.Main) {
                        eventListener?.onDownloadCompleted(fileName, tempFile)
                    }
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

    suspend fun directDownload(url: String, progress: (Int) -> Unit): ByteArray? = withContext(Dispatchers.IO) {
        this.async {
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

            } finally {
                client.http.disconnect()
                inputStream?.close()
                outputStream?.close()
            }
            outputStream?.toByteArray()
        }.await()
    }


}