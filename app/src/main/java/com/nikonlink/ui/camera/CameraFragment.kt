package com.nikonlink.ui.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nikonlink.R
import com.nikonlink.common.ConnectionState
import com.nikonlink.connection.ble.BleScanner
import com.nikonlink.databinding.FragmentCameraBinding
import com.nikonlink.databinding.ItemCameraDeviceBinding
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CameraFragment : Fragment(R.layout.fragment_camera) {

    private val viewModel: CameraViewModel by viewModels()
    private val adapter = CameraDeviceAdapter { device -> viewModel.connectToCamera(device) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCameraBinding.bind(view)

        binding.recyclerViewCameras.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewCameras.adapter = adapter

        binding.btnScan.setOnClickListener {
            when (viewModel.uiState.value.isScanning) {
                true -> viewModel.stopScanning()
                false -> viewModel.startScanning()
            }
        }

        binding.btnDirectConnect.setOnClickListener {
            viewModel.connectPtpIpDirectly()
        }

        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                // Update scan button
                binding.btnScan.text = if (state.isScanning) "SCANNING... (tap to stop)" else "SCAN FOR CAMERAS"

                // Update status card
                when (state.connectionState) {
                    ConnectionState.Disconnected -> {
                        binding.tvConnectionStatus.text = "Disconnected"
                        binding.tvConnectionStatus.setTextColor(0xFF888888.toInt())
                        binding.tvStatusTitle.text = "Not Connected"
                        binding.tvStatusDetail.text = "Scan for Nikon cameras or connect via WiFi"
                        binding.btnDirectConnect.visibility = View.VISIBLE  // Always show -- user may be on WiFi
                        binding.btnDisconnect.visibility = View.GONE
                    }
                    ConnectionState.BLEScanning -> {
                        binding.tvConnectionStatus.text = "Scanning..."
                        binding.tvConnectionStatus.setTextColor(0xFFF5A623.toInt())
                        binding.tvStatusTitle.text = "Scanning for Cameras"
                        binding.tvStatusDetail.text = "Looking for Nikon cameras nearby..."
                        binding.btnDirectConnect.visibility = View.GONE
                        binding.btnDisconnect.visibility = View.GONE
                    }
                    ConnectionState.BLEConnecting -> {
                        binding.tvConnectionStatus.text = "Connecting..."
                        binding.tvConnectionStatus.setTextColor(0xFFF5A623.toInt())
                        binding.tvStatusTitle.text = "Connecting to ${state.connectedCameraName ?: "Camera"}"
                        binding.tvStatusDetail.text = "Establishing Bluetooth connection..."
                        binding.btnDirectConnect.visibility = View.GONE
                        binding.btnDisconnect.visibility = View.GONE
                    }
                    ConnectionState.BLEConnected -> {
                        binding.tvConnectionStatus.text = "BLE Connected"
                        binding.tvConnectionStatus.setTextColor(0xFF27AE60.toInt())
                        binding.tvStatusTitle.text = "Bluetooth Connected"
                        binding.tvStatusDetail.text = "Select files to transfer or use Remote. Tap SCAN to switch WiFi."
                        binding.btnDirectConnect.visibility = View.GONE
                        binding.btnDisconnect.visibility = View.VISIBLE
                    }
                    ConnectionState.WiFiHandshake, ConnectionState.WiFiConnecting -> {
                        binding.tvConnectionStatus.text = "WiFi..."
                        binding.tvConnectionStatus.setTextColor(0xFFF5A623.toInt())
                        binding.tvStatusTitle.text = "Connecting WiFi"
                        binding.tvStatusDetail.text = "Switching to camera WiFi network..."
                        binding.btnDirectConnect.visibility = View.GONE
                        binding.btnDisconnect.visibility = View.GONE
                    }
                    ConnectionState.WiFiConnected -> {
                        binding.tvConnectionStatus.text = "WiFi Connected"
                        binding.tvConnectionStatus.setTextColor(0xFF27AE60.toInt())
                        binding.tvStatusTitle.text = "WiFi Connected"
                        binding.tvStatusDetail.text = "Opening PTP-IP connection..."
                        binding.btnDirectConnect.visibility = View.GONE
                        binding.btnDisconnect.visibility = View.VISIBLE
                    }
                    ConnectionState.WiFiTransfer -> {
                        binding.tvConnectionStatus.text = "Ready"
                        binding.tvConnectionStatus.setTextColor(0xFF27AE60.toInt())
                        binding.tvStatusTitle.text = "Connected — High Speed Ready"
                        binding.tvStatusDetail.text = "You can now browse files, transfer, and control the camera."
                        binding.btnDirectConnect.visibility = View.GONE
                        binding.btnDisconnect.visibility = View.VISIBLE
                    }
                }

                // Update camera list
                adapter.submitList(state.discoveredCameras)
                binding.tvEmptyHint.visibility = if (state.discoveredCameras.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}

class CameraDeviceAdapter(
    private val onConnect: (BleScanner.ScanDevice) -> Unit
) : RecyclerView.Adapter<CameraDeviceAdapter.ViewHolder>() {

    private var devices: List<BleScanner.ScanDevice> = emptyList()

    fun submitList(list: List<BleScanner.ScanDevice>) {
        devices = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCameraDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    inner class ViewHolder(private val binding: ItemCameraDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: BleScanner.ScanDevice) {
            binding.tvCameraName.text = device.name
            binding.tvCameraRssi.text = "Signal: ${device.rssi} dBm  |  ${device.device.address}"
            binding.root.setOnClickListener { onConnect(device) }
        }
    }
}
