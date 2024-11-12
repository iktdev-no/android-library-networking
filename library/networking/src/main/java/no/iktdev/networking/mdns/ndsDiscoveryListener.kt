package no.iktdev.networking.mdns

import android.net.nsd.NsdServiceInfo

interface ndsDiscoveryListener {

    fun onResolveFailed(info: NsdServiceInfo, errorCode: Int)
    fun onResolved(info: NsdServiceInfo)
    fun onLost(info: NsdServiceInfo)
    fun onStartFailed(serviceType: String, errorCode: Int)
}