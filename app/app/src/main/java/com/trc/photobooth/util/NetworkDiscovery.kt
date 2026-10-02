package com.trc.photobooth.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkDiscovery(private val context: Context) {

    private val tag = "NetworkDiscovery"
    private val serviceType = "_photobooth._tcp."

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false

    private val _discoveredHost = MutableStateFlow<String?>(null)
    val discoveredHost: StateFlow<String?> = _discoveredHost.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun startDiscovery() {
        if (isDiscovering) return

        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(tag, "NSD discovery started: $regType")
                _isSearching.value = true
                isDiscovering = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(tag, "Service found: ${service.serviceName}, type: ${service.serviceType}")
                if (service.serviceType.contains("photobooth") || service.serviceName.contains("Photobooth", ignoreCase = true)) {
                    try {
                        nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                Log.w(tag, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val host = serviceInfo.host?.hostAddress
                                val port = serviceInfo.port
                                Log.i(tag, "Resolved Photobooth Pi at $host:$port")
                                if (host != null) {
                                    _discoveredHost.value = "$host:$port"
                                    stopDiscovery()
                                }
                            }
                        })
                    } catch (e: Exception) {
                        Log.e(tag, "Error resolving service", e)
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(tag, "Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(tag, "Discovery stopped: $serviceType")
                _isSearching.value = false
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Discovery start failed: $errorCode")
                _isSearching.value = false
                isDiscovering = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Discovery stop failed: $errorCode")
                _isSearching.value = false
                isDiscovering = false
            }
        }

        try {
            nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(tag, "Failed to start NSD", e)
            _isSearching.value = false
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering) return
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.e(tag, "Error stopping NSD", e)
        } finally {
            isDiscovering = false
            _isSearching.value = false
            discoveryListener = null
        }
    }
}
