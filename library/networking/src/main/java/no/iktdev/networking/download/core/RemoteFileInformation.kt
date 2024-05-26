package no.iktdev.networking.download.core

import android.os.Build
import android.util.Log
import no.iktdev.networking.client.Http
import no.iktdev.networking.client.isOk
import java.net.HttpURLConnection

class RemoteFileInformation {

    fun getRemoteFileInformationData(client: Http): RemoteFileInformationData? {
        if (!client.http.isOk()) {
            return null
        }
        val connection = client.http
        connection.requestMethod = "HEAD"

        connection.connect()

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
        val disposition = connection.getHeaderField("Content-Disposition")?.let {
            if (it.contains("filename=")) {
                it.split("filename=").firstOrNull()?.trim(' ', '"', ';')
            } else null
        }

        return disposition.ifNullOrEmpty {
            connection.url.path.let { it.substring(it.lastIndexOf("/")+1) }
        }
    }

    private fun getSizeFromHeader(connection: HttpURLConnection): Long? {
        return try {
            val headerValue = connection.getHeaderField("Content-Length")
            if (headerValue.isNullOrBlank()) {
                Log.w(this::class.simpleName, "Header value Content-Length is not present..")
                null
            } else Integer.parseInt(headerValue).toLong()
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