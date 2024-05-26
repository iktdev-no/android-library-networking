package no.iktdev.networking.download.core

data class RemoteFileInformationData(
    val fileName: String? = null,
    val fileExtension: String? = null,
    val fileSize: Long = 0,
    val uri: String
)