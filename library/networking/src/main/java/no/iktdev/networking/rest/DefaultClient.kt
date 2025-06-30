package no.iktdev.networking.rest

import android.annotation.SuppressLint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.iktdev.networking.UrlBuilder
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

open class DefaultClient {
    var url: String? = null
        private set
    fun usingUrl(url: String) = apply {
        this.url = url
    }

    var exceptionHandler: ((Exception) -> Unit)? = null
        private set
    fun setExceptionHandler(e : (Exception) -> Unit) = apply {
        this.exceptionHandler = e
    }

    private var timeout: Int = 5000
    fun withTimeout(timeout: Int) = apply {
        this.timeout = timeout
    }

    private var bearerToken: String? = null
    fun withBearerToken(token: String) = apply {
        this.bearerToken = token
    }

    private var disableTrust: Boolean = false
    fun withoutTrust() = apply {
        disableTrust = true
    }

    fun withTrust() = apply {
        disableTrust = false
    }


    private var contentType: String = "application/json"
    fun withContentType(contentType: String) = apply {
        this.contentType = contentType
    }

    private var paths: List<String> = listOf()
    fun withPaths(vararg part: String) = apply {
        this.paths = part.toList()
    }

    fun getUrl(): URL? {
        val baseUrl = url ?: return throw IllegalArgumentException("Base URL must be provided")
        return UrlBuilder(baseUrl).with(paths).asURL()
    }

    fun asConnection(url: URL): HttpURLConnection? {
        val connection = if (url.toString().startsWith("https")) {
            val conn = url.openConnection() as HttpsURLConnection
            conn.also {
                if (disableTrust) {
                    it.disableTrust()
                }
                if (!bearerToken.isNullOrEmpty()) {
                    it.setRequestProperty("Authorization", "Bearer $bearerToken")
                    it.instanceFollowRedirects = true
                }
            }
        } else {
            url.openConnection() as HttpURLConnection
        }
        connection.also {
            it.setRequestProperty("Content-Type", contentType)
        }
        return connection.apply {
            connectTimeout = timeout
            readTimeout = timeout
        }
    }

    fun Exception.toHttpResponse(): HttpResponse<Nothing> {
        return HttpResponse(
            status = -500,
            result = null,
            error = this.message,
            request = HttpRequest(
                address = this@DefaultClient.url!!,
                url = url.toString()
            )
        )
    }


    suspend inline fun <reified T> get(): HttpResponse<T> = withContext(Dispatchers.IO) {
        val url = getUrl() ?: return@withContext HttpResponse(-201, null, "Failed to create url!",
            HttpRequest(
                address = this@DefaultClient.url ?: "Not set",
                url = "Not set"
            )
        )
        val connection = asConnection(url) ?: return@withContext HttpResponse(-1, null, "Failed to create HTTP connection",
            HttpRequest(
                address = this@DefaultClient.url ?: "Not set",
                url = url.toString()
            )
        )
        connection.requestMethod = "GET"
        try {
            connection.tryConnect()
            connection.read()
        } catch (e: ReadObjectException) {
            return@withContext e.httpResponse.toTypeParameter()
        } catch (e: HttpConnectionException) {
            return@withContext e.httpResponse.toTypeParameter()
        } catch (e: Exception) {
            exceptionHandler?.invoke(e) ?: e.printStackTrace()
            return@withContext e.toHttpResponse().toTypeParameter()
        }
    }

    suspend inline fun <reified T> post(data: Any): HttpResponse<T> = withContext(Dispatchers.IO) {
        val url = getUrl() ?: return@withContext HttpResponse(-1, null, "Failed to create url!",
            HttpRequest(
                address = this@DefaultClient.url ?: "Not set",
                url = "Not set"
            )
        )
        val connection = asConnection(url) ?: return@withContext HttpResponse(0, null, "Failed to create HTTP connection",
            HttpRequest(
                address = this@DefaultClient.url ?: "Not set",
                url = url.toString()
            )
        )
        connection.requestMethod = "POST"
        connection.doOutput = true
        try {
            connection.tryConnect()
            val dos = DataOutputStream(connection.outputStream)
            val payload = Gson().toJson(data)
            dos.writeBytes(payload)
            dos.flush()
            connection.read()
        } catch (e: ReadObjectException) {
            return@withContext e.httpResponse.toTypeParameter()
        } catch (e: HttpConnectionException) {
            return@withContext e.httpResponse.toTypeParameter()
        } catch (e: Exception) {
            exceptionHandler?.invoke(e) ?: e.printStackTrace()
            return@withContext e.toHttpResponse().toTypeParameter()
        }
    }

