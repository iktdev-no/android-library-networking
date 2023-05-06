package no.iktdev.networking.glide

import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import no.iktdev.networking.client.Http
import no.iktdev.networking.Security
import java.net.URL

class GlideWrapper {
    fun getUrl(url: URL): GlideUrl {
        return getUrl(url.toString())
    }
    fun getUrl(url: String): GlideUrl {
        return if (Http.isHttps(url) && !Security.authorizationBearerToken.isNullOrEmpty())
            GlideUrl(url, LazyHeaders.Builder()
                .addHeader("Authorization", "Bearer ${Security.authorizationBearerToken}").build())
        else GlideUrl(url)
    }

    fun getImage(path: String): Any {
        return if (Http.isHttp(path)) getUrl(path)
        else path
    }
}