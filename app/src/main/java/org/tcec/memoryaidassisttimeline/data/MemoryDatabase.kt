package org.tcec.memoryaidassisttimeline.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(node: MemoryNode)

    @Query("SELECT * FROM memory_nodes ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<MemoryNode>>
}

@Database(entities = [MemoryNode::class], version = 1)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
}
