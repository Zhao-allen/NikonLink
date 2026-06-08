// core-data/src/main/java/com/nikonlink/data/db/entity/CameraDeviceEntity.kt
package com.nikonlink.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "camera_devices")
data class CameraDeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val model: String,
    val bleAddress: String,
    val wifiSsid: String? = null,
    val wifiPassword: String? = null,
    val lastConnected: Long = System.currentTimeMillis(),
    val firmwareVersion: String? = null,
    val protocol: String = "snapbridge" // snapbridge | ptpip | http | wmu
)
