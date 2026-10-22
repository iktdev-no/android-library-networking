package no.iktdev.networking.rest

import android.annotation.SuppressLint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.iktdev.networking.UrlBuilder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Coroutine-based HTTP client. HTTP error statuses are returned in [HttpResponse.status]; failures
 * inside the client are thrown as [NetworkingException] subclasses.
 */
open class DefaultClient {
    var url: String? = null
        private set
    /** Sets the base URL used to create requests. */
    fun usingUrl(url: String) = apply {
        this.url = url
    }

    var exceptionHandler: ((Exception) -> Unit)? = null
        private set
    /** Registers a callback invoked before a [NetworkingException] is rethrown. */
    fun setExceptionHandler(e : (Exception) -> Unit) = apply {
        this.exceptionHandler = e
    }

    private var timeout: Int = 5000
    /** Sets connect and read timeouts, in milliseconds. */
    fun withTimeout(timeout: Int) = apply {
        this.timeout = timeout
    }

    private var bearerToken: String? = null
    /** Adds an `Authorization: Bearer …` header to requests. */
    fun withBearerToken(token: String) = apply {
        this.bearerToken = token
    }

    private var disableTrust: Boolean = false
    /** Disables TLS certificate and hostname checks. Use only for controlled development setups. */
    fun withoutTrust() = apply {
        disableTrust = true
    }

    /** Restores TLS certificate and hostname verification. */
    fun withTrust() = apply {
        disableTrust = false
    }


    private var contentType: String = "application/json"
    /** Sets the `Content-Type` request header. */
    fun withContentType(contentType: String) = apply {
        this.contentType = contentType
    }

    private var paths: List<String> = listOf()
    /** Sets the path for the next request and clears query parameters from the previous path. */
    fun withPaths(vararg part: String) = apply {
        this.paths = part.toList()
        this.queryParams = emptyList()
    }

    private var queryParams: List<String> = emptyList()

    /** Sets encoded query parameters for the current path. Call after [withPaths]; null values are omitted. */
    fun withQueryParams(vararg params: Pair<String, Any?>) = apply {
        this.queryParams = params.mapNotNull { (name, value) ->
            value?.let { encodeQueryParam(name, it) }
        }
    }

    /** Adds one encoded query parameter. Call repeatedly when a key needs multiple values. */
    fun withQueryParam(name: String, value: Any?) = apply {
        value?.let { this.queryParams = this.queryParams + encodeQueryParam(name, it) }
    }

    private fun encodeQueryParam(name: String, value: Any): String {
        val encodedName = URLEncoder.encode(name, Charsets.UTF_8.name())
        val encodedValue = URLEncoder.encode(value.toString(), Charsets.UTF_8.name())
        return "$encodedName=$encodedValue"
    }

    /**
     * Builds the current request URL.
     *
     * @throws IllegalArgumentException if no base URL has been configured.
     * @throws MalformedURLException if the configured URL cannot be parsed.
     */
    @Throws(IllegalArgumentException::class, MalformedURLException::class)
    fun getUrl(): URL? {
        val baseUrl = url ?: throw IllegalArgumentException("Base URL must be provided")
        val pathUrl = UrlBuilder(baseUrl).with(paths).asURL()
        return if (queryParams.isEmpty()) pathUrl else URL("${pathUrl}?${queryParams.joinToString("&")}")
    }

    /**
     * Creates a configured connection for [url].
     *
     * @throws IOException if the URL handler cannot open a connection.
     */
    @Throws(IOException::class)
    fun asConnection(url: URL): HttpURLConnection {
        val connection = if (url.toString().startsWith("https")) {
            val conn = url.openConnection() as HttpsURLConnection
            conn.also {
                if (disableTrust) {
                    it.disableTrust()
                }
            }
        } else {
            url.openConnection() as HttpURLConnection
        }
        connection.also {
            it.setRequestProperty("Content-Type", contentType)
            if (!bearerToken.isNullOrEmpty()) {
                it.setRequestProperty("Authorization", "Bearer $bearerToken")
            }
        }
        return connection.apply {
            connectTimeout = timeout
            readTimeout = timeout
        }
    }

