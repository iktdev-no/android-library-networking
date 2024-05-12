package no.iktdev.networking.download

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.iktdev.networking.client.Http
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import kotlin.math.roundToInt


data class RemoteFileInfo(
    val fileName: String? = null,
    val fileExtension: String? = null,
    val fileSize: Double = -1.0
)

class DownloadClient(val http: Http) {
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val _progress = MutableLiveData<Int>(0)
    val progress: LiveData<Int> = _progress

    private val _state = MutableLiveData(DownloadState.NotStarted)
    val state: LiveData<DownloadState> = _state


    private val BUFFER_SIZE = 4096
    private var rfi: RemoteFileInfo? = null
    fun getRemoteFileInfo(): RemoteFileInfo? {
        if (http.http.responseCode != HttpURLConnection.HTTP_OK)
            return null

        val conn = http.http
        val _disp: String = conn.getHeaderField("Content-Disposition")

        val size = if (!conn.getHeaderField("content-length").isNullOrEmpty())
            conn.getHeaderField("content-length").toDouble() else
            conn.contentLength.toDouble()

        Log.d(
            javaClass.name,
            "Method => " + (conn.contentLength
                .toString() + " :: " + conn.getHeaderField("content-length") + " <= Parsed").toString()
        )

        val fileName = if (_disp != null && _disp.indexOf("filename=") > 0) _disp.substring(
            _disp.indexOf("filename=") + 10,
            _disp.length - 1
        ) else http.url.toString().let { url -> url.substring(url.toString().lastIndexOf("/") + 1) }

        val extension = if (conn.contentType != null && conn.contentType
                .contains("/")
        ) conn.contentType.split("/".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray().get(1) else  http.url.toString().let { url -> url.substring(url.toString().lastIndexOf(".") + 1) }
        return RemoteFileInfo(
            fileName = fileName,
            fileExtension = extension,
            fileSize = size
        )
    }

    init {
        rfi = getRemoteFileInfo()
    }

    suspend fun download(outFile: File? = null): File? {
        val http = http.http
        val rfi = rfi ?: return null

        val target = outFile ?: withContext(Dispatchers.IO) {
            File.createTempFile(rfi.fileName ?: "target", rfi.fileExtension)
        }

        scope.run {

            _state.postValue(DownloadState.Started)
            val inputStream = http.inputStream
            val fos = FileOutputStream(target, false)

            var totalBytesRead = 0
            val buffer = ByteArray(BUFFER_SIZE)
            inputStream.apply {
                fos.use { fout ->
                    run {
                        _state.postValue(DownloadState.Started)
                        var bytesRead = read(buffer)
                        while (bytesRead >= 0) {
                            fout.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            bytesRead = read(buffer)
                            sendProgress(totalBytesRead)
                            // System.out.println(getProgress(totalBytesRead))
                        }
                    }
                }
            }
            inputStream.close()
            fos.close()
            _state.postValue(DownloadState.Completed)
        }
        return target
    }

    private var currentProgress = 0
    private fun sendProgress(totalBytesRead: Int) {
        val progress = (totalBytesRead * 100 / (rfi?.fileSize ?: 0.0) * 100).roundToInt()
        if (progress > currentProgress) currentProgress = progress else return
        _progress.postValue(currentProgress)
    }

    enum class DownloadState {
        NotStarted,
        ReadingFileInfo,
        Started,
        Completed,
        Failed
    }

}