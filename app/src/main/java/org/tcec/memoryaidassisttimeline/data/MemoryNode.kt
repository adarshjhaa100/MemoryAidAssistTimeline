package org.tcec.memoryaidassisttimeline.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MemoryType { AUDIO, LOCATION, SENSOR, SCREEN_TEXT }

@Entity(tableName = "memory_nodes")
data class MemoryNode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MemoryType,
    val content: String,
    val details: String? = null // For JSON metadata like lat/lon or sensor values
)
