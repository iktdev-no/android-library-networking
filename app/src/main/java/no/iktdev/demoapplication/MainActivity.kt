package no.iktdev.demoapplication

import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.iktdev.demoapplication.databinding.ActivityMainBinding
import no.iktdev.networking.mdns.ndsDiscovery
import no.iktdev.networking.mdns.ndsDiscoveryListener
import java.net.InetAddress

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        val ioScope = CoroutineScope(Job() + Dispatchers.IO)

        ioScope.launch {
            val discovery = ndsDiscovery(this@MainActivity, "_streamit._tcp", object: ndsDiscoveryListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    TODO("Not yet implemented")
                }

                override fun onResolved(info: NsdServiceInfo) {
                    TODO("Not yet implemented")
                }

                override fun onLost(hostAddress: InetAddress, serviceName: String) {
                    TODO("Not yet implemented")
                }

                override fun onStartFailed(serviceType: String, errorCode: Int) {
                    TODO("Not yet implemented")
                }

            })
            discovery.startDiscovery()
        }
    }
}