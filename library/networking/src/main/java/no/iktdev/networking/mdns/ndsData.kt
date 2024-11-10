package no.iktdev.networking.mdns

import no.iktdev.networking.isIPv4
import no.iktdev.networking.isIPv6
import java.net.InetAddress

data class ndsData(
    val name: String,
    val url: String,
    val ip: String,
    val port: Int
) {
    fun isV4(): Boolean {
        return InetAddress.getByName(ip).isIPv4()
    }

    fun isV6(): Boolean {
        return InetAddress.getByName(ip).isIPv6()
    }
}
