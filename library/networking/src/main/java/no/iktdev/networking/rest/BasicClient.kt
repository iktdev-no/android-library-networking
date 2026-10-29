package no.iktdev.networking.rest

import no.iktdev.networking.UrlBuilder
import no.iktdev.networking.security.TlsConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.net.ssl.HttpsURLConnection

abstract class BasicClient {
    protected var trust = Trust.DefaultTrust

    var url: String? = null
        protected set
    /** Sets the base URL used to create requests. */
    open fun usingUrl(url: String) = apply {
        this.url = url
    }

    protected var bearerToken: String? = null
    /** Adds an `Authorization: Bearer …` header to requests. */
    open fun withBearerToken(token: String) = apply {
        this.bearerToken = token
    }

    private var pinnedPublicKeySha256: String? = null
    open fun withPinnedPublicKey(sha256Key: String) = apply {
        require(sha256Key.isNotBlank()) {
            "Pinned public key SHA-256 cannot be blank"
        }

        pinnedPublicKeySha256 = sha256Key
        trust = Trust.DefaultTrustWithPinnedKey
    }

    open fun withDefaultTrust() = apply {
        pinnedPublicKeySha256 = null
        trust = Trust.DefaultTrust
    }

    open fun withAnyTrust() = apply {
        pinnedPublicKeySha256 = null
        trust = Trust.AnyTrust
    }


    enum class HttpMethods {
        GET,
        POST,
        PUT,
        PATCH,
        DELETE,
        HEAD
    }


    private var timeout: Int = 5000
    /** Sets connect and read timeouts, in milliseconds. */
    open fun withTimeout(timeout: Int) = apply {
        this.timeout = timeout
    }

    protected var queryParams: List<String> = emptyList()

    /** Sets encoded query parameters for the current path. Call after [withPaths]; null values are omitted. */
    open fun withQueryParams(vararg params: Pair<String, Any?>) = apply {
        this.queryParams = params.mapNotNull { (name, value) ->
            value?.let { encodeQueryParam(name, it) }
        }
    }

    protected var paths: List<String> = listOf()
    /** Sets the path for the next request and clears query parameters from the previous path. */
    open fun withPaths(vararg part: String) = apply {
        this.paths = part.toList()
        this.queryParams = emptyList()
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

    var exceptionHandler: ((Exception) -> Unit)? = null
        private set
    /** Registers a callback invoked before a [NetworkingException] is rethrown. */
    fun setExceptionHandler(e : (Exception) -> Unit) = apply {
        this.exceptionHandler = e
    }

    protected fun encodeQueryParam(name: String, value: Any): String {
        val encodedName = URLEncoder.encode(name, Charsets.UTF_8.name())
        val encodedValue = URLEncoder.encode(value.toString(), Charsets.UTF_8.name())
        return "$encodedName=$encodedValue"
    }

    open fun asConnection(): HttpURLConnection {
        val requestUrl = getUrl()
            ?: throw IllegalArgumentException("Base URL must be provided")

        val connection = requestUrl.openConnection() as HttpURLConnection

        try {
            if (connection is HttpsURLConnection) {
                val tls = when (trust) {
                    Trust.DefaultTrust ->
                        TlsConfig.default()

                    Trust.DefaultTrustWithPinnedKey ->
                        TlsConfig.pinned(
                            requireNotNull(pinnedPublicKeySha256) {
                                "Pinned public key is required for pinned TLS"
                            }
                        )

                    Trust.AnyTrust ->
                        TlsConfig.insecure()
                }

                connection.sslSocketFactory = tls.socketFactory

                tls.hostnameVerifier?.let {
                    connection.hostnameVerifier = it
                }
            }

            return connection.apply {
                contentType?.let {
                    setRequestProperty("Content-Type", it)
                }

                if (!bearerToken.isNullOrEmpty()) {
                    setRequestProperty("Authorization", "Bearer $bearerToken")
                }

                connectTimeout = timeout
                readTimeout = timeout
            }
        } catch (e: Exception) {
            connection.disconnect()
            throw e
        }
    }



    enum class Trust {
        AnyTrust,
        DefaultTrust,
        DefaultTrustWithPinnedKey
    }
}