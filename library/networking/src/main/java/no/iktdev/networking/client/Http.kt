package no.iktdev.networking.client

import java.net.URI
import java.net.URL
import java.util.*

abstract class Http(url: URL): HttpMethodImplementation(url) {
    companion object {
        /**
         * @return true if either http or https
         */
        fun isHttp(url: String): Boolean {
            if (url.isEmpty()) return false
            if (!url.contains(":")) return false
            if (url.length < 5) return false

            return (url.substring(0, 4).lowercase(Locale.getDefault()) == "http")
        }

        fun isHttp(url: URI?): Boolean {
            return if (url == null) false else isHttp(url.toString())
        }

        /**
         * @return true if only https
         */
        fun isHttps(url: String): Boolean {
            if (!isHttp(url)) return false
            return (url.substring(0, url.indexOf(":")).contains("s")) && isHttp(url)
        }

        fun isHttps(url: URI?): Boolean {
            return if (url == null) false else isHttps(url.toString())
        }


        fun getHttpByUrl(url: URL): Http {
            return if (isHttps(url.toString())) HttpSecure(url)
            else HttpOpen(url)
        }

        fun getHttpByUrl(url: String): Http {
            return getHttpByUrl(URL(url))
        }
    }

    data class HttpObjectResponse<T>(val status: Int, val result: T?, val url: String)
    data class HttpStringResponse(val status: Int, val result: String, val url: String)

}