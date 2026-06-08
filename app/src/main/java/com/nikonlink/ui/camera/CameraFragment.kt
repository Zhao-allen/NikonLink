package com.nikonlink.ui.camera

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.nikonlink.R
import com.nikonlink.common.ConnectionState
import com.nikonlink.databinding.FragmentCameraBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CameraFragment : Fragment(R.layout.fragment_camera) {

    private val viewModel: CameraViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCameraBinding.bind(view)

        binding.btnConnect.setOnClickListener {
            val ip = binding.etCameraIp.text.toString().trim().ifEmpty { "192.168.1.1" }
            viewModel.connectPtpIpDirectly(ip)
        }

        binding.btnRetry.setOnClickListener {
            val ip = binding.etCameraIp.text.toString().trim().ifEmpty { "192.168.1.1" }
            viewModel.connectPtpIpDirectly(ip)
        }

        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.events.collect { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.progressBar.visibility = View.GONE
                binding.btnConnect.visibility = View.GONE
                binding.btnRetry.visibility = View.GONE
                binding.btnDisconnect.visibility = View.GONE

                when (state.connectionState) {
                    ConnectionState.Disconnected -> {
                        binding.tvConnectionStatus.text = "Disconnected"
                        binding.tvConnectionStatus.setTextColor(0xFF888888.toInt())
                        if (state.lastError != null) {
                            binding.tvStatusTitle.text = "Connection Failed"
                            binding.tvStatusDetail.text = state.lastError
                            binding.tvStatusDetail.setTextColor(0xFFE74C3C.toInt())
                            binding.btnRetry.visibility = View.VISIBLE
                        } else if (state.isOnCameraWifi) {
                            binding.tvStatusTitle.text = "On Camera WiFi: ${state.currentWifiSsid ?: ""}"
                            binding.tvStatusDetail.text = "Ready to connect. Tap CONNECT below."
                            binding.tvStatusDetail.setTextColor(0xFF27AE60.toInt())
                            binding.btnConnect.visibility = View.VISIBLE
                        } else {
                            binding.tvStatusTitle.text = "Connect to Camera WiFi"
                            binding.tvStatusDetail.text = "Phone is not on camera WiFi.\nGo to Settings → Wi-Fi → Connect to your camera's network."
                            binding.tvStatusDetail.setTextColor(0xFF999999.toInt())
                        }
                    }
                    ConnectionState.WiFiConnecting -> {
                        binding.tvConnectionStatus.text = "Connecting..."
                        binding.tvConnectionStatus.setTextColor(0xFFF5A623.toInt())
                        binding.tvStatusTitle.text = "Connecting to Camera"
                        binding.tvStatusDetail.text = "Attempting PTP-IP to ${state.connectedCameraName ?: "192.168.1.1"}..."
                        binding.tvStatusDetail.setTextColor(0xFF999999.toInt())
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    ConnectionState.WiFiTransfer -> {
                        binding.tvConnectionStatus.text = "Connected"
                        binding.tvConnectionStatus.setTextColor(0xFF27AE60.toInt())
                        binding.tvStatusTitle.text = "Connected"
                        binding.tvStatusDetail.text = "Camera connected! Switch to Browse tab to view files."
                        binding.tvStatusDetail.setTextColor(0xFF27AE60.toInt())
                        binding.btnDisconnect.visibility = View.VISIBLE
                    }
                    else -> {
                        binding.tvConnectionStatus.text = state.connectionState.name
                    }
                }
            }
        }

        // Auto-check WiFi on start
        viewModel.refreshWifiState()
    }
}
