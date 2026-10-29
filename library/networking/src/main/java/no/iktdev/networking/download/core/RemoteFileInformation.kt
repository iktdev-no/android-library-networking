package no.iktdev.networking.download.core

import android.os.Build
import android.util.Log
import no.iktdev.networking.rest.DefaultClient
import java.net.HttpURLConnection

class RemoteFileInformation {

    fun getRemoteFileInformationData(
        client: DefaultClient
    ): RemoteFileInformationData? {
        val connection = try {
            client.prepareConnection(DefaultClient.HttpMethods.HEAD)
        } catch (e: Exception) {
            return null
        }

        return try {
            val status = connection.responseCode

            if (status !in 200..299) {
                return null
            }

            val contentSize = getSizeFromConnection(connection)
                .takeIf { it > 0L }
                ?: getSizeFromHeader(connection)
                ?: 0L

            val supportsRanges = connection
                .getHeaderField("Accept-Ranges")
                ?.equals("bytes", ignoreCase = true) == true

            val contentFileName = getFileName(connection)
            val extension = contentFileName?.withoutExtension()

            RemoteFileInformationData(
                fileName = contentFileName,
                fileExtension = extension,
                fileSize = contentSize,
                uri = connection.url.toString(),
                supportsRanges = supportsRanges
            )
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
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
            connection.contentLength.toLong()
        } else
            if (!connection.contentLengthLong.isZeroOrNegative()) {
                connection.contentLengthLong
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
