package no.iktdev.networking.download.core

data class DownloadReportData(
    val outFile: String?, // AbsolutePath
    var bytesReceived: Long,
    val remoteLength: Long
)
