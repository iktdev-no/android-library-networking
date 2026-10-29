
package no.iktdev.networking.glide

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.Glide as AndroidGlide
import java.net.URL

/**
 * Configurable Glide client.
 *
 * The global instance can use a dynamic configuration provider.
 * Independent instances can be created with create().
 */
class Glide private constructor() {

    companion object {
        private val global = Glide()

        fun usingUrl(url: String): Glide =
            global.usingBaseUrl(url)

        fun withBearerToken(token: String): Glide =
            global.withToken(token)

        fun withoutBearerToken(): Glide =
            global.withoutToken()

        fun withDefaultTrust(): Glide =
            global.withTrust(GlideTlsMode.DefaultTrust)

        fun withPinnedPublicKey(sha256Key: String): Glide =
            global.withPin(sha256Key)

        fun withAnyTrust(): Glide =
            global.withTrust(GlideTlsMode.AnyTrust)

        fun usingConfigProvider(
            provider: () -> GlideServerConfig
        ): Glide = global.usingConfigProvider(provider)

        fun load(
            imageView: ImageView,
            pathOrUrl: String
        ) = global.loadInto(imageView, pathOrUrl)

        fun load(
            context: Context,
            pathOrUrl: String
        ): RequestBuilder<Drawable> =
            global.loadWith(context, pathOrUrl)

        fun getUrl(pathOrUrl: String): GlideUrl =
            global.currentGlideUrl(pathOrUrl)

        fun create(): Glide = Glide()
    }

    @Volatile
    private var config = GlideConfig()

    @Volatile
    private var configProvider: (() -> GlideServerConfig)? = null

    fun usingConfigProvider(
        provider: () -> GlideServerConfig
    ): Glide = apply {
        configProvider = provider
    }

    fun clearConfigProvider(): Glide = apply {
        configProvider = null
    }

    fun usingBaseUrl(url: String): Glide = apply {
        val parsed = URL(url)

        require(
            (parsed.protocol.equals("http", true) ||
                    parsed.protocol.equals("https", true)) &&
                    parsed.host.isNotBlank()
        ) {
            "URL must be an absolute HTTP or HTTPS URL"
        }

        config = config.copy(baseUrl = url)
    }

    fun withToken(token: String): Glide = apply {
        config = config.copy(bearerToken = token)
    }

    fun withoutToken(): Glide = apply {
        config = config.copy(bearerToken = null)
    }

    fun withTrust(mode: GlideTlsMode): Glide = apply {
        require(mode != GlideTlsMode.PinnedPublicKey) {
            "Use withPin() to configure pinned TLS"
        }

        config = config.copy(
            tlsMode = mode,
            pinnedPublicKeySha256 = null
        )
    }

    fun withPin(sha256Key: String): Glide = apply {
        require(sha256Key.isNotBlank()) {
            "Pinned public key SHA-256 cannot be blank"
        }

        config = config.copy(
            tlsMode = GlideTlsMode.PinnedPublicKey,
            pinnedPublicKeySha256 = sha256Key
        )
    }

    fun load(imageView: ImageView, pathOrUrl: String) {
        loadInto(imageView, pathOrUrl)
    }

    fun load(
        context: Context,
        pathOrUrl: String
    ): RequestBuilder<Drawable> =
        loadWith(context, pathOrUrl)

    private fun loadInto(
        imageView: ImageView,
        pathOrUrl: String
    ) {
        loadWith(imageView.context, pathOrUrl).into(imageView)
    }

    private fun loadWith(
        context: Context,
        pathOrUrl: String
    ): RequestBuilder<Drawable> =
        AndroidGlide.with(context).load(createRequest(pathOrUrl))

    internal fun currentConfig(): GlideConfig {
        val dynamic = configProvider?.invoke()
            ?: return config

        return GlideConfig(
            baseUrl = dynamic.baseUrl,
            bearerToken = dynamic.bearerToken,
            tlsMode = dynamic.tlsMode,
            pinnedPublicKeySha256 = dynamic.pinnedPublicKeySha256
        )
    }

    internal fun createRequest(pathOrUrl: String): GlideRequest {
        val current = currentConfig()
        val url = resolveUrl(current.baseUrl, pathOrUrl)
        val headers = mutableMapOf<String, String>()

        if (url.startsWith("https://", ignoreCase = true)) {
            current.bearerToken
                ?.takeIf { it.isNotBlank() }
                ?.let { headers["Authorization"] = "Bearer $it" }
        }

        return GlideRequest(
            url = url,
            headers = headers,
            tlsMode = current.tlsMode,
            pinnedPublicKeySha256 = current.pinnedPublicKeySha256
        )
    }

    private fun currentGlideUrl(pathOrUrl: String): GlideUrl {
        val request = createRequest(pathOrUrl)

        val headers = LazyHeaders.Builder().apply {
            request.headers.forEach { (name, value) ->
                addHeader(name, value)
            }
        }.build()

        return GlideUrl(request.url, headers)
    }

    private fun resolveUrl(
        baseUrl: String?,
        pathOrUrl: String
    ): String {
        val absolute = runCatching { URL(pathOrUrl) }.getOrNull()

        if (
            absolute != null &&
            (absolute.protocol.equals("http", true) ||
                    absolute.protocol.equals("https", true))
        ) {
            return absolute.toString()
        }

        val base = requireNotNull(baseUrl) {
            "No base URL configured. Configure usingUrl(...) or provide an absolute HTTP(S) URL."
        }

        val normalizedBase =
            if (base.endsWith("/")) base else "$base/"

        return URL(URL(normalizedBase), pathOrUrl).toString()
    }
}

data class GlideServerConfig(
    val baseUrl: String,
    val bearerToken: String?,
    val tlsMode: GlideTlsMode,
    val pinnedPublicKeySha256: String? = null
)

internal data class GlideConfig(
    val baseUrl: String? = null,
    val bearerToken: String? = null,
    val tlsMode: GlideTlsMode = GlideTlsMode.DefaultTrust,
    val pinnedPublicKeySha256: String? = null
)

enum class GlideTlsMode {
    DefaultTrust,
    PinnedPublicKey,
    AnyTrust
}

internal data class GlideRequest(
    val url: String,
    val headers: Map<String, String>,
    val tlsMode: GlideTlsMode,
    val pinnedPublicKeySha256: String?
)
