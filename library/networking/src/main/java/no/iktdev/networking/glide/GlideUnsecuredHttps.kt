package no.iktdev.networking.glide

import android.util.Log
import com.bumptech.glide.Registry
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import no.iktdev.networking.Security
import okhttp3.OkHttpClient
import java.io.InputStream
import javax.net.ssl.X509TrustManager

class GlideUnsecuredHttps {
    companion object {
        fun getUnsafeOkHttpClient(): OkHttpClient {
            Log.d("UnsecureGlideHttps", "Using http client that permits unsafe https")
            return try {
                val sec = Security()
                val socketFactory = sec.ssl()
                val trusted: X509TrustManager = sec.truster[0] as X509TrustManager

                val builder = OkHttpClient.Builder()
                builder.sslSocketFactory(socketFactory, trusted) // (X509TrustManager)trustAllCerts[0]);
                builder.hostnameVerifier(sec.name)
                builder.build()
            } catch (e: Exception) {
                Log.e(this::class.java.simpleName, "Unable to build unsecure OkHttp Client")
                e.printStackTrace()
                throw RuntimeException(e)
            }
        }
    }
    fun registerComponent(registry: Registry) {
        Log.d("GlideModule", "Loading glide module from ${this.javaClass.name}")
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