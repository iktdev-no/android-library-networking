package no.iktdev.networking

import android.annotation.SuppressLint
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.*

class Security {
    companion object {
        var makeCompletelyUnsecureCalls: Boolean = false
        var authorizationBearerToken: String? = null
        var allowRedirects: Boolean = true

        fun shouldProvideBearer(defined: HttpAuthenticate, requestedAuthMode: HttpAuthenticate): Boolean {
            val shouldProvide = !(defined == HttpAuthenticate.DoAnon && requestedAuthMode == HttpAuthenticate.Defaults || requestedAuthMode == HttpAuthenticate.DoAnon)
            return !(shouldProvide && authorizationBearerToken.isNullOrEmpty())
        }
    }

    var truster = arrayOf<TrustManager>(
        @SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            @Throws(CertificateException::class)
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                // Do trust
            }

            @Throws(CertificateException::class)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                // Do trust
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }
        }
    )

    var name = HostnameVerifier { _, _ -> true }

    @Throws(NoSuchAlgorithmException::class, KeyManagementException::class)
    fun ssl(): SSLSocketFactory {
        val ssl = SSLContext.getInstance("TLS")
        ssl.init(null, truster, SecureRandom())
        return ssl.socketFactory
    }

    fun disableHttpsSecurity(http: HttpsURLConnection) {
        http.hostnameVerifier = name
        http.sslSocketFactory = ssl()
        /*HttpsURLConnection.setDefaultHostnameVerifier(name)
        HttpsURLConnection.setDefaultSSLSocketFactory(ssl())*/
    }

    fun setAuthorizationBearer(http: HttpsURLConnection) {
        if (authorizationBearerToken.isNullOrEmpty())
            throw AuthorizationBearerNotConfigured("Authorization Token is not configured!\n\tPlease call Security.authorizationBearerToken = 'yourtokenhere'")
        http.setRequestProperty("Authorization", "Bearer $authorizationBearerToken")
        http.instanceFollowRedirects = allowRedirects
    }


    class AuthorizationBearerNotConfigured(override val message: String?): RuntimeException()

    /*fun getToken(): String? {
        val fingerprint = App.connection().getServer()?.fingerprint;

        return if (fingerprint != null) {
            Get2().accessObject(fingerprint)?.jwt?.token
        } else {""}
    }*/

    enum class HttpAuthenticate {
        DoAuth,
        DoAnon,
        Defaults

    }

}