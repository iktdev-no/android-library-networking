
package no.iktdev.networking.security

import android.annotation.SuppressLint
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class TlsConfig private constructor(
    val socketFactory: SSLSocketFactory,
    val trustManager: X509TrustManager,
    val hostnameVerifier: HostnameVerifier?
) {
    companion object {
        fun default(): TlsConfig =
            create(defaultTrustManager(), null)

        fun pinned(publicKeySha256: String): TlsConfig =
            create(pinnedTrustManager(publicKeySha256), null)

        fun insecure(): TlsConfig =
            create(trustAllManager(), HostnameVerifier { _, _ -> true })

        private fun create(
            manager: X509TrustManager,
            hostnameVerifier: HostnameVerifier?
        ): TlsConfig {
            val context = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(manager), SecureRandom())
            }

            return TlsConfig(
                socketFactory = context.socketFactory,
                trustManager = manager,
                hostnameVerifier = hostnameVerifier
            )
        }

        private fun defaultTrustManager(): X509TrustManager {
            val factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            )
            factory.init(null as java.security.KeyStore?)

            return factory.trustManagers
                .filterIsInstance<X509TrustManager>()
                .firstOrNull()
                ?: throw IllegalStateException("No X509TrustManager available")
        }

        @SuppressLint("CustomX509TrustManager")
        private fun pinnedTrustManager(
            configuredPin: String
        ): X509TrustManager {
            val pin = configuredPin.removePrefix("sha256/").trim()

            val expected = try {
                Base64.decode(pin, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Pin must be a Base64-encoded SHA-256 SPKI digest",
                    e
                )
            }

            require(expected.size == 32) {
                "SHA-256 pin must decode to exactly 32 bytes"
            }

            return object : X509TrustManager {
                override fun checkServerTrusted(
                    chain: Array<X509Certificate>,
                    authType: String
                ) {
                    if (chain.isEmpty()) {
                        throw CertificateException("Server supplied no certificate")
                    }

                    val matches = chain.any { certificate ->
                        val digest = MessageDigest.getInstance("SHA-256")
                            .digest(certificate.publicKey.encoded)

                        MessageDigest.isEqual(expected, digest)
                    }

                    if (!matches) {
                        throw CertificateException(
                            "Server certificate public key does not match the approved pin"
                        )
                    }

                    // A matching pin is the trust decision. Check certificate
                    // validity as an additional safeguard.
                    chain.first().checkValidity()
                }

                override fun checkClientTrusted(
                    chain: Array<X509Certificate>,
                    authType: String
                ) {
                    defaultTrustManager().checkClientTrusted(chain, authType)
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> =
                    defaultTrustManager().acceptedIssuers
            }
        }

        @Suppress("CustomX509TrustManager", "TrustAllX509TrustManager")
        private fun trustAllManager(): X509TrustManager =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<X509Certificate>,
                    authType: String
                ) = Unit

                override fun checkServerTrusted(
                    chain: Array<X509Certificate>,
                    authType: String
                ) = Unit

                override fun getAcceptedIssuers(): Array<X509Certificate> =
                    emptyArray()
            }
    }
}
