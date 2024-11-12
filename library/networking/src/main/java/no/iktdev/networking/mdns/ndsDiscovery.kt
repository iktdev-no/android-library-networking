package no.iktdev.networking.mdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class ndsDiscovery(context: Context, val serviceName: String, var listener: ndsDiscoveryListener) {
    val TAG = this::class.java.simpleName
    private var nsdManager: NsdManager? = null

    init {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val discoveryListener: NsdManager.DiscoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.d("NsdHelper", "Discovery started for service type: $serviceType")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            serviceInfo?.let { info ->
                Log.d("NsdHelper", "Service found: ${info.serviceName}, type: ${info.serviceType}")
                if (info.serviceType.trim('.') == serviceName.trim('.')) {
                    nsdManager?.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            serviceInfo?.let { info -> listener.onResolveFailed(info, errorCode) }
                            Log.e("NsdHelper", "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo?) {
                            resolvedServiceInfo?.let { info ->
                                listener.onResolved(info)
                            }
                        }
                    })
                }
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            serviceInfo?.let { info ->
                Log.d("NsdHelper", "Service lost: ${info.serviceName}")
                listener.onLost(info.host, info.serviceName)
            }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d("NsdHelper", "Discovery stopped for service type: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e("NsdHelper", "Discovery start failed: $errorCode")
            listener.onStartFailed(serviceType, errorCode)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e("NsdHelper", "Discovery stop failed: $errorCode")
            nsdManager?.stopServiceDiscovery(this)
        }

    }


    // Stopp tjenesteoppdagelsen
    fun stopDiscovery() {
        nsdManager?.stopServiceDiscovery(discoveryListener)
    }

    fun startDiscovery() {
        nsdManager?.discoverServices(serviceName, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }
}