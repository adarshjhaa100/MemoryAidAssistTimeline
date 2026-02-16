package org.tcec.memoryaidassisttimeline.services

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log
import org.tcec.memoryaidassisttimeline.data.SensorDataManager
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException
import kotlin.math.log10
import kotlin.math.sqrt

class AudioHelper(private val context: Context) {
    
    private var classifier: AudioClassifier? = null
    private var voskModel: Model? = null
    private var audioRecord: AudioRecord? = null
    
    companion object {
        private const val TAG = "MemorySensor"
    }
    
    init {
        try {
            // Load TFLite Model
            classifier = AudioClassifier.createFromFile(context, "yamnet.tflite")
            audioRecord = classifier?.createAudioRecord()
            SensorDataManager.updateTfliteStatus("TFLite: Loaded")
            Log.d(TAG, "AudioHelper initialized, model loaded")
        } catch (e: IOException) {
            SensorDataManager.updateTfliteStatus("TFLite: Failed to load")
            Log.e(TAG, "Failed to load TFLite model", e)
            e.printStackTrace()
        }
        
        // Async Load Vosk Model
        SensorDataManager.updateVoskStatus("Vosk: Loading...")
        StorageService.unpack(context, "model-en-in", "model",
            { model -> 
                voskModel = model
                SensorDataManager.updateVoskStatus("Vosk: Ready")
                Log.d(TAG, "Vosk model loaded successfully")
            }, 
            { e -> 
                SensorDataManager.updateVoskStatus("Vosk: Error - ${e.message}")
                SensorDataManager.updateVoskStatus("Vosk: Error - ${e.printStackTrace()}")
                Log.e(TAG, "Failed to unpack Vosk model", e)
                println("Failed to unpack Vosk model $e");
                e.printStackTrace() 
            }
        )
    }

    fun isSpeechDetected(): Boolean {
        if (classifier == null || audioRecord == null) {
            Log.w(TAG, "Classifier or AudioRecord not initialized")
            SensorDataManager.updateTfliteStatus("TFLite: Not Ready")
            return false
        }

        try {
            // 1. Start recording
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                 audioRecord?.startRecording()
            }
            
            // 2. Load audio into Tensor
            val tensor = classifier?.createInputTensorAudio()
            tensor?.load(audioRecord)
            
            // Calculate Decibels from the same buffer if possible, or just read a chunk manually?
            // TensorAudio reads from AudioRecord. To get amplitude without interfering, 
            // we might be able to read just before? 
            // Actually, `tensor.load(audioRecord)` consumes data. 
            // Let's assume we can't easily get the raw buffer *out* of TensorAudio easily without hacking.
            // BUT, we can read from AudioRecord *first* into a short array, then load that array? 
            // No, `load` takes AudioRecord.
            // Let's just create a separate short buffer read for visualization purposes *before* tensor load?
            // Or better: Use the score as a proxy? No user asked for Decibel.
            
            // Hack: We can temporarily read a small chunk to calc amplitude.
            val bufferSize = 1024
            val floatBuffer = FloatArray(bufferSize)
            // This assumes AudioRecord is running. 
            // Note: TensorAudio.load() reads specific amount. If we read, we steal data. 
            // However, for speech detection (every 10s), missing 1024 samples (approx 60ms) is fine.
            val readResult = audioRecord?.read(floatBuffer, 0, bufferSize, AudioRecord.READ_BLOCKING) ?: 0
            if (readResult > 0) {
                 val amplitude = calculateRMS(floatBuffer, readResult)
                 val db = 20 * log10(amplitude)
                 // Normalize for display roughly -80 to 0 or similar
                 SensorDataManager.addDecibelReading(db.coerceIn(-100f, 0f))
            }

            // 3. Classify
            val output = classifier?.classify(tensor)
            
            // Stop recording? No, we reuse it.
             audioRecord?.stop()
            
            // 4. Check if "Speech" is confident
            val speech = output?.flatMap { it.categories }?.find { it.label == "Speech" }
            val isSpeech = (speech?.score ?: 0f) > 0.5f
            
            if (isSpeech) {
                Log.d(TAG, "Speech detected with score: ${speech?.score}")
            }
            
            return isSpeech
        } catch (e: Exception) {
            Log.e(TAG, "Error in speech detection", e)
            return false
        }
    }
    
    private fun calculateRMS(buffer: FloatArray, readSize: Int): Float {
        var sum = 0f
        for (i in 0 until readSize) {
            sum += buffer[i] * buffer[i]
        }
        return if (readSize > 0) sqrt(sum / readSize) else 0f
    }
    
    fun getRecognizer(): Recognizer? {
        if (voskModel == null) {
            Log.w(TAG, "Vosk model not yet loaded")
            return null
        }
        return Recognizer(voskModel, 16000.0f)
    }
}
