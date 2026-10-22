package no.iktdev.networking.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build

/** A transport that Android reports for the active default network. */
enum class NetworkTransport {
    WIFI, CELLULAR, ETHERNET, VPN, BLUETOOTH, OTHER
}

enum class NetworkConnectionState {
    INITIALIZING, CONNECTED, LOSING, DISCONNECTED
}

/** Human-readable link details intended for diagnostics and network troubleshooting. */
data class NetworkLinkDetails(
    val interfaceName: String?,
    val addresses: List<String>,
    val dnsServers: List<String>,
    val routes: List<String>,
    val domains: String?,
    /** Whether Android Private DNS is active; available from API 28, otherwise `null`. */
    val privateDnsActive: Boolean?,
    /** Strict Private DNS provider hostname, when configured and available from API 28. */
    val privateDnsServerName: String?,
    /** Available on Android API 29 and newer; `null` on older supported API levels. */
    val mtu: Int?
)

/**
 * Snapshot of Android's active default network. Internet capability/validation does not indicate
 * whether a particular LAN or Tailscale host is reachable.
 */
data class NetworkConnectivity(
    val state: NetworkConnectionState,
    val transports: Set<NetworkTransport> = emptySet(),
    val hasInternetCapability: Boolean = false,
    val isValidated: Boolean = false,
    val isMetered: Boolean = false,
    val losingInMs: Int? = null,
    val link: NetworkLinkDetails? = null
) {
    val isConnected: Boolean
        get() = state == NetworkConnectionState.CONNECTED || state == NetworkConnectionState.LOSING
}

fun interface NetworkConnectivityListener {
    fun onConnectivityChanged(connectivity: NetworkConnectivity)
}

/**
 * Observes Android's active default network. Android callbacks may arrive off the main thread.
 * Registers with the system only while at least one observer is active.
 *
 * The consuming app must have `android.permission.ACCESS_NETWORK_STATE` (provided by this
 * library's manifest when the AAR manifest is merged).
 */
class NetworkConnectivityMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val lock = Any()
    private val listeners = linkedSetOf<NetworkConnectivityListener>()
    private var registered = false

    @Volatile
    var current: NetworkConnectivity = NetworkConnectivity(state = NetworkConnectionState.INITIALIZING)
        private set

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            publish(readCurrent())
        }

        override fun onLosing(network: Network, maxMsToLive: Int) {
            val snapshot = readCurrent()
            if (snapshot.isConnected) {
                publish(snapshot.copy(state = NetworkConnectionState.LOSING, losingInMs = maxMsToLive))
            }
        }

        override fun onLost(network: Network) {
            publish(readCurrent())
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            publish(readCurrent())
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            publish(readCurrent())
        }
    }

    /** Adds an observer, immediately reports the current snapshot, and returns a closeable handle. */
    fun observe(listener: NetworkConnectivityListener): AutoCloseable {
        val shouldRegister: Boolean
        synchronized(lock) {
            listeners.add(listener)
            shouldRegister = !registered
            if (shouldRegister) registered = true
        }
        if (shouldRegister) registerPlatformCallbacks()
        val changed = publish(readCurrent())
        if (!changed) listener.onConnectivityChanged(current)

        return AutoCloseable {
            val shouldUnregister: Boolean
            synchronized(lock) {
                listeners.remove(listener)
                shouldUnregister = listeners.isEmpty() && registered
                if (shouldUnregister) registered = false
            }
            if (shouldUnregister) unregisterPlatformCallbacks()
        }
    }

    private fun registerPlatformCallbacks() {
        manager.registerDefaultNetworkCallback(callback)
    }

    private fun unregisterPlatformCallbacks() {
        manager.unregisterNetworkCallback(callback)
    }

    private fun readCurrent(): NetworkConnectivity {
        val network = manager.activeNetwork ?: return disconnected()
        val capabilities = manager.getNetworkCapabilities(network) ?: return disconnected()
        return NetworkConnectivity(
            state = NetworkConnectionState.CONNECTED,
            transports = capabilities.toTransports(),
            hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            isMetered = manager.isActiveNetworkMetered,
            link = manager.getLinkProperties(network)?.toDetails()
        )
    }

    private fun disconnected() = NetworkConnectivity(
        state = NetworkConnectionState.DISCONNECTED,
        isMetered = manager.isActiveNetworkMetered
    )

    private fun NetworkCapabilities.toTransports(): Set<NetworkTransport> = buildSet {
        if (hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add(NetworkTransport.WIFI)
        if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add(NetworkTransport.CELLULAR)
        if (hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add(NetworkTransport.ETHERNET)
        if (hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add(NetworkTransport.VPN)
        if (hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add(NetworkTransport.BLUETOOTH)
        if (isEmpty()) add(NetworkTransport.OTHER)
    }

    private fun LinkProperties.toDetails() = NetworkLinkDetails(
        interfaceName = interfaceName,
        addresses = linkAddresses.map { "${it.address.hostAddress}/${it.prefixLength}" },
        dnsServers = dnsServers.mapNotNull { it.hostAddress },
        routes = routes.map { route ->
            val gateway = route.gateway?.hostAddress ?: "on-link"
            "${route.destination} via $gateway${if (route.isDefaultRoute) " (default)" else ""}"
        },
        domains = domains,
        privateDnsActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isPrivateDnsActive else null,
        privateDnsServerName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) privateDnsServerName else null,
        mtu = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mtu else null
    )

    private fun publish(snapshot: NetworkConnectivity): Boolean {
        val observers: List<NetworkConnectivityListener>
        synchronized(lock) {
            if (snapshot == current) return false
            current = snapshot
            observers = listeners.toList()
        }
        observers.forEach { it.onConnectivityChanged(snapshot) }
        return true
    }
}
