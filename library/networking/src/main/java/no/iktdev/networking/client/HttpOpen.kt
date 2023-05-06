package no.iktdev.networking.client

import java.net.HttpURLConnection
import java.net.URL

class HttpOpen(url: URL): Http(url) {
    override val http: HttpURLConnection = open()
}