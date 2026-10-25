package no.iktdev.networking.download.core

import android.os.Build
import android.util.Log
import no.iktdev.networking.client.Http
import java.net.HttpURLConnection

class RemoteFileInformation {

    fun getRemoteFileInformationData(url: String): RemoteFileInformationData? {
        val client = Http.getHttpByUrl(url)
        val connection = client.http
        connection.requestMethod = "HEAD"

        connection.connect()
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            return null
        }

        val contentSize = getSizeFromConnection(connection).ifZeroOrNegative { getSizeFromHeader(connection) ?: 0 }
        val contentFileName = getFileName(connection)
        val extension = contentFileName?.withoutExtension()

        connection.disconnect()

        return RemoteFileInformationData(
            fileName = contentFileName,
            fileExtension = extension,
            fileSize = contentSize,
            uri = connection.url.toString()
        )
    }


    private fun getFileName(connection: HttpURLConnection): String? {
        val disposition = connection.getHeaderField("Content-Disposition")?.let { header ->
            val parameters = header.split(';').map { it.trim() }
            val encoded = parameters.firstOrNull { it.startsWith("filename*=", ignoreCase = true) }
                ?.substringAfter('=')?.trim('"')?.substringAfter("''", missingDelimiterValue = "")
            val filename = parameters.firstOrNull { it.startsWith("filename=", ignoreCase = true) }
                ?.substringAfter('=')?.trim().orEmpty().trim('"')
            encoded?.takeIf(String::isNotEmpty)
                ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }
                ?: filename.takeIf(String::isNotEmpty)
        }

        return disposition.ifNullOrEmpty {
            connection.url.path.let { it.substring(it.lastIndexOf("/")+1) }
        }
    }

    fun getSizeFromHeader(connection: HttpURLConnection): Long? {
        return try {
            val headerValue = connection.getHeaderField("Content-Length")
            if (headerValue.isNullOrBlank()) {
                Log.w(this::class.simpleName, "Header value Content-Length is not present..")
                null
            } else headerValue.toLong()
        } catch (e: Exception) {
            null
        }
    }

    private fun getSizeFromConnection(connection: HttpURLConnection): Long {
        return if (!connection.contentLength.isZeroOrNegative()) {
            return connection.contentLength.toLong()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!connection.contentLengthLong.isZeroOrNegative()) {
                return connection.contentLengthLong
            } else 0L
        } else 0L

    }


    private fun Int?.isZeroOrNegative(): Boolean {
        return this == null || this <= 0
    }

    private fun Long?.isZeroOrNegative(): Boolean {
        return this == null || this <= 0L
    }

    private fun Long.ifZeroOrNegative(block: (Long) -> Long): Long {
        return if (this.isZeroOrNegative()) {
            block(this)
        } else this
    }

    private fun String?.ifNullOrEmpty(block: (String?) -> String?): String? {
        return if (this.isNullOrEmpty()) {
            block(this)
        } else {
            this
        }
    }
    fun String.withoutExtension(): String {
        return try {
            val lastIndexOfDot = this.lastIndexOf(".") ?: -1
            if (lastIndexOfDot < 0) {
                return this
            } else {
                this.substring(0, lastIndexOfDot)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            this
        }
    }
}
