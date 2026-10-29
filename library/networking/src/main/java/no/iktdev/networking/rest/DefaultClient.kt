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
import java.security.KeyStore
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
open class DefaultClient(): BasicClient() {


    /** Sets the base URL used to create requests. */
    override fun usingUrl(url: String) = apply {
        super.usingUrl(url)
    }

    /** Sets connect and read timeouts, in milliseconds. */
    override fun withTimeout(timeout: Int) = apply {
        super.withTimeout(timeout)
    }

    /** Adds an `Authorization: Bearer …` header to requests. */
    override fun withBearerToken(token: String) = apply {
        this.bearerToken = token
    }

    override fun withAnyTrust(): DefaultClient =
        apply { super.withAnyTrust() }

    override fun withDefaultTrust(): DefaultClient =
        apply { super.withDefaultTrust() }

    override fun withPinnedPublicKey(sha256Key: String): DefaultClient =
        apply { super.withPinnedPublicKey(sha256Key) }

    /** Sets the `Content-Type` request header. */
    fun withContentType(contentType: String) = apply {
        this.contentType = contentType
    }

    /** Sets the path for the next request and clears query parameters from the previous path. */
    override fun withPaths(vararg part: String) = apply {
        super.withPaths(*part)
    }


    /** Sets encoded query parameters for the current path. Call after [withPaths]; null values are omitted. */
    override fun withQueryParams(vararg params: Pair<String, Any?>) = apply {
        super.withQueryParams(*params)
    }

    /** Adds one encoded query parameter. Call repeatedly when a key needs multiple values. */
    fun withQueryParam(name: String, value: Any?) = apply {
        value?.let { this.queryParams += encodeQueryParam(name, it) }
    }


    @Throws(IOException::class)
    fun openStream(headers: Map<String, String> = emptyMap()): HttpURLConnection {

        val connection = asConnection()
        contentType = null

        try {
            connection.requestMethod = HttpMethods.GET.name
            headers.forEach { (name, value) ->
                connection.setRequestProperty(name, value)
            }

            connection.connect()
            return connection
        } catch (e: Exception) {
            connection.disconnect()
            throw e
        }
    }




    /**
     * Prepares and connects an HTTP request.
     *
     * @throws HttpConnectionConstructionException if URL or connection setup fails.
     * @throws HttpConnectionException if connecting fails.
     */
    @Throws(HttpConnectionConstructionException::class, HttpConnectionException::class)
    fun prepareConnection(method: HttpMethods, doOutput: Boolean = false): HttpURLConnection {
        val connection = try {
            asConnection()
        } catch (e: Exception) {
            throw HttpConnectionConstructionException(
                message = "Failed to create HTTP connection: ${e.message}",
                request = this@DefaultClient.url ?: "Not set",
                cause = e
            )
        }

        try {
            connection.requestMethod = method.name
            connection.doOutput = doOutput
            connection.tryConnect()
            return connection
        } catch (e: NetworkingException) {
            // This method did not successfully hand the connection to its caller.
            connection.disconnect()
            throw e
        } catch (e: Exception) {
            // This method did not successfully hand the connection to its caller.
            connection.disconnect()
            throw HttpConnectionConstructionException(
                message = "Failed to configure HTTP connection: ${e.message}",
                request = this@DefaultClient.url ?: "Not set",
                cause = e
            )
        }
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
        val connection = prepareConnection(method, doOutput = data != null)
        try {
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
                        request = this@DefaultClient.url,
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
                request = requestUrl,
                cause = e
            )
        } finally {
            connection.disconnect()
        }
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
                request = this@DefaultClient.url,
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
                request = this@DefaultClient.url,
                cause = e
            )
        }

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
                    request = this@DefaultClient.url,
                    cause = e
                )
            }
        }

        // Preserve real HTTP error codes and bodies so API layers can handle them.
        if (!successful) {
            return HttpResponse(status, null, content.ifBlank { null }, this@DefaultClient.url)
        }

        // Empty successful responses (for example 204 No Content) have no model to decode.
        if (content.isBlank()) {
            return HttpResponse(status, null, null, this@DefaultClient.url)
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
            HttpResponse(status, result, null, this@DefaultClient.url)
        } catch (e: Exception) {
            val className = try {
                T::class.java.simpleName ?: T::class.simpleName ?: "[Unable to get name]"
            } catch (_: Exception) {
                "[Unable to get name]"
            }
            throw ReadObjectException(
                responseStatus = status,
                responseBody = content,
                request = this@DefaultClient.url,
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
        val request: String?
    )

}
