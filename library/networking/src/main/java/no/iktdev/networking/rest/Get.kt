package no.iktdev.networking.rest

import no.iktdev.networking.Security
import no.iktdev.networking.UrlBuilder
import no.iktdev.networking.client.Http
import java.io.FileNotFoundException
import java.net.*

@Suppress("unused")
abstract class Get(url: String?, definedAuthMode: Security.HttpAuthenticate = Security.HttpAuthenticate.DoAuth): Api(url, definedAuthMode) {

    /**
     * @param paths { url paths }
     * @param securityAuthMode Uses authorization bearer token in Security. If undefined or null, exception will be thrown!!
     */
    @Suppress("unused")
    protected inline fun <reified T> request(paths: List<String> = listOf(), timeOut: Int = 0, securityAuthMode: Security.HttpAuthenticate = Security.HttpAuthenticate.Defaults): Http.HttpObjectResponse<T?> {
        if (url == null) {
            return Http.HttpObjectResponse(HttpURLConnection.HTTP_NOT_FOUND, null, "No Url passed!")
        }

        val urlBuilder = UrlBuilder(url).with(apiPaths).with(paths)
        val http = getHttp(urlBuilder.asURL(), Security.shouldProvideBearer(definedAuthMode, securityAuthMode))

        return try {
            http.Get<T>(if (timeOut == 0 || timeOut < this.timeOut) this.timeOut else timeOut )
        } catch (e: SocketTimeoutException) {
            Http.HttpObjectResponse(HttpURLConnection.HTTP_CLIENT_TIMEOUT, null, urlBuilder.toUrl())
        } catch (e: FileNotFoundException) {
            Http.HttpObjectResponse(HttpURLConnection.HTTP_NOT_FOUND, null, urlBuilder.toUrl())
        } catch (e: Exception) {
            e.printStackTrace()
            Http.HttpObjectResponse(0, null, urlBuilder.toUrl())
        }
    }


    /**
     * @param paths { url path after baseurl }
     * @param securityAuthMode Uses authorization bearer token in Security. If undefined or null, exception will be thrown!!
     */
    @Suppress("unused")
    protected fun request(paths: List<String> = listOf(), timeOut: Int = 0, securityAuthMode: Security.HttpAuthenticate = Security.HttpAuthenticate.Defaults): Http.HttpStringResponse? {
        return executeRequest(method = Method.GET, paths = paths, timeout = timeOut, securityAuthMode = securityAuthMode)
    }
}