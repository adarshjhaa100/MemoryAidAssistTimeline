package org.tcec.memoryaidassisttimeline.services

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.tcec.memoryaidassisttimeline.R
import org.tcec.memoryaidassisttimeline.data.MemoryDao
import org.tcec.memoryaidassisttimeline.data.MemoryNode
import org.tcec.memoryaidassisttimeline.data.MemoryType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import javax.inject.Inject
import org.json.JSONObject
import org.tcec.memoryaidassisttimeline.data.SensorDataManager

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.annotation.SuppressLint

@AndroidEntryPoint
class PassiveSensorService : Service() {

    @Inject lateinit var memoryDao: MemoryDao
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var audioHelper: AudioHelper
    private var speechService: SpeechService? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    companion object {
        private const val TAG = "MemorySensorService"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        try {
            SensorDataManager.setServiceRunning(true)
            
            // 1. Start Foreground IMMEDIATELY to avoid ANR/Crash
            val types = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
            ServiceCompat.startForeground(this, 1, createNotification(), types)

            // 2. Initialize Components
            audioHelper = AudioHelper(this)
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            
            // 3. Start Logic
            startContinuousListening()
            startSensorLogging()
        } catch (e: Exception) {
            Log.e(TAG, "Fatal Error in Service onCreate", e)
            // Ensure we don't leave a broken service running?
            // If startForeground failed, we are already dead.
            // If AudioHelper failed, we might want to run without it?
            // For now, logging stack trace is critical.
        }
    }

    private var lastLoggedLocation: android.location.Location? = null
    private var lastLoggedTime: Long = 0

    private fun startSensorLogging() {
        scope.launch {
            while (isActive) {
                logLocation()
                delay(10_000) // Check every 10 seconds
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun logLocation() {
        Log.d(TAG, "Requesting location update...")
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        // Always update UI
                        SensorDataManager.updateLocation(location.latitude, location.longitude)
                        
                        // Smart Logging Logic
                        val shouldLog = if (lastLoggedLocation == null) {
                            true
                        } else {
                            val distance = location.distanceTo(lastLoggedLocation!!)
                            val timeDiff = System.currentTimeMillis() - lastLoggedTime
                            
                            // Log if moved > 10m OR if 1 minute passed
                            distance > 10 || timeDiff >= 60_000
                        }

                        if (shouldLog) {
                            Log.d(TAG, "Logging Location: Moved or Time Limit reached.")
                            val content = "Lat: ${location.latitude}, Lon: ${location.longitude}"
                            saveMemory(content, MemoryType.LOCATION, "{\"lat\":${location.latitude}, \"lon\":${location.longitude}}")
                            
                            lastLoggedLocation = location
                            lastLoggedTime = System.currentTimeMillis()
                        } else {
                            Log.d(TAG, "Skipping Location Log: No significant move & < 1 min.")
                        }
                    } else {
                        Log.w(TAG, "Location received was NULL")
                        SensorDataManager.updateLocation(0.0, 0.0)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Location request failed", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting location", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startContinuousListening() {
        scope.launch {
            // Wait until model is ready loop or just retry?
            // AudioHelper loads model async. We should retry connecting.
            var recognizer: org.vosk.Recognizer? = null
            while (isActive && recognizer == null) {
                recognizer = audioHelper.getRecognizer()
                if (recognizer == null) {
                    SensorDataManager.updateVoskStatus("Vosk: Waiting for model...")
                    delay(1000)
                }
            }

            if (!isActive) return@launch

            SensorDataManager.updateVoskStatus("Vosk: Initializing Speech Service...")
            
            withContext(Dispatchers.Main) {
                try {
                    speechService = SpeechService(recognizer, 16000.0f)
                    speechService?.startListening(object : RecognitionListener {
                        override fun onResult(hypothesis: String) {
                            try {
                                val text = JSONObject(hypothesis).getString("text")
                                if (text.isNotEmpty()) {
                                    Log.d(TAG, "Final Result: $text")
                                    SensorDataManager.updateLiveTranscription(text)
                                    SensorDataManager.updateVoskStatus("Vosk: Saved memory")
                                    saveMemory(text)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing final result", e)
                            }
                        }
                        
                        override fun onPartialResult(hypothesis: String) {
                             try {
                                val partial = JSONObject(hypothesis).getString("partial")
                                if (partial.isNotEmpty()) {
                                    SensorDataManager.updateLiveTranscription(partial)
                                    SensorDataManager.updateVoskStatus("Vosk: Listening...")
                                }
                            } catch (e: Exception) { }
                        }
                        
                        override fun onFinalResult(hypothesis: String) {
                            try {
                                val text = JSONObject(hypothesis).getString("text")
                                if (text.isNotEmpty()) {
                                    Log.d(TAG, "Final Result (Final): $text")
                                    SensorDataManager.updateLiveTranscription(text)
                                    saveMemory(text)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing final result", e)
                            }
                        }
                        
                        override fun onError(exception: Exception) {
                            Log.e(TAG, "Speech service error", exception)
                            SensorDataManager.updateVoskStatus("Vosk: Error - ${exception.message}")
                            // Retry?
                        }
                        
                        override fun onTimeout() {
                            SensorDataManager.updateVoskStatus("Vosk: Timeout")
                        }
                    })
                    SensorDataManager.updateVoskStatus("Vosk: Listening (Continuous)")
                } catch (e: Exception) {
                     Log.e(TAG, "Failed to start speech service", e)
                     SensorDataManager.updateVoskStatus("Vosk: Failed to start")
                }
            }
        }
    }

    private fun saveMemory(text: String, type: MemoryType = MemoryType.AUDIO, details: String? = null) {
        scope.launch {
            Log.d(TAG, "Saving memory: $text [$type]")
            try {
                memoryDao.insert(MemoryNode(
                    type = type, 
                    content = text,
                    details = details
                ))
                Log.d(TAG, "Memory saved successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save memory", e)
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "memory_channel"
        val channel = NotificationChannel(channelId, "Memory Sense", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        
        val stopIntent = Intent(this, PassiveSensorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Memory Active")
            .setContentText("Listening for context...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        SensorDataManager.setServiceRunning(false)
        SensorDataManager.updateVoskStatus("Vosk: Stopped")
        scope.cancel()
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
    }
}
