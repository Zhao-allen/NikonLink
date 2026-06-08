package com.nikonlink.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikonlink.common.ConnectionState
import com.nikonlink.connection.ConnectionManager
import com.nikonlink.connection.ble.BleScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val connectionManager: ConnectionManager
) : ViewModel() {

    data class CameraUiState(
        val connectionState: ConnectionState = ConnectionState.Disconnected,
        val discoveredCameras: List<BleScanner.ScanDevice> = emptyList(),
        val isScanning: Boolean = false,
        val connectedCameraName: String? = null,
        val wifiSsid: String = "",
        val wifiPassword: String = ""
    )

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectionManager.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state, isScanning = state == ConnectionState.BLEScanning) }
            }
        }
        viewModelScope.launch {
            connectionManager.discoveredCameras.collect { cameras ->
                _uiState.update { it.copy(discoveredCameras = cameras) }
            }
        }
    }

    fun startScanning() {
        connectionManager.startScanning()
    }

    fun stopScanning() {
        connectionManager.stopScanning()
    }

    fun connectToCamera(device: BleScanner.ScanDevice) {
        _uiState.update { it.copy(connectedCameraName = device.name) }
        connectionManager.connectToCamera(device.device)
    }

    fun connectViaWifi(ssid: String, password: String) {
        _uiState.update { it.copy(wifiSsid = ssid, wifiPassword = password) }
        connectionManager.requestWifiConnection(ssid, password)
    }

    /** Direct PTP-IP connection -- use when phone is already on camera WiFi. */
    fun connectPtpIpDirectly() {
        _uiState.update { it.copy(connectedCameraName = "Camera (WiFi)") }
        connectionManager.connectPtpIpDirectly()
    }

    fun disconnect() {
        _uiState.update { it.copy(connectedCameraName = null) }
        connectionManager.disconnectAll()
    }

    fun updateWifiSsid(ssid: String) {
        _uiState.update { it.copy(wifiSsid = ssid) }
    }

    fun updateWifiPassword(password: String) {
        _uiState.update { it.copy(wifiPassword = password) }
    }
}
