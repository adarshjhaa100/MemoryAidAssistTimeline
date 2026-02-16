package org.tcec.memoryaidassisttimeline.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object SensorDataManager {
    private val _voskModelStatus = MutableStateFlow("Vosk: Initializing...")
    val voskModelStatus = _voskModelStatus.asStateFlow()

    private val _tfliteModelStatus = MutableStateFlow("TFLite: Initializing...")
    val tfliteModelStatus = _tfliteModelStatus.asStateFlow()

    // Holds the last 100 amplitude readings for the graph
    private val _audioDecibels = MutableStateFlow<List<Float>>(emptyList())
    val audioDecibels = _audioDecibels.asStateFlow()
    
    // Live Location State
    private val _location = MutableStateFlow<String?>(null)
    val location = _location.asStateFlow()

    fun updateVoskStatus(status: String) {
        _voskModelStatus.value = status
    }

    fun updateLocation(lat: Double, lon: Double) {
        val loc = "Lat: $lat, Lon: $lon"
        android.util.Log.d("SensorDataManager", "Updating location: $loc")
        _location.value = loc
    }

    fun updateTfliteStatus(status: String) {
        _tfliteModelStatus.value = status
    }

    fun addDecibelReading(db: Float) {
        val currentList = _audioDecibels.value.toMutableList()
        if (currentList.size >= 100) {
            currentList.removeAt(0)
        }
        currentList.add(db)
        _audioDecibels.value = currentList
    }

    private val _liveTranscription = MutableStateFlow("")
    val liveTranscription = _liveTranscription.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    fun updateLiveTranscription(text: String) {
        _liveTranscription.value = text
    }

    fun setServiceRunning(isRunning: Boolean) {
        _isServiceRunning.value = isRunning
    }
}
