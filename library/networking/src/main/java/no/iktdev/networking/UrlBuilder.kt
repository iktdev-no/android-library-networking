package no.iktdev.networking

import no.iktdev.networking.client.Http
import java.net.URL

class UrlBuilder(val url: String) {
    var _url: String = ""
        private set
    var protocol: Protocol = Protocol.HTTP
        private set
    var domain: String = ""
        private set
    var path: List<String> = listOf()
        private set

    init {
        obtainProtocol(url)
        obtainDomain(url)
        obtainPaths(url)
    }

    private fun obtainProtocol(url: String): Boolean {
        return if (url.contains("//")) {
            when {
                Http.isHttps(url) -> {
                    this.protocol = Protocol.HTTPS
                    true
                }
                Http.isHttp(url) -> {
                    this.protocol = Protocol.HTTP
                    true
                }
                else -> false
            }
        } else false
    }
    private fun obtainDomain(url: String) {
        var murl = if (Http.isHttp(url) && url.contains("//")) url.substring(url.indexOf("//")+2) else url
        murl = if (murl.contains("/")) murl.substring(0, murl.indexOf("/")) else murl
        this.domain = murl.trim()
    }
    private fun obtainPaths(url: String) {
        val murl = if (Http.isHttp(url) && url.contains("//")) url.substring(url.indexOf("//")+2) else url
        val urlPath = if (murl.contains("/")) murl.substring(murl.indexOf("/")) else return
        val splittedPaths: java.util.ArrayList<String> = arrayListOf()
        splittedPaths.addAll(urlPath.split("/").filter { it.isNotEmpty() }.toTypedArray())
        this.path += splittedPaths
    }

    @Suppress("unused")
    fun to(path: String) = apply { if (path.isNotEmpty()) this.path += (path) }
    fun with(paths: List<String>) = apply {
        this.path += (paths.filter { it.isNotEmpty() })
    }

    fun with(vararg parts: String) = apply {
        this.path += parts.filter { it.isNotEmpty() }.toList()
    }

    fun toUrl(): String {
        _url = (if (protocol == Protocol.HTTPS) "https" else "http") + "://" + domain
        _url += "/"
        _url += path.joinToString("/")
        return _url.trimEnd('/')
    }


    fun asURL(): URL {
        return URL(toUrl())
    }
}
