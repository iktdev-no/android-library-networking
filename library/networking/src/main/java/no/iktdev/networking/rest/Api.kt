package no.iktdev.networking.rest

import android.util.Log
import com.google.gson.Gson
import no.iktdev.networking.Security
import no.iktdev.networking.UrlBuilder
import no.iktdev.networking.client.Http
import no.iktdev.networking.client.HttpOpen
import no.iktdev.networking.client.HttpSecure
import java.io.FileNotFoundException
import java.lang.reflect.Type
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

open class Api(
    val url: String?,
    var definedAuthMode: Security.HttpAuthenticate = Security.HttpAuthenticate.DoAuth
) {
    open var timeOut: Int = 5000
    open val apiPaths: List<String> = listOf()

    enum class Method {
        GET,
        POST,
        DELETE
    }

    @Suppress("unused")
    protected inline fun <reified T> fromJson(content: String, type: Type): T {
        try {
            return Gson().fromJson(content, type)
        } catch (e: Exception) {
            Log.e("Decode to $type failed", content)
            throw e
        }
    }

    protected fun getHttp(url: URL, useAuthorizationBearerOnHttps: Boolean): Http {
        val http = if (Http.isHttps(url.toString())) HttpSecure(url) else HttpOpen(url)
        if (http is HttpSecure && useAuthorizationBearerOnHttps)
            http.useAuthorizationBearer()
        return http
    }

    protected fun getHttp(url: URL, useAuthorizationToken: String): Http {
        val http = if (Http.isHttps(url.toString())) HttpSecure(url) else HttpOpen(url)
        if (http is HttpSecure && useAuthorizationToken.isNotEmpty())
            Security.useAuthorizationBearer(http.http, useAuthorizationToken)
        return http
    }

    protected fun httpClient(
        paths: List<String> = listOf(),
        securityAuthMode: Security.HttpAuthenticate = Security.HttpAuthenticate.Defaults
    ): Http? {
        if (url == null) {
            return null
        }
        val urlBuilder = UrlBuilder(url).with(apiPaths).with(paths)
        return getHttp(
            urlBuilder.asURL(),
            Security.shouldProvideBearer(definedAuthMode, securityAuthMode)
        )
    }

    protected fun httpClient(
        paths: List<String> = listOf(),
        useAuthorizationToken: String
    ): Http? {
        if (url == null) {
            return null
        }
        val urlBuilder = UrlBuilder(url).with(apiPaths).with(paths)
        return getHttp(
            urlBuilder.asURL(),
            useAuthorizationToken
        )
    }

    protected fun executeRequest(
        method: Method,
        paths: List<String>,
        timeout: Int = 0,
        securityAuthMode: Security.HttpAuthenticate,
        payload: String? = null
    ): Http.HttpStringResponse {
        val http = httpClient(paths, securityAuthMode)
            ?: return Http.HttpStringResponse(
                HttpURLConnection.HTTP_NOT_FOUND,
                "",
                "No Url passed!"
            )
        return executeOnMethod(http = http, method = method, timeout = timeout, payload = payload)
    }

    protected fun executeRequest(
        method: Method,
        paths: List<String>,
        timeout: Int = 0,
        useAuthorizationToken: String,
        payload: String? = null
    ): Http.HttpStringResponse {
        val http = httpClient(paths, useAuthorizationToken)
            ?: return Http.HttpStringResponse(
                HttpURLConnection.HTTP_NOT_FOUND,
                "",
                "No Url passed!"
            )
        return executeOnMethod(http = http, method = method, timeout = timeout, payload = payload)
    }

    private fun executeOnMethod(
        http: Http,
        timeout: Int = 0,
        method: Method,
        payload: String? = null

    ): Http.HttpStringResponse {
        return try {
            when (method) {
                Method.GET -> http.Get(if (timeout == 0 || timeout < this.timeOut) this.timeOut else timeout)
                Method.POST -> if (payload != null) http.Post(
                    payload,
                    timeOut
                ) else Http.HttpStringResponse(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "Payload was null for method $method",
                    http.url.toString()
                )
                Method.DELETE -> if (payload != null) http.Delete(
                    payload,
                    timeOut
                ) else Http.HttpStringResponse(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "Payload was null for method $method",
                    http.url.toString()
                )

            }
        } catch (e: SocketTimeoutException) {
            Http.HttpStringResponse(HttpURLConnection.HTTP_CLIENT_TIMEOUT, "", http.url.toString())
        } catch (e: FileNotFoundException) {
            Http.HttpStringResponse(HttpURLConnection.HTTP_NOT_FOUND, "", http.url.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            Http.HttpStringResponse(0, "", http.url.toString())
        }
    }

}