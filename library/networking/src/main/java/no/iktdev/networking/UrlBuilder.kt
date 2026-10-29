
package no.iktdev.networking

import java.net.URI
import java.net.URL

class UrlBuilder(val url: String) {

    private val uri = URI(url)

    val protocol: Protocol = when (uri.scheme?.lowercase()) {
        "https" -> Protocol.HTTPS
        "http" -> Protocol.HTTP
        else -> throw IllegalArgumentException(
            "Unsupported URL protocol: ${uri.scheme}"
        )
    }

    val domain: String = uri.rawAuthority
        ?.substringAfterLast('@')
        ?: throw IllegalArgumentException("URL must contain a domain: $url")

    var path: List<String> = uri.rawPath
        .orEmpty()
        .split('/')
        .filter(String::isNotEmpty)
        private set

    fun isHttps(url: String): Boolean =
        URI(url).scheme.equals("https", ignoreCase = true)

    fun isHttp(url: String): Boolean =
        URI(url).scheme.equals("http", ignoreCase = true)

    fun to(path: String) = apply {
        this.path += splitPath(path)
    }

    fun with(paths: List<String>) = apply {
        this.path += paths.flatMap(::splitPath)
    }

    fun with(vararg parts: String) = apply {
        this.path += parts.flatMap(::splitPath)
    }

    fun toUrl(): String = buildString {
        append(protocol.name.lowercase())
        append("://")
        append(domain)
        append('/')
        append(path.joinToString("/"))
    }.trimEnd('/')

    fun asURL(): URL = URI(toUrl()).toURL()

    private fun splitPath(path: String): List<String> =
        path.split('/').filter(String::isNotEmpty)
}
