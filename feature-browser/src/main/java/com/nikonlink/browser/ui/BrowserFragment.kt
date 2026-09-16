package com.nikonlink.browser.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nikonlink.browser.R
import com.nikonlink.browser.databinding.FragmentBrowserBinding
import com.nikonlink.common.ConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BrowserFragment : Fragment() {

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BrowserViewModel by viewModels()
    private lateinit var adapter: FolderListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FolderListAdapter(
            onFolderClick = { entry -> viewModel.navigateToFolder(entry.path) },
            onFileClick = { entry -> viewModel.toggleSelection(entry.path, entry.size) },
            onLongClick = { entry ->
                if (!viewModel.uiState.value.isMultiSelectMode) {
                    viewModel.toggleSelection(entry.path, entry.size)
                }
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnBack.setOnClickListener { viewModel.navigateBack() }
        binding.btnSelectAll.setOnClickListener { viewModel.selectAll() }
        binding.btnTransfer.setOnClickListener {
            viewModel.clearSelection()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                adapter.submitList(state.entries)
                adapter.updateSelectedPaths(state.selectedPaths)

                binding.tvPath.text = state.currentPath
                binding.selectionBar.visibility = if (state.isMultiSelectMode) View.VISIBLE else View.GONE
                binding.tvSelectedCount.text = getString(R.string.selected_count, state.selectedPaths.size, FolderListAdapter.formatSize(state.selectedTotalSize))
            }
        }

        // Only load when connected
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.connectionState.collect { state ->
                if (state == ConnectionState.WiFiTransfer) {
                    viewModel.loadDirectory("/DCIM")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
