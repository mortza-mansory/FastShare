package com.myapp.fastshare_plugin

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import java.net.Inet4Address
import java.net.NetworkInterface

class HotspotManager(private val context: Context) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var hotspotCallback: WifiManager.LocalOnlyHotspotCallback? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var hotspotInfo: HotspotInfo? = null
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    data class HotspotInfo(val ssid: String, val password: String, val ip: String, val port: Int = 8080)

    fun startHotspot(onSuccess: (HotspotInfo) -> Unit, onFailure: (String) -> Unit) {
        Logger.debug("HOTSPOT_START", "Attempting to start LocalOnlyHotspot")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Logger.verbose("HOTSPOT_COMPAT", "Android ${Build.VERSION.SDK_INT} supports LocalOnlyHotspot")
            startLocalOnlyHotspot(onSuccess, onFailure)
        } else {
            Logger.error("HOTSPOT_UNSUPPORTED", "LocalOnlyHotspot not supported on Android ${Build.VERSION.SDK_INT}")
            onFailure("LocalOnlyHotspot not supported on this Android version")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startLocalOnlyHotspot(onSuccess: (HotspotInfo) -> Unit, onFailure: (String) -> Unit) {
        Logger.debug("HOTSPOT_CALLBACK", "Setting up LocalOnlyHotspot callback")
        hotspotCallback = object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                Logger.success("HOTSPOT_SUCCESS", "LocalOnlyHotspot started successfully")
                this@HotspotManager.reservation = reservation
                val config = reservation.wifiConfiguration
                val ssid = config?.SSID ?: "FastShare_${System.currentTimeMillis() % 1000}"
                val password = config?.preSharedKey ?: generatePassword()
                val ip = getHotspotIp()
                val port = 8080
                hotspotInfo = HotspotInfo(ssid, password, ip, port)
                Logger.debug("HOTSPOT_CONFIG", "SSID: $ssid, IP: $ip, Port: $port")
                onSuccess(hotspotInfo!!)
            }

            override fun onStopped() {
                Logger.warning("HOTSPOT_STOPPED", "LocalOnlyHotspot stopped")
                hotspotInfo = null
                reservation = null
            }

            override fun onFailed(reason: Int) {
                Logger.error("HOTSPOT_FAILED", "LocalOnlyHotspot failed with reason: $reason")
                onFailure("Hotspot failed: $reason")
                reservation = null
            }
        }
        Logger.verbose("HOTSPOT_REQUEST", "Requesting LocalOnlyHotspot from WifiManager")
        wifiManager.startLocalOnlyHotspot(hotspotCallback, Handler(Looper.getMainLooper()))
    }

    fun stopHotspot() {
        Logger.debug("HOTSPOT_STOP", "Stopping LocalOnlyHotspot")
        reservation?.close()
        reservation = null
        hotspotInfo = null
        Logger.success("HOTSPOT_STOPPED", "LocalOnlyHotspot stopped successfully")
    }

    fun connectToHotspot(ssid: String, password: String, onConnected: () -> Unit, onFailure: (String) -> Unit) {
        Logger.debug("HOTSPOT_CONNECT", "Connecting to hotspot: $ssid")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Logger.verbose("HOTSPOT_CONNECT_Q", "Using NetworkSpecifier for Android ${Build.VERSION.SDK_INT}")
            connectWithNetworkSpecifier(ssid, password, onConnected, onFailure)
        } else {
            Logger.verbose("HOTSPOT_CONNECT_LEGACY", "Using legacy WiFi connection for Android ${Build.VERSION.SDK_INT}")
            connectLegacy(ssid, password, onConnected, onFailure)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectWithNetworkSpecifier(ssid: String, password: String, onConnected: () -> Unit, onFailure: (String) -> Unit) {
        Logger.debug("HOTSPOT_SPECIFIER", "Creating WifiNetworkSpecifier for SSID: $ssid")
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        Logger.verbose("HOTSPOT_CALLBACK_SETUP", "Setting up NetworkCallback for connection")
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Logger.success("HOTSPOT_CONNECTED", "Successfully connected to hotspot network")
                connectivityManager.bindProcessToNetwork(network)
                onConnected()
            }

            override fun onUnavailable() {
                Logger.error("HOTSPOT_CONNECT_FAIL", "Failed to connect to hotspot network")
                onFailure("Failed to connect to hotspot")
            }
        }
        Logger.debug("HOTSPOT_REQUEST_NETWORK", "Requesting network connection")
        connectivityManager.requestNetwork(request, networkCallback!!)
    }

    private fun connectLegacy(ssid: String, password: String, onConnected: () -> Unit, onFailure: (String) -> Unit) {
        Logger.debug("HOTSPOT_LEGACY_CONFIG", "Creating WifiConfiguration for SSID: $ssid")
        val config = WifiConfiguration()
        config.SSID = "\"$ssid\""
        config.preSharedKey = "\"$password\""
        val netId = wifiManager.addNetwork(config)
        if (netId != -1) {
            Logger.debug("HOTSPOT_LEGACY_ENABLE", "Enabling network with ID: $netId")
            wifiManager.enableNetwork(netId, true)
            wifiManager.reconnect()
            Logger.verbose("HOTSPOT_LEGACY_DELAY", "Waiting 5 seconds for connection")
            // Assume connected after a delay
            Handler(Looper.getMainLooper()).postDelayed({
                Logger.success("HOTSPOT_LEGACY_CONNECTED", "Legacy connection assumed successful")
                onConnected()
            }, 5000)
        } else {
            Logger.error("HOTSPOT_LEGACY_FAIL", "Failed to add network configuration")
            onFailure("Failed to add network")
        }
    }

    fun disconnectFromHotspot() {
        Logger.debug("HOTSPOT_DISCONNECT", "Disconnecting from hotspot")
        networkCallback?.let {
            Logger.verbose("HOTSPOT_UNREGISTER_CALLBACK", "Unregistering network callback")
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(it)
        }
        wifiManager.disconnect()
        Logger.success("HOTSPOT_DISCONNECTED", "Successfully disconnected from hotspot")
    }

    private fun getHotspotIp(): String {
        Logger.debug("HOTSPOT_IP_DETECT", "Detecting hotspot IP address")
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                Logger.verbose("HOTSPOT_IP_INTERFACE", "Checking interface: ${intf.name}")
                if (intf.name.contains("wlan") || intf.name.contains("ap")) {
                    val addresses = intf.inetAddresses
                    for (addr in addresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val ip = addr.hostAddress ?: "192.168.43.1"
                            Logger.success("HOTSPOT_IP_FOUND", "Hotspot IP detected: $ip")
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.error("HOTSPOT_IP_ERROR", "Error detecting hotspot IP: ${e.message}")
            e.printStackTrace()
        }
        Logger.warning("HOTSPOT_IP_DEFAULT", "Using default hotspot IP: 192.168.43.1")
        return "192.168.43.1" // Default hotspot IP
    }

    private fun generatePassword(): String {
        val password = "FastShare" + (100000 + (Math.random() * 900000).toInt())
        Logger.debug("HOTSPOT_PASSWORD_GEN", "Generated hotspot password")
        return password
    }
}