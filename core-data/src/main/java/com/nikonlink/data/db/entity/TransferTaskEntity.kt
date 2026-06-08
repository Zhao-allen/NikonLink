// core-data/src/main/java/com/nikonlink/data/db/entity/TransferTaskEntity.kt
package com.nikonlink.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_tasks")
data class TransferTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cameraId: Long,
    val remotePath: String,
    val fileName: String,
    val fileSize: Long = 0,
    val fileType: String, // JPEG, NEF, TIFF, MP4, MOV
    val status: String = "pending", // pending, transferring, completed, failed
    val progressBytes: Long = 0,
    val localUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null
)
