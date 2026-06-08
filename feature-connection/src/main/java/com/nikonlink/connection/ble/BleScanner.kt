package com.nikonlink.connection.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/**
 * SnapBridge BLE Service UUID: 0000A000-0000-1000-8000-00805F9B34FB
 */
class BleScanner {

    companion object {
        val SNAPBIRDGE_SERVICE_UUID: UUID = UUID.fromString("0000A000-0000-1000-8000-00805F9B34FB")
        private const val SCAN_TIMEOUT_MS = 30_000L
    }

    data class ScanDevice(
        val device: BluetoothDevice,
        val name: String,
        val rssi: Int
    )

    fun scan(): Flow<List<ScanDevice>> = callbackFlow {
        val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            ?: run { close(Exception("Bluetooth not available")); return@callbackFlow }

        val scanner: BluetoothLeScanner = adapter.bluetoothLeScanner
            ?: run { close(Exception("BLE not supported")); return@callbackFlow }

        val discoveredDevices = mutableMapOf<String, ScanDevice>()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SNAPBIRDGE_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: "Unknown Nikon"
                discoveredDevices[device.address] = ScanDevice(
                    device = device,
                    name = name,
                    rssi = result.rssi
                )
                trySend(discoveredDevices.values.toList())
            }

            override fun onScanFailed(errorCode: Int) {
                close(Exception("BLE scan failed with code: $errorCode"))
            }
        }

        scanner.startScan(listOf(filter), settings, callback)
        trySend(emptyList())

        awaitClose {
            scanner.stopScan(callback)
        }
    }
}
