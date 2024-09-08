package no.iktdev.networking.download

import no.iktdev.networking.download.core.RemoteFileInformation
import no.iktdev.networking.download.core.RemoteFileInformationData
import java.io.File

interface DownloadEvents {
    fun onDownloadStarted(fileName: String, file: File) {}
    fun onDownloadInformationObtained(remoteFileInformation: RemoteFileInformationData?) {}
    fun onDownloadProgress(fileName: String, progress: Int) {}
    fun onDownloadCompleted(fileName: String, file: File) {}
    fun onDownloadFailed(fileName: String, file: File?) {}
    fun onDownloadCanceled(fileName: String, file: File?) {}
}