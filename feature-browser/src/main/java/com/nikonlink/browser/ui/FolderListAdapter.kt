package com.nikonlink.browser.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nikonlink.browser.databinding.ItemFileEntryBinding
import com.nikonlink.common.FileType

class FolderListAdapter(
    private val onFolderClick: (BrowserViewModel.FileEntry) -> Unit,
    private val onFileClick: (BrowserViewModel.FileEntry) -> Unit,
    private val onLongClick: (BrowserViewModel.FileEntry) -> Unit
) : ListAdapter<BrowserViewModel.FileEntry, FolderListAdapter.ViewHolder>(DiffCallback) {

    private var selectedPaths: Set<String> = emptySet()

    fun updateSelectedPaths(paths: Set<String>) {
        selectedPaths = paths
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFileEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        holder.bind(entry, entry.path in selectedPaths)
    }

    inner class ViewHolder(
        private val binding: ItemFileEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: BrowserViewModel.FileEntry, isSelected: Boolean) {
            binding.apply {
                fileName.text = entry.name
                fileSize.text = if (entry.isDirectory) "" else formatSize(entry.size)

                when (entry.fileType) {
                    FileType.NEF -> {
                        fileTypeBadge.text = "RAW"
                        fileTypeBadge.setBackgroundColor(0xFFF5A623.toInt())
                    }
                    FileType.JPEG, FileType.TIFF -> {
                        fileTypeBadge.text = ""
                        fileTypeBadge.setBackgroundColor(Color.TRANSPARENT)
                    }
                    FileType.MP4, FileType.MOV -> {
                        fileTypeBadge.text = "" // duration not available without metadata
                        fileTypeBadge.setBackgroundColor(0xFF4A90D9.toInt())
                    }
                    null -> {
                        fileTypeBadge.text = if (entry.isDirectory) "📁" else ""
                        fileTypeBadge.setBackgroundColor(Color.TRANSPARENT)
                    }
                }

                fileIcon.text = when {
                    entry.isDirectory -> "📁"
                    entry.fileType?.isVideo == true -> "🎬"
                    else -> "🖼"
                }

                selectionCheckbox.isChecked = isSelected

                root.setBackgroundColor(
                    if (isSelected) 0x3327AE60 else Color.TRANSPARENT
                )

                root.setOnClickListener {
                    if (entry.isDirectory) onFolderClick(entry) else onFileClick(entry)
                }
                root.setOnLongClickListener {
                    onLongClick(entry)
                    true
                }
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<BrowserViewModel.FileEntry>() {
        override fun areItemsTheSame(old: BrowserViewModel.FileEntry, new: BrowserViewModel.FileEntry) =
            old.path == new.path

        override fun areContentsTheSame(old: BrowserViewModel.FileEntry, new: BrowserViewModel.FileEntry) =
            old == new
    }

    companion object {
        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
            else -> "${"%.2f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
        }
    }
}