    /** Base type for failures produced by the networking client itself. */
    open class NetworkingException(
        override val message: String,
        val request: HttpRequest? = null,
        cause: Throwable? = null
    ) : Exception(message, cause)

    /** Thrown when a request URL or its connection cannot be constructed or configured. */
    class HttpConnectionConstructionException(
        message: String,
        request: HttpRequest? = null,
        cause: Throwable? = null
    ) : NetworkingException(message, request, cause)

    /** Thrown when connecting to a server or reading a successful response fails. */
    class HttpConnectionException(
        message: String,
        request: HttpRequest? = null,
        cause: Throwable? = null
    ) : NetworkingException(message, request, cause)

    /** Thrown when a request payload cannot be serialized. */
    class RequestSerializationException(
        message: String,
        cause: Throwable? = null
    ) : NetworkingException(message, cause = cause)

    /** Thrown when the serialized request payload cannot be written to the connection. */
    class RequestWriteException(
        message: String,
        request: HttpRequest,
        cause: Throwable? = null
    ) : NetworkingException(message, request, cause)

    /** Thrown when a successful HTTP response body cannot be decoded as the requested type. */
    class ReadObjectException(
        val responseStatus: Int,
        val responseBody: String,
        request: HttpRequest,
        message: String,
        cause: Throwable? = null
    ) : NetworkingException(message, request, cause)

    /**
     * Prepares and connects an HTTP request.
     *
     * @throws HttpConnectionConstructionException if URL or connection setup fails.
     * @throws HttpConnectionException if connecting fails.
     */
    @Throws(HttpConnectionConstructionException::class, HttpConnectionException::class)
    fun prepareConnection(method: HttpMethods, doOutput: Boolean = false): HttpURLConnection {
        val requestUrl = try {
            getUrl() ?: throw IllegalArgumentException("Failed to create request URL")
        } catch (e: Exception) {
            throw HttpConnectionConstructionException(
                message = "Failed to create request URL: ${e.message}",
                request = HttpRequest(this@DefaultClient.url ?: "Not set", "Not set"),
                cause = e
            )
        }
        val request = HttpRequest(this@DefaultClient.url ?: "Not set", requestUrl.toString())
        val connection = try {
            asConnection(requestUrl)
        } catch (e: Exception) {
            throw HttpConnectionConstructionException(
                message = "Failed to create HTTP connection: ${e.message}",
                request = request,
                cause = e
            )
        }

        try {
            connection.requestMethod = method.name
            connection.doOutput = doOutput
            connection.tryConnect()
        } catch (e: NetworkingException) {
            throw e
        } catch (e: Exception) {
            throw HttpConnectionConstructionException(
                message = "Failed to configure HTTP connection: ${e.message}",
                request = request,
                cause = e
            )
        }
        return connection
    }

    /** Serializes a request payload as JSON (or returns supported scalar values as text). */
    fun preparePayload(data: Any?): String {
        return when (data) {
            is String, Int, Long, Float, Boolean, Char -> data.toString()
            else -> Gson().toJson(data)
        }
    }

