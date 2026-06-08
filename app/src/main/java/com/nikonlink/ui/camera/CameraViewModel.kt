package com.nikonlink.ui.camera

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikonlink.common.ConnectionState
import com.nikonlink.connection.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    data class CameraUiState(
        val connectionState: ConnectionState = ConnectionState.Disconnected,
        val connectedCameraName: String? = null,
        val lastError: String? = null,
        val isOnCameraWifi: Boolean = false,
        val currentWifiSsid: String? = null
    )

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            connectionManager.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
        viewModelScope.launch {
            connectionManager.lastError.collect { error ->
                _uiState.update { it.copy(lastError = error) }
                if (error != null) {
                    _events.emit("Error: $error")
                }
            }
        }
        detectCameraWifi()
    }

    fun connectPtpIpDirectly(ip: String = "192.168.1.1") {
        _uiState.update { it.copy(connectedCameraName = ip) }
        connectionManager.connectPtpIpDirectly(ip)
    }

    fun disconnect() {
        _uiState.update { it.copy(connectedCameraName = null, lastError = null) }
        connectionManager.disconnectAll()
    }

    fun refreshWifiState() {
        detectCameraWifi()
    }

    private fun detectCameraWifi() {
        try {
            val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wifiManager?.connectionInfo
            val ssid = info?.ssid?.trim('"') ?: ""
            val ip = wifiManager?.dhcpInfo?.ipAddress ?: 0
            val ipStr = String.format("%d.%d.%d.%d",
                ip and 0xFF, (ip shr 8) and 0xFF, (ip shr 16) and 0xFF, (ip shr 24) and 0xFF)
            val isCameraWifi = ipStr.startsWith("192.168.1.") || ssid.contains("NIKON", true) ||
                    ssid.contains("CCCC", true) || ipStr.startsWith("169.254.")
            _uiState.update { it.copy(isOnCameraWifi = isCameraWifi, currentWifiSsid = ssid) }
        } catch (e: Exception) {
            Log.w("NikonLink", "WiFi detect failed: ${e.message}")
        }
    }
}
