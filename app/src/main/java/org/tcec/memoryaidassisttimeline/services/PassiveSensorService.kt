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
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.vosk.Recognizer
import kotlin.math.log10

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

    @SuppressLint("MissingPermission")
    private fun startContinuousListening() {
        scope.launch {
            var recognizer: Recognizer? = null
            while (isActive && recognizer == null) {
                recognizer = audioHelper.getRecognizer()
                if (recognizer == null) {
                    SensorDataManager.updateVoskStatus("Vosk: Waiting for model...")
                    delay(1000)
                }
            }

            if (!isActive || recognizer == null) return@launch

            SensorDataManager.updateVoskStatus("Vosk: Ready (Manual Stream)")
            
            // Manual AudioRecord Loop
            val sampleRate = 16000
            val bufferSize = Math.max(
                AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
                4096
            )
            
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                SensorDataManager.updateVoskStatus("Audio Error: Init Failed")
                return@launch
            }

            try {
                audioRecord.startRecording()
                SensorDataManager.updateVoskStatus("Vosk: Listening (Visualizer Active)")
                
                val buffer = ByteArray(bufferSize)
                
                while (isActive) {
                    val readResult = audioRecord.read(buffer, 0, bufferSize)
                    if (readResult > 0) {
                        // 1. Calculate RMS for Visualizer
                        // Convert bytes to shorts for amplitude calculation
                        var sum = 0.0
                        for (i in 0 until readResult step 2) {
                            if (i + 1 < readResult) {
                                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i+1].toInt() shl 8)
                                val shortSample = sample.toShort()
                                sum += shortSample * shortSample
                            }
                        }
                        // RMS Amplitude (0..32768)
                        val rms = Math.sqrt(sum / (readResult / 2)).toFloat()
                        
                        // Convert to dBFS (0 is max, -Infinity is silence)
                        // Reference is 32768.0 (Max 16-bit)
                        val db = if (rms > 0) {
                            20 * log10(rms / 32768f)
                        } else {
                            -100f // Silence floor
                        }
                        
                        // 2. Feed Vosk & Gate Visualizer
                        var isSpeechDetected = false
                        
                        if (recognizer.acceptWaveForm(buffer, readResult)) {
                            // Final Result
                            val result = recognizer.result
                            val json = JSONObject(result)
                            val text = json.optString("text", "")
                            if (text.isNotEmpty()) {
                                isSpeechDetected = true
                                Log.d(TAG, "Final: $text")
                                SensorDataManager.updateLiveTranscription(text)
                                saveMemory(text)
                            }
                        } else {
                            // Partial Result
                            val partial = recognizer.partialResult
                            val json = JSONObject(partial)
                            val partialText = json.optString("partial", "")
                            if (partialText.isNotEmpty()) {
                                isSpeechDetected = true
                                SensorDataManager.updateLiveTranscription(partialText)
                            }
                        }
                        
                        // Semantic Gate: Only show pulse if Vosk found words
                        if (!isSpeechDetected) {
                            SensorDataManager.addDecibelReading(-100f) // Silence
                        } else {
                            SensorDataManager.addDecibelReading(db)
                        }
                    } else {
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in manual audio loop", e)
                SensorDataManager.updateVoskStatus("Error: ${e.message}")
            } finally {
                try {
                    audioRecord.stop()
                    audioRecord.release()
                } catch (e: Exception) { }
            }
        }
    }

    private fun processVoskResult(jsonResult: String, isFinal: Boolean) {
        try {
            val json = JSONObject(jsonResult)
            if (isFinal) {
                val text = json.optString("text", "")
                if (text.isNotEmpty()) {
                    Log.d(TAG, "Final: $text")
                    SensorDataManager.updateLiveTranscription(text)
                    saveMemory(text)
                }
            } else {
                val partial = json.optString("partial", "")
                if (partial.isNotEmpty()) {
                    SensorDataManager.updateLiveTranscription(partial)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON Error: $e")
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
