package org.tcec.memoryaidassisttimeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.tcec.memoryaidassisttimeline.data.MemoryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    dao: MemoryDao
) : ViewModel() {
    val memories = dao.getAllMemories()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    val voskStatus = org.tcec.memoryaidassisttimeline.data.SensorDataManager.voskModelStatus
    val tfliteStatus = org.tcec.memoryaidassisttimeline.data.SensorDataManager.tfliteModelStatus
    val decibels = org.tcec.memoryaidassisttimeline.data.SensorDataManager.audioDecibels
    val liveTranscription = org.tcec.memoryaidassisttimeline.data.SensorDataManager.liveTranscription
    val isServiceRunning = org.tcec.memoryaidassisttimeline.data.SensorDataManager.isServiceRunning
}
