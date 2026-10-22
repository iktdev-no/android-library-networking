package no.iktdev.networking.glide

import com.bumptech.glide.Registry
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import no.iktdev.networking.Security
import okhttp3.OkHttpClient
import java.io.InputStream
import javax.net.ssl.X509TrustManager
import java.util.logging.Level
import java.util.logging.Logger

class GlideUnsecuredHttps {
    companion object {
        private val logger = Logger.getLogger(GlideUnsecuredHttps::class.java.name)

        fun getUnsafeOkHttpClient(): OkHttpClient {
            logger.fine("Creating OkHttp client that permits unsafe HTTPS")
            return try {
                val sec = Security()
                val socketFactory = sec.ssl()
                val trusted: X509TrustManager = sec.truster[0] as X509TrustManager

                val builder = OkHttpClient.Builder()
                builder.sslSocketFactory(socketFactory, trusted) // (X509TrustManager)trustAllCerts[0]);
                builder.hostnameVerifier(sec.name)
                builder.build()
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Unable to build the HTTPS OkHttp client", e)
                throw RuntimeException(e)
            }
        }
    }
    fun registerComponent(registry: Registry) {
        logger.fine("Loading Glide module ${this.javaClass.name}")
        if (Security.makeCompletelyUnsecureCalls) {
            val client: OkHttpClient = getUnsafeOkHttpClient()
            registry.replace(
                GlideUrl::class.java,
                InputStream::class.java,
                OkHttpUrlLoader.Factory(client)
            )
        }
    }
}