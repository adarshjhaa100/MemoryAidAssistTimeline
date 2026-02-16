package org.tcec.memoryaidassisttimeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.tcec.memoryaidassisttimeline.data.MemoryDao
import javax.inject.Inject
import kotlin.collections.emptyList

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val dao: MemoryDao
) : ViewModel() {
    private val _searchQuery = kotlinx.coroutines.flow.MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val memories = kotlinx.coroutines.flow.combine(dao.getAllMemories(), _searchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter { it.content.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val voskStatus = org.tcec.memoryaidassisttimeline.data.SensorDataManager.voskModelStatus
    val tfliteStatus = org.tcec.memoryaidassisttimeline.data.SensorDataManager.tfliteModelStatus
    val decibels = org.tcec.memoryaidassisttimeline.data.SensorDataManager.audioDecibels
    val liveTranscription = org.tcec.memoryaidassisttimeline.data.SensorDataManager.liveTranscription
    val isServiceRunning = org.tcec.memoryaidassisttimeline.data.SensorDataManager.isServiceRunning
    val location = org.tcec.memoryaidassisttimeline.data.SensorDataManager.location

    fun exportData(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val allMemories = dao.getAllMemoriesList()
                val gson = com.google.gson.Gson()
                val jsonString = gson.toJson(allMemories)

                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val filename = "MemoryAid_Export_$timestamp.json"
                
                // Save to Downloads
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(downloadsDir, filename)
                
                file.writeText(jsonString)
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Exported to Downloads/$filename", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Export Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