    /** Writes a request body using UTF-8. */
    @Throws(IOException::class)
    fun sendPayload(connection: HttpURLConnection, payload: String?) {
        if (payload == null) return
        connection.outputStream.use { output ->
            output.write(payload.toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * Executes an HTTP request. HTTP statuses outside 200–299 are returned normally in
     * [HttpResponse.status]; failures in this client are thrown.
     *
     * @throws HttpConnectionConstructionException if the URL or connection cannot be prepared.
     * @throws HttpConnectionException if connecting or reading the response fails.
     * @throws RequestSerializationException if [data] cannot be serialized.
     * @throws RequestWriteException if [data] cannot be sent.
     * @throws ReadObjectException if a successful response cannot be decoded as [T].
     * @throws CancellationException if the coroutine is cancelled.
     */
    @Throws(
        HttpConnectionConstructionException::class,
        HttpConnectionException::class,
        RequestSerializationException::class,
        RequestWriteException::class,
        ReadObjectException::class,
        CancellationException::class
    )
    suspend inline fun <reified T> executeRequest(
        method: HttpMethods,
        data: Any? = null
    ): HttpResponse<T> = withContext(Dispatchers.IO) {
        try {
            val connection = prepareConnection(method, doOutput = data != null)
            if (data != null && method in listOf(HttpMethods.POST, HttpMethods.DELETE, HttpMethods.PUT, HttpMethods.PATCH)) {
                val payload = try {
                    preparePayload(data)
                } catch (e: Exception) {
                    throw RequestSerializationException("Failed to serialize request body", e)
                }
                try {
                    sendPayload(connection, payload)
                } catch (e: Exception) {
                    throw RequestWriteException(
                        message = "Failed to write request body: ${e.message}",
                        request = HttpRequest(url ?: "Not set", connection.url.toString()),
                        cause = e
                    )
                }
            }
            connection.read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkingException) {
            exceptionHandler?.invoke(e)
            throw e
        } catch (e: Exception) {
            exceptionHandler?.invoke(e)
            val requestUrl = runCatching { getUrl()?.toString() }.getOrNull() ?: "Not set"
            throw HttpConnectionException(
                message = "Unexpected networking client failure: ${e.message}",
                request = HttpRequest(url ?: "Not set", requestUrl),
                cause = e
            )
        }
    }

    enum class HttpMethods {
        GET,
        POST,
        PUT,
        PATCH,
        DELETE
    }

    /**
     * Sends a GET request. Non-2xx HTTP statuses are returned in [HttpResponse.status].
     *
     * @throws HttpConnectionConstructionException if the URL or connection cannot be prepared.
     * @throws HttpConnectionException if connecting or reading the response fails.
     * @throws ReadObjectException if the successful response body cannot be decoded as [T].
     * @throws CancellationException if the coroutine is cancelled.
     */
    @Throws(HttpConnectionConstructionException::class, HttpConnectionException::class, ReadObjectException::class, CancellationException::class)
    suspend inline fun <reified T> get(): HttpResponse<T> = executeRequest(HttpMethods.GET)

    /**
     * Sends a POST request with [data] as its body.
     *
     * @throws HttpConnectionConstructionException if the URL or connection cannot be prepared.
     * @throws HttpConnectionException if connecting or reading the response fails.
     * @throws RequestSerializationException if [data] cannot be serialized.
     * @throws RequestWriteException if [data] cannot be sent.
     * @throws ReadObjectException if the successful response body cannot be decoded as [T].
     * @throws CancellationException if the coroutine is cancelled.
     */
    @Throws(HttpConnectionConstructionException::class, HttpConnectionException::class, RequestSerializationException::class, RequestWriteException::class, ReadObjectException::class, CancellationException::class)
    suspend inline fun <reified T> post(data: Any): HttpResponse<T> = executeRequest(HttpMethods.POST, data)

    /**
     * Sends a PUT request with [data] as its body.
     *
     * @throws HttpConnectionConstructionException if the URL or connection cannot be prepared.
     * @throws HttpConnectionException if connecting or reading the response fails.
     * @throws RequestSerializationException if [data] cannot be serialized.
     * @throws RequestWriteException if [data] cannot be sent.
     * @throws ReadObjectException if the successful response body cannot be decoded as [T].
     * @throws CancellationException if the coroutine is cancelled.
     */
    @Throws(HttpConnectionConstructionException::class, HttpConnectionException::class, RequestSerializationException::class, RequestWriteException::class, ReadObjectException::class, CancellationException::class)
    suspend inline fun <reified T> put(data: Any): HttpResponse<T> = executeRequest(HttpMethods.PUT, data)

    /**
     * Sends a PATCH request with [data] as its body.
     *
     * @throws HttpConnectionConstructionException if the URL or connection cannot be prepared.
     * @throws HttpConnectionException if connecting or reading the response fails.
     * @throws RequestSerializationException if [data] cannot be serialized.
     * @throws RequestWriteException if [data] cannot be sent.
     * @throws ReadObjectException if the successful response body cannot be decoded as [T].
     * @throws CancellationException if the coroutine is cancelled.
     */
    @Throws(HttpConnectionConstructionException::class, HttpConnectionException::class, RequestSerializationException::class, RequestWriteException::class, ReadObjectException::class, CancellationException::class)
    suspend inline fun <reified T> patch(data: Any): HttpResponse<T> = executeRequest(HttpMethods.PATCH, data)

    /**
     * Sends a DELETE request, optionally with [data] as its body.
     *
     * @throws HttpConnectionConstructionException if the URL or connection cannot be prepared.
     * @throws HttpConnectionException if connecting or reading the response fails.
     * @throws RequestSerializationException if [data] cannot be serialized.
     * @throws RequestWriteException if [data] cannot be sent.
     * @throws ReadObjectException if the successful response body cannot be decoded as [T].
     * @throws CancellationException if the coroutine is cancelled.
     */
    @Throws(HttpConnectionConstructionException::class, HttpConnectionException::class, RequestSerializationException::class, RequestWriteException::class, ReadObjectException::class, CancellationException::class)
    suspend inline fun <reified T> delete(data: Any? = null): HttpResponse<T> = executeRequest(HttpMethods.DELETE, data)




    /**
     * Opens the connection.
     *
     * @throws HttpConnectionException if the connection cannot be opened.
     */
    @Throws(HttpConnectionException::class)
    fun HttpURLConnection.tryConnect() {
        try {
            this.connect()
        } catch (e: Exception) {
            throw HttpConnectionException(
                message = "Failed to connect: ${e.message}",
                request = HttpRequest(
                    address = this@DefaultClient.url ?: "Not set",
                    url = this.url.toString()
                ),
                cause = e
            )
        }
    }

    /**
     * Reads and decodes a response. Non-2xx responses retain their HTTP status and raw error body.
     *
     * @throws HttpConnectionException if a successful response cannot be read.
     * @throws ReadObjectException if the successful body cannot be decoded as [T].
     */
    @Throws(HttpConnectionException::class, ReadObjectException::class)
    inline fun <reified T> HttpURLConnection.read(): HttpResponse<T> {
        val status = try {
            responseCode
        } catch (e: Exception) {
            throw HttpConnectionException(
                message = "Failed to receive HTTP response: ${e.message}",
                request = HttpRequest(
                    address = this@DefaultClient.url ?: "Not set",
                    url = url?.toString() ?: "Not set"
                ),
                cause = e
            )
        }

        val request = HttpRequest(
            address = this@DefaultClient.url ?: "Not set",
            url = url?.toString() ?: "Not set"
        )
        val successful = status in 200..299
        val content = try {
            val responseStream = if (successful) inputStream else errorStream
            responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } catch (e: Exception) {
            if (!successful) {
                ""
            } else {
                throw HttpConnectionException(
                    message = "Failed to read HTTP response: ${e.message}",
                    request = request,
                    cause = e
                )
            }
        }

        // Preserve real HTTP error codes and bodies so API layers can handle them.
        if (!successful) {
            return HttpResponse(status, null, content.ifBlank { null }, request)
        }

        // Empty successful responses (for example 204 No Content) have no model to decode.
        if (content.isBlank()) {
            return HttpResponse(status, null, null, request)
        }

        return try {
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
            HttpResponse(status, result, null, request)
        } catch (e: Exception) {
            val className = try {
                T::class.java.simpleName ?: T::class.simpleName ?: "[Unable to get name]"
            } catch (_: Exception) {
                "[Unable to get name]"
            }
            throw ReadObjectException(
                responseStatus = status,
                responseBody = content,
                request = request,
                message = "Failed to decode HTTP $status response as $className: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Result of an HTTP request. [status] is the server's HTTP status; client failures are thrown
     * as [NetworkingException] subclasses rather than represented as negative status values.
     */
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
