package no.iktdev.networking.rest

import com.google.gson.Gson
import no.iktdev.networking.Security
import no.iktdev.networking.UrlBuilder
import no.iktdev.networking.client.Http
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException

@Suppress("unused")
abstract class Post(url: String?, definedAuthMode: Security.HttpAuthenticate = Security.HttpAuthenticate.DoAuth): Api(url, definedAuthMode) {


    /**
     * Will return response in T
     * @param paths { url path after baseurl }
     * @param payload Any object that can be converted to json using GSON
     * @param securityAuthMode Uses authorization bearer token in Security. If undefined or null, exception will be thrown!!
     */
    @Suppress("unused")
    protected inline fun <reified T> request(paths: List<String> = listOf(), payload: Any, securityAuthMode: Security.HttpAuthenticate = Security.HttpAuthenticate.Defaults): Http.HttpObjectResponse<T?> {
        if (url == null) {
            return Http.HttpObjectResponse(HttpURLConnection.HTTP_NOT_FOUND, null, "No Url passed!")
        }

        val urlBuilder = UrlBuilder(url).with(apiPaths).with(paths)
        val http = getHttp(urlBuilder.asURL(), Security.shouldProvideBearer(definedAuthMode, securityAuthMode))

        val payloadString = Gson().toJson(payload)

        return try {
            http.Post<T>(payloadString, timeOut)
        } catch (e: SocketTimeoutException) {
            Http.HttpObjectResponse(HttpURLConnection.HTTP_CLIENT_TIMEOUT, null, http.url.toString())
        } catch(e: FileNotFoundException) {
            Http.HttpObjectResponse(HttpURLConnection.HTTP_NOT_FOUND, null, http.url.toString())
        } catch(e: Exception) {
            e.printStackTrace()
            Http.HttpObjectResponse(0, null, http.url.toString())
        }
    }

    protected inline fun <reified T> request(vararg parts: String, payload: Any, securityAuthMode: Security.HttpAuthenticate = Security.HttpAuthenticate.Defaults): Http.HttpObjectResponse<T?> {
        return request<T>(paths = parts.toList(), payload = payload, securityAuthMode = securityAuthMode)
    }

    /**
     * Will return response as raw String
     * @param paths { url path after baseurl }
     * @param payload Any object that can be converted to json using GSON
     * @param securityAuthMode Uses authorization bearer token in Security. If undefined or null, exception will be thrown!!
     */
    @Suppress("unused")
    protected fun request(paths: List<String> = listOf(), payload: Any, securityAuthMode: Security.HttpAuthenticate = Security.HttpAuthenticate.Defaults): Http.HttpStringResponse {
        val payloadString = Gson().toJson(payload)
        return executeRequest(method = Method.POST, paths = paths, securityAuthMode = securityAuthMode, payload = payloadString)
    }
}