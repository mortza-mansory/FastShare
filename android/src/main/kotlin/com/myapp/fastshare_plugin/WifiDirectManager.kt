package com.myapp.fastshare_plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Looper

class WifiDirectManager(private val context: Context, private val eventEmitter: (Map<String, Any?>) -> Unit) {

    private val wifiP2pManager: WifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: WifiP2pManager.Channel = wifiP2pManager.initialize(context, Looper.getMainLooper(), null)
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private var peers: MutableList<WifiP2pDevice> = mutableListOf()
    private var isWifiDirectEnabled = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    isWifiDirectEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    eventEmitter(mapOf("event" to "wifiDirectStateChanged", "enabled" to isWifiDirectEnabled))
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    wifiP2pManager.requestPeers(channel) { deviceList ->
                        peers.clear()
                        peers.addAll(deviceList.deviceList)
                        val peerList = peers.map { device ->
                            mapOf(
                                "deviceName" to device.deviceName,
                                "deviceAddress" to device.deviceAddress,
                                "primaryDeviceType" to device.primaryDeviceType,
                                "secondaryDeviceType" to device.secondaryDeviceType,
                                "status" to device.status
                            )
                        }
                        eventEmitter(mapOf("event" to "wifiDirectPeersChanged", "peers" to peerList))
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    wifiP2pManager.requestConnectionInfo(channel) { info ->
                        eventEmitter(mapOf(
                            "event" to "wifiDirectConnectionChanged",
                            "isGroupOwner" to info.isGroupOwner,
                            "groupOwnerAddress" to info.groupOwnerAddress?.hostAddress,
                            "groupFormed" to info.groupFormed
                        ))
                    }
                }
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    device?.let {
                        eventEmitter(mapOf(
                            "event" to "wifiDirectThisDeviceChanged",
                            "deviceName" to it.deviceName,
                            "deviceAddress" to it.deviceAddress,
                            "status" to it.status
                        ))
                    }
                }
            }
        }
    }

    fun startWifiDirect() {
        Logger.debug("WIFI_DIRECT", "Starting Wi-Fi Direct")
        context.registerReceiver(receiver, intentFilter)
        eventEmitter(mapOf("event" to "wifiDirectStarted"))
    }

    fun stopWifiDirect() {
        Logger.debug("WIFI_DIRECT", "Stopping Wi-Fi Direct")
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            Logger.error("WIFI_DIRECT", "Error unregistering receiver: ${e.message}")
        }
        wifiP2pManager.cancelConnect(channel, null)
        wifiP2pManager.removeGroup(channel, null)
        eventEmitter(mapOf("event" to "wifiDirectStopped"))
    }

    fun discoverPeers() {
        Logger.debug("WIFI_DIRECT", "Discovering Wi-Fi Direct peers")
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Logger.success("WIFI_DIRECT", "Peer discovery started")
                eventEmitter(mapOf("event" to "wifiDirectDiscoveryStarted"))
            }

            override fun onFailure(reason: Int) {
                Logger.error("WIFI_DIRECT", "Peer discovery failed: $reason")
                eventEmitter(mapOf("event" to "wifiDirectDiscoveryFailed", "reason" to reason))
            }
        })
    }

    fun connectToPeer(deviceAddress: String) {
        Logger.debug("WIFI_DIRECT", "Connecting to peer: $deviceAddress")
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Logger.success("WIFI_DIRECT", "Connection initiated")
                eventEmitter(mapOf("event" to "wifiDirectConnectionInitiated"))
            }

            override fun onFailure(reason: Int) {
                Logger.error("WIFI_DIRECT", "Connection failed: $reason")
                eventEmitter(mapOf("event" to "wifiDirectConnectionFailed", "reason" to reason))
            }
        })
    }

    fun getPeers(): List<Map<String, Any?>> {
        return peers.map { device ->
            mapOf(
                "deviceName" to device.deviceName,
                "deviceAddress" to device.deviceAddress,
                "primaryDeviceType" to device.primaryDeviceType,
                "secondaryDeviceType" to device.secondaryDeviceType,
                "status" to device.status
            )
        }
    }

    fun isEnabled(): Boolean = isWifiDirectEnabled
}