package no.iktdev.networking.client

import no.iktdev.networking.Security
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class HttpSecure(url: URL): Http(url) {

    override val http: HttpsURLConnection = open() as HttpsURLConnection

    init {
        if (Security.makeCompletelyUnsecureCalls) {
            Security().disableHttpsSecurity(http)
        }
    }

    @Throws(Security.AuthorizationBearerNotConfigured::class)
    fun useAuthorizationBearer() {
        Security().setAuthorizationBearer(http)
    }
}