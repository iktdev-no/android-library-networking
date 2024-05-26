package no.iktdev.networking.download

import java.io.File

interface DownloadEvents {
    fun onDownloadStarted(fileName: String) {}
    fun onDownloadProgress(fileName: String, progress: Int) {}
    fun onDownloadCompleted(fileName: String, file: File) {}
    fun onDownloadFailed(fileName: String) {}
}