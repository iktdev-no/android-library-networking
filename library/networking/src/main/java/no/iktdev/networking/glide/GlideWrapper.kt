package no.iktdev.networking.glide

import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import no.iktdev.networking.client.Http
import no.iktdev.networking.Security
import java.net.URL

object GlideWrapper {
    var authorizationBearerToken: String? = null
    fun getUrl(url: URL): GlideUrl {
        return getUrl(url.toString())
    }
    fun getUrl(url: String): GlideUrl {
        return if (Http.isHttps(url) && !authorizationBearerToken.isNullOrEmpty())
            GlideUrl(url, LazyHeaders.Builder()
                .addHeader("Authorization", "Bearer $authorizationBearerToken").build())
        else GlideUrl(url)
    }

    /**
     * Returns a GlideUrl for the given path.
     * If the path is an HTTP URL, it will return a GlideUrl with the Authorization header if set.
     * If the path is not an HTTP URL, it will return the path as is.
     */
    fun getImage(path: String): Any {
        return if (Http.isHttp(path)) getUrl(path)
        else path
    }
}


