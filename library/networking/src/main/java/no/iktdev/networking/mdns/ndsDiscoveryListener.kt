package no.iktdev.networking.mdns

import android.net.nsd.NsdServiceInfo
import java.net.InetAddress

interface ndsDiscoveryListener {

    fun onResolveFailed(info: NsdServiceInfo, errorCode: Int)
    fun onResolved(ndsData: ndsData)
    fun onLost(hostAddress: InetAddress, serviceName: String)
    fun onStartFailed(serviceType: String, errorCode: Int)
}