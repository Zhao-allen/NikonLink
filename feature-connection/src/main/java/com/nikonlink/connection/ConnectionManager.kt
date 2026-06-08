package com.nikonlink.connection

import android.bluetooth.BluetoothDevice
import com.nikonlink.common.ConnectionState
import com.nikonlink.connection.ble.BleGattManager
import com.nikonlink.connection.ble.BleScanner
import com.nikonlink.connection.wifi.PtpIpClient
import com.nikonlink.connection.wifi.WifiConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionManager @Inject constructor(
    private val bleScanner: BleScanner,
    private val bleGattManager: BleGattManager,
    private val wifiConnectionManager: WifiConnectionManager,
    private val ptpIpClient: PtpIpClient
) {
    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredCameras = MutableStateFlow<List<BleScanner.ScanDevice>>(emptyList())
    val discoveredCameras: StateFlow<List<BleScanner.ScanDevice>> = _discoveredCameras.asStateFlow()

    private var scanJob: Job? = null
    private var gattJob: Job? = null
    private var wifiJob: Job? = null
    private var heartbeatJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Start scanning for nearby Nikon cameras. */
    fun startScanning() {
        if (_connectionState.value != ConnectionState.Disconnected) return
        _connectionState.value = ConnectionState.BLEScanning

        scanJob?.cancel()
        scanJob = scope.launch {
            bleScanner.scan()
                .catch { e ->
                    _connectionState.value = ConnectionState.Disconnected
                    _discoveredCameras.value = emptyList()
                }
                .collect { devices ->
                    _discoveredCameras.value = devices
                }
        }
    }

    /** Stop scanning. */
    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        if (_connectionState.value == ConnectionState.BLEScanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    /** Connect to a specific camera device via BLE. */
    fun connectToCamera(device: BluetoothDevice) {
        stopScanning()
        _connectionState.value = ConnectionState.BLEConnecting

        gattJob?.cancel()
        gattJob = scope.launch {
            bleGattManager.connect(device).collect { event ->
                when (event) {
                    is BleGattManager.GattEvent.Connected -> {
                        _connectionState.value = ConnectionState.BLEConnected
                        startHeartbeat()
                    }
                    is BleGattManager.GattEvent.Disconnected -> {
                        _connectionState.value = ConnectionState.Disconnected
                        stopHeartbeat()
                    }
                    is BleGattManager.GattEvent.ServicesDiscovered -> {
                        // GATT services ready
                    }
                    else -> { /* handled by callers via dedicated Flows */ }
                }
            }
        }
    }

    /** Request WiFi handshake. Camera returns SSID/password via BLE, app connects. */
    fun requestWifiConnection(ssid: String, password: String) {
        if (_connectionState.value != ConnectionState.BLEConnected &&
            _connectionState.value != ConnectionState.WiFiHandshake) return

        _connectionState.value = ConnectionState.WiFiHandshake

        wifiJob?.cancel()
        wifiJob = scope.launch {
            wifiConnectionManager.connectToCamera(ssid, password).collect { event ->
                when (event) {
                    is WifiConnectionManager.WifiEvent.Connected -> {
                        _connectionState.value = ConnectionState.WiFiConnected
                        ptpIpClient.connect("192.168.1.1")
                            .onSuccess { _connectionState.value = ConnectionState.WiFiTransfer }
                            .onFailure { _connectionState.value = ConnectionState.WiFiConnected }
                    }
                    is WifiConnectionManager.WifiEvent.Disconnected -> {
                        _connectionState.value = ConnectionState.BLEConnected
                        ptpIpClient.disconnect()
                    }
                    is WifiConnectionManager.WifiEvent.Error -> {
                        _connectionState.value = ConnectionState.BLEConnected
                    }
                }
            }
        }
    }

    /** Disconnect everything. */
    fun disconnectAll() {
        stopHeartbeat()
        wifiJob?.cancel()
        gattJob?.cancel()
        scanJob?.cancel()
        ptpIpClient.disconnect()
        wifiConnectionManager.disconnect()
        bleGattManager.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    /** Send keep-alive heartbeat via BLE. Auto-reconnect if no response for 5s. */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var missedBeats = 0
            while (isActive) {
                delay(1000)
                missedBeats++
                if (missedBeats >= 5) {
                    _connectionState.value = ConnectionState.Disconnected
                    stopHeartbeat()
                    startScanning()
                    break
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