    suspend inline fun <reified T> put(data: Any): HttpResponse<T> = withContext(Dispatchers.IO) {
        val url = getUrl() ?: return@withContext HttpResponse(-1, null, "Failed to create url!",
            HttpRequest(
                address = this@DefaultClient.url ?: "Not set",
                url = "Not set"
            )
        )
        val connection = asConnection(url) ?: return@withContext HttpResponse(0, null, "Failed to create HTTP connection",
            HttpRequest(
                address = this@DefaultClient.url ?: "Not set",
                url = url.toString()
            )
        )
        connection.requestMethod = "PUT"
        connection.doOutput = true
        try {
            connection.tryConnect()
            val dos = DataOutputStream(connection.outputStream)
            val payload = Gson().toJson(data)
            dos.writeBytes(payload)
            dos.flush()
            connection.read()
        } catch (e: ReadObjectException) {
            return@withContext e.httpResponse.toTypeParameter()
        } catch (e: HttpConnectionException) {
            return@withContext e.httpResponse.toTypeParameter()
        } catch (e: Exception) {
            exceptionHandler?.invoke(e) ?: e.printStackTrace()
            return@withContext e.toHttpResponse().toTypeParameter()
        }
    }

    suspend inline fun <reified T> delete(data: Any? = null): HttpResponse<T> = withContext(Dispatchers.IO) {
        val url = getUrl() ?: return@withContext HttpResponse(-1, null, "Failed to create url!",
            HttpRequest(
                address = this@DefaultClient.url ?: "Not set",
                url = "Not set"
            )
        )
        val connection = asConnection(url) ?: return@withContext HttpResponse(0, null, "Failed to create HTTP connection",
            HttpRequest(
                address = this@DefaultClient.url ?: "Not set",
                url = url.toString()
            )
        )
        connection.requestMethod = "DELETE"
        connection.doOutput = true
        try {
            connection.tryConnect()
            if (data != null) {
                val dos = DataOutputStream(connection.outputStream)
                val payload = Gson().toJson(data)
                dos.writeBytes(payload)
                dos.flush()
            }
            connection.read()
        } catch (e: ReadObjectException) {
            return@withContext e.httpResponse.toTypeParameter()
        } catch (e: HttpConnectionException) {
            return@withContext e.httpResponse.toTypeParameter()
        } catch (e: Exception) {
            exceptionHandler?.invoke(e) ?: e.printStackTrace()
            return@withContext e.toHttpResponse().toTypeParameter()
        }
    }

    fun HttpURLConnection.tryConnect() {
        try {
            this.connect()
        } catch (e: Exception) {
            val response = HttpResponse(
                status = -500,
                result = null,
                error = e.message,
                request = HttpRequest(
                    address = this@DefaultClient.url!!,
                    url = this.url.toString()
                )
            )
            throw HttpConnectionException(
                httpResponse = response,
                message = "Failed to connect using tryConnect ${e.message}"
            )
        }
    }

    inline fun <reified T> HttpResponse<Nothing>.toTypeParameter(): HttpResponse<T> {
        return HttpResponse(
            status = this.status,
            result = this.result,
            error = this.error,
            request = this.request
        )
    }

    class HttpConnectionException(
        val httpResponse: HttpResponse<Nothing>,
        override val message: String?
    ) : Exception()

    inline fun <reified T> HttpURLConnection.read(): HttpResponse<T> {
        return try {
            val content = inputStream.bufferedReader().use { it.readText() }

            val result: T? = when (T::class) {
                String::class -> content as T
                Int::class -> content.toIntOrNull() as T?
                Long::class -> content.toLongOrNull() as T?
                Double::class -> content.toDoubleOrNull() as T?
                Float::class -> content.toFloatOrNull() as T?
                Boolean::class -> content.toBooleanStrictOrNull() as T?
                else -> {
                    val type = object : TypeToken<T>() {}.type
                    Gson().fromJson<T>(content, type)
                }
            }
            HttpResponse(this.responseCode, result, null,
                HttpRequest(
                    address = this@DefaultClient.url!!,
                    url = this.url.toString()
                )
            )
        } catch (e: Exception) {
            val className = try {
                T::class.java.simpleName ?: T::class.simpleName ?: "[Unable to get name]"
            } catch (_: Exception) {
                "[Unable to get name]"
            }
            e.printStackTrace()
            val responseObject = HttpResponse(
                status = -400,
                result = null,
                error = e.message,
                HttpRequest(
                    address = this@DefaultClient.url!!,
                    url = url.toString())
            )
            throw ReadObjectException(
                httpResponse = responseObject,
                "Decode to $className failed for content: $content")
        }
    }

    class ReadObjectException(
        val httpResponse: HttpResponse<Nothing>,
        override val message: String?): Exception()

    data class HttpResponse<T>(
        val status: Int,
        val result: T?,
        val error: String? = null,
        val request: HttpRequest
    )
    data class HttpRequest(
        val address: String,
        val url: String
    )



    fun HttpsURLConnection.disableTrust() = apply {
        this.hostnameVerifier = HostnameVerifier { _, _ -> true }

        val truster = arrayOf<TrustManager>(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                @SuppressLint("TrustAllX509TrustManager")
                @Throws(CertificateException::class)
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                    // Do trust
                }

                @SuppressLint("TrustAllX509TrustManager")
                @Throws(CertificateException::class)
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    // Do trust
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf()
                }
            }
        )

        this.sslSocketFactory = SSLContext.getInstance("TLS").apply {
            init(null, truster, SecureRandom())
        }.socketFactory

    }
}