package com.myapp.fastshare_plugin

import android.content.Context
import android.content.Intent
import android.location.LocationManager
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
import android.provider.Settings
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
                val ssid = config?.SSID ?: "AndroidShare"
                val password = config?.preSharedKey ?: generatePassword()
                val ip = getHotspotIp()
                val port = 8080
                hotspotInfo = HotspotInfo(ssid, password, ip, port)
                Logger.debug("HOTSPOT_CONFIG", "SSID: $ssid, Password: $password, IP: $ip, Port: $port")
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
        hotspotCallback = null
        networkCallback = null
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
                // Open WiFi settings for manual connection
                try {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Logger.error("HOTSPOT_SETTINGS_FAIL", "Failed to open WiFi settings: ${e.message}")
                }
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

    fun enableWifi(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        Logger.debug("HOTSPOT_ENABLE_WIFI", "Enabling WiFi")
        try {
            if (!wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
                // Check if WiFi was actually enabled
                if (wifiManager.isWifiEnabled) {
                    Logger.success("HOTSPOT_WIFI_ENABLED", "WiFi enabled successfully")
                    onSuccess()
                } else {
                    Logger.warning("HOTSPOT_WIFI_ENABLE_FAILED", "Failed to enable WiFi programmatically, opening settings")
                    // Open WiFi settings for user to enable manually
                    try {
                        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        onFailure("Please enable WiFi in settings and try again")
                    } catch (e: Exception) {
                        Logger.error("HOTSPOT_WIFI_SETTINGS_FAIL", "Failed to open WiFi settings: ${e.message}")
                        onFailure("Failed to enable WiFi and couldn't open settings: ${e.message}")
                    }
                }
            } else {
                Logger.debug("HOTSPOT_WIFI_ALREADY_ENABLED", "WiFi is already enabled")
                onSuccess()
            }
        } catch (e: Exception) {
            Logger.error("HOTSPOT_ENABLE_WIFI_ERROR", "Error enabling WiFi: ${e.message}")
            // Try to open settings as fallback
            try {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onFailure("Failed to enable WiFi programmatically. Please enable WiFi in settings and try again")
            } catch (settingsException: Exception) {
                onFailure("Failed to enable WiFi: ${e.message}")
            }
        }
    }

    fun scanHotspots(onSuccess: (List<Map<String, Any>>) -> Unit, onFailure: (String) -> Unit) {
        Logger.debug("HOTSPOT_SCAN", "Starting WiFi scan for hotspots")

        // Check if location services are enabled (required for WiFi scanning)
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Logger.warning("HOTSPOT_LOCATION_DISABLED", "Location services are disabled, cannot scan WiFi")
            // Open location settings
            try {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onFailure("Location services are disabled. Please enable location services to scan for hotspots.")
            } catch (e: Exception) {
                Logger.error("HOTSPOT_LOCATION_SETTINGS_FAIL", "Failed to open location settings: ${e.message}")
                onFailure("Location services are disabled and settings could not be opened. Please enable location services manually.")
            }
            return
        }

        try {
            // Start a new scan
            val success = wifiManager.startScan()
            if (!success) {
                Logger.warning("HOTSPOT_SCAN_START_FAILED", "Failed to start scan, using cached results")
            }
            // Get current results (may include new ones if scan started)
            val scanResults = wifiManager.scanResults
            val hotspots = scanResults.filter { result ->
                result.SSID.startsWith("AndroidShare_")
            }.map { result ->
                mapOf(
                    "ssid" to result.SSID,
                    "bssid" to result.BSSID,
                    "level" to result.level
                )
            }
            Logger.success("HOTSPOT_SCAN_SUCCESS", "Found ${hotspots.size} hotspots")
            onSuccess(hotspots)
        } catch (e: Exception) {
            Logger.error("HOTSPOT_SCAN_ERROR", "Error scanning hotspots: ${e.message}")
            onFailure("Failed to scan hotspots: ${e.message}")
        }
    }

    private fun generatePassword(): String {
        // Use a fixed password for simplicity
        val password = "12345678"
        Logger.debug("HOTSPOT_PASSWORD_GEN", "Generated hotspot password: $password")
        return password
    }
}