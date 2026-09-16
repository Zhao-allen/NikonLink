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
                        binding.tvConnectionStatus.text = getString(R.string.status_disconnected)
                        binding.tvConnectionStatus.setTextColor(0xFF888888.toInt())
                        if (state.lastError != null) {
                            binding.tvStatusTitle.text = getString(R.string.status_connection_failed)
                            binding.tvStatusDetail.text = state.lastError
                            binding.tvStatusDetail.setTextColor(0xFFE74C3C.toInt())
                            binding.btnRetry.visibility = View.VISIBLE
                        } else if (state.isOnCameraWifi) {
                            binding.tvStatusTitle.text = getString(R.string.status_on_camera_wifi, state.currentWifiSsid ?: "")
                            binding.tvStatusDetail.text = getString(R.string.ready_to_connect)
                            binding.tvStatusDetail.setTextColor(0xFF27AE60.toInt())
                            binding.btnConnect.visibility = View.VISIBLE
                        } else {
                            binding.tvStatusTitle.text = getString(R.string.connect_to_camera_wifi)
                            binding.tvStatusDetail.text = getString(R.string.phone_not_on_camera_wifi)
                            binding.tvStatusDetail.setTextColor(0xFF999999.toInt())
                        }
                    }
                    ConnectionState.WiFiConnecting -> {
                        binding.tvConnectionStatus.text = getString(R.string.status_connecting)
                        binding.tvConnectionStatus.setTextColor(0xFFF5A623.toInt())
                        binding.tvStatusTitle.text = getString(R.string.connecting_to_camera)
                        binding.tvStatusDetail.text = getString(R.string.attempting_ptp_ip, state.connectedCameraName ?: "192.168.1.1")
                        binding.tvStatusDetail.setTextColor(0xFF999999.toInt())
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    ConnectionState.WiFiTransfer -> {
                        binding.tvConnectionStatus.text = getString(R.string.status_connected)
                        binding.tvConnectionStatus.setTextColor(0xFF27AE60.toInt())
                        binding.tvStatusTitle.text = getString(R.string.status_connected)
                        binding.tvStatusDetail.text = getString(R.string.camera_connected_browse)
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
