package org.tcec.memoryaidassisttimeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.tcec.memoryaidassisttimeline.data.MemoryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    dao: MemoryDao
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
}
