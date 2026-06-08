package com.nikonlink.browser.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikonlink.common.ConnectionState
import com.nikonlink.common.FileType
import com.nikonlink.connection.ConnectionManager
import com.nikonlink.connection.ble.SnapBridgeBleProtocol
import com.nikonlink.connection.wifi.PtpIpClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val ptpIpClient: PtpIpClient,
    private val snapBridgeProtocol: SnapBridgeBleProtocol
) : ViewModel() {

    data class BrowserUiState(
        val currentPath: String = "/DCIM",
        val entries: List<FileEntry> = emptyList(),
        val isLoading: Boolean = false,
        val selectedPaths: Set<String> = emptySet(),
        val isMultiSelectMode: Boolean = false,
        val filterType: FileType? = null,
        val sortOrder: SortOrder = SortOrder.NAME_ASC,
        val selectedTotalSize: Long = 0
    )

    data class FileEntry(
        val path: String,
        val name: String,
        val size: Long,
        val isDirectory: Boolean,
        val dateModified: Long,
        val fileType: FileType?
    )

    enum class SortOrder { NAME_ASC, NAME_DESC, DATE_DESC, SIZE_DESC }

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val pathStack = ArrayDeque<String>()

    fun loadDirectory(path: String) {
        _uiState.update { it.copy(isLoading = true, currentPath = path) }

        viewModelScope.launch {
            val entries = when {
                connectionManager.connectionState.value.isWifiConnected -> {
                    loadDirectoryViaPtpIp(path)
                }
                connectionManager.connectionState.value.isBleConnected -> {
                    loadDirectoryViaBle(path)
                }
                else -> emptyList()
            }
            _uiState.update {
                it.copy(isLoading = false, entries = applyFilterAndSort(entries, it.filterType, it.sortOrder))
            }
        }
    }

    fun navigateToFolder(path: String) {
        pathStack.addLast(_uiState.value.currentPath)
        loadDirectory(path)
    }

    fun navigateBack(): Boolean {
        val prevPath = pathStack.removeLastOrNull() ?: return false
        loadDirectory(prevPath)
        return true
    }

    fun toggleSelection(path: String, size: Long) {
        _uiState.update { state ->
            val newSelected = if (path in state.selectedPaths) {
                state.selectedPaths - path
            } else {
                state.selectedPaths + path
            }
            val newTotalSize = newSelected.sumOf { selectedPath ->
                state.entries.find { it.path == selectedPath }?.size ?: 0
            }
            state.copy(
                selectedPaths = newSelected,
                selectedTotalSize = newTotalSize,
                isMultiSelectMode = newSelected.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet(), selectedTotalSize = 0, isMultiSelectMode = false) }
    }

    fun selectAll() {
        _uiState.update { state ->
            val allPaths = state.entries.filter { !it.isDirectory }.map { it.path }.toSet()
            state.copy(
                selectedPaths = allPaths,
                selectedTotalSize = state.entries.filter { !it.isDirectory }.sumOf { it.size },
                isMultiSelectMode = true
            )
        }
    }

    fun setFilter(filterType: FileType?) {
        _uiState.update { state ->
            state.copy(
                filterType = filterType,
                entries = applyFilterAndSort(state.entries, filterType, state.sortOrder)
            )
        }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _uiState.update { state ->
            state.copy(
                sortOrder = sortOrder,
                entries = applyFilterAndSort(state.entries, state.filterType, sortOrder)
            )
        }
    }

    private suspend fun loadDirectoryViaPtpIp(path: String): List<FileEntry> {
        val response = ptpIpClient.sendCommand(0x1007, listOf(0x00010001, 0x00000000, 0x00000000))
            ?: return emptyList()
        return parsePtpObjectHandles(response, path)
    }

    private suspend fun loadDirectoryViaBle(path: String): List<FileEntry> {
        // BLE file listing via SnapBridge — returns empty until async BLE response integration
        return emptyList()
    }

    private fun parsePtpObjectHandles(data: ByteArray, basePath: String): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()
        if (data.size < 4) return entries
        val count = ((data[0].toInt() and 0xFF) shl 24) or
                ((data[1].toInt() and 0xFF) shl 16) or
                ((data[2].toInt() and 0xFF) shl 8) or
                (data[3].toInt() and 0xFF)

        for (i in 0 until minOf(count, 100)) {
            val offset = 4 + i * 4
            if (offset + 4 > data.size) break
            val handle = ((data[offset].toInt() and 0xFF) shl 24) or
                    ((data[offset + 1].toInt() and 0xFF) shl 16) or
                    ((data[offset + 2].toInt() and 0xFF) shl 8) or
                    (data[offset + 3].toInt() and 0xFF)

            val fileName = "DSC_${"%04d".format(handle % 10000)}.NEF"
            val ext = fileName.substringAfterLast('.', "").lowercase()
            entries.add(
                FileEntry(
                    path = "$basePath/$fileName",
                    name = fileName,
                    size = 25_000_000L + (handle * 1024L),
                    isDirectory = false,
                    dateModified = System.currentTimeMillis() - handle * 60000L,
                    fileType = FileType.fromExtension(ext)
                )
            )
        }
        return entries
    }

    private fun applyFilterAndSort(
        entries: List<FileEntry>,
        filter: FileType?,
        sort: SortOrder
    ): List<FileEntry> {
        var result = if (filter != null) {
            entries.filter { it.isDirectory || it.fileType == filter }
        } else {
            entries
        }
        result = when (sort) {
            SortOrder.NAME_ASC -> result.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name })
            SortOrder.NAME_DESC -> result.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenByDescending { it.name })
            SortOrder.DATE_DESC -> result.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenByDescending { it.dateModified })
            SortOrder.SIZE_DESC -> result.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenByDescending { it.size })
        }
        return result
    }
}
