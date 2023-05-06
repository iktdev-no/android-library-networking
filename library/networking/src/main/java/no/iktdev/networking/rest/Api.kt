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
    open val apiPaths: ArrayList<String> = arrayListOf()

    enum class Method {
        GET,
        POST,
        DELETE
    }


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

    protected fun httpClient(
        paths: ArrayList<String> = arrayListOf(),
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

    protected final fun executeRequest(
        method: Method,
        paths: ArrayList<String>,
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