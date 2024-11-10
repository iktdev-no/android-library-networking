package no.iktdev.networking

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

fun InetAddress.isIPv4(): Boolean {
    return this is Inet4Address
}

fun InetAddress.isIPv6(): Boolean {
    return this is Inet6Address
}