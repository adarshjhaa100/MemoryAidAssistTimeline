package org.tcec.memoryaidassisttimeline.services

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
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

@AndroidEntryPoint
class PassiveSensorService : Service() {

    @Inject lateinit var memoryDao: MemoryDao
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var audioHelper: AudioHelper
    private var speechService: SpeechService? = null
    
    companion object {
        private const val TAG = "MemorySensorService"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        SensorDataManager.setServiceRunning(true)
        audioHelper = AudioHelper(this)
        startForeground(1, createNotification())
        
        // Start continuous listening immediately
        startContinuousListening()
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
            
            // Background loop for decibel updates only (optional, if we want graph to move)
            // Since we are not manually reading audioRecord here anymore (SpeechService does it),
            // we cannot easily get decibels unless we hook into SpeechService or run a parallel reader?
            // Running parallel reader on same AudioSource usually fails on Android.
            // For now, we accept that Graph might be static OR we rely on a simulated/mock level if needed,
            // OR we try to read from AudioHelper if it supports non-exclusive. 
            // NOTE: AudioHelper.isSpeechDetected logic is now unused for control flow,
            // but we can still call it periodically to update the graph if it doesn't conflict with SpeechService.
            // However, SpeechService locks the mic. calling audioHelper.isSpeechDetected() which tries to startRecording() will likely fail.
            // So Decibel graph might die here. That's a trade-off for using Vosk SpeechService directly.
            // To fix this properly, we'd need to implementing raw audio reading and feed Vosk manually. 
            // For this iteration, let's just log that we are skipping manual audio reads.
        }
    }

    private fun saveMemory(text: String) {
        scope.launch {
            Log.d(TAG, "Saving memory: $text")
            try {
                memoryDao.insert(MemoryNode(type = MemoryType.AUDIO, content = text))
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
