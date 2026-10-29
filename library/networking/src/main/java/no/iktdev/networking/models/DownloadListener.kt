package no.iktdev.networking.models

import no.iktdev.networking.download.core.RemoteFileInformationData
import java.io.File

/**
 * Events emitted by DownloadClient.
 * Override only the callbacks you need.
 */
interface DownloadListener {
    fun onStarted(targetFile: File) {}
    fun onRemoteFileInfo(remoteFileInformation: RemoteFileInformationData?) {}
    fun onProgress(downloadedBytes: Long, totalBytes: Long, percent: Int?) {}
    fun onCompleted(targetFile: File) {}
    fun onCanceled(partFile: File) {}
    fun onFailed(targetFile: File, error: Throwable) {}
    fun onStateChanged(state: DownloadState) {}
}