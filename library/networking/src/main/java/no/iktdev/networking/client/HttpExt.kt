package no.iktdev.networking.client

import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection

fun HttpURLConnection.isOk(): Boolean {
    return this.responseCode == HttpURLConnection.HTTP_OK
}

fun HttpsURLConnection.isOk(): Boolean {
    return this.responseCode == HttpURLConnection.HTTP_OK
}