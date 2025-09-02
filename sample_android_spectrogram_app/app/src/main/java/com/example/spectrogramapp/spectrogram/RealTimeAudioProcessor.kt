package com.example.spectrogramapp.spectrogram

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*

/**
 * Handles real-time audio recording and processing for live spectrogram generation
 */
class RealTimeAudioProcessor {
    
    companion object {
        private const val TAG = "RealTimeAudioProcessor"
        
        // Default audio configuration
        private const val DEFAULT_SAMPLE_RATE = 44100
        private const val DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val DEFAULT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // Buffer configuration
        private const val BUFFER_SIZE_MULTIPLIER = 4
        private const val MIN_BUFFER_SIZE = 2048
        private const val MAX_BUFFER_SIZE = 16384
    }
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    private var onFrameDataCallback: ((SpectrogramFrame) -> Unit)? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Audio processing parameters
    private var sampleRate: Int = DEFAULT_SAMPLE_RATE
    private var channelConfig: Int = DEFAULT_CHANNEL_CONFIG
    private var audioFormat: Int = DEFAULT_AUDIO_FORMAT
    private var bufferSize: Int = 0
    
    // Spectrogram generation
    private val spectrogramGenerator = FFmpegSpectrogramGenerator()
    
    /**
     * Start recording audio and generating real-time spectrograms
     * @param sampleRate Sample rate in Hz (default: 44100)
     * @param channelConfig Audio channel configuration (default: MONO)
     * @param audioFormat Audio format (default: 16-bit PCM)
     * @param onFrameData Callback for spectrogram frame data
     */
    fun startRecording(
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        channelConfig: Int = DEFAULT_CHANNEL_CONFIG,
        audioFormat: Int = DEFAULT_AUDIO_FORMAT,
        onFrameData: (SpectrogramFrame) -> Unit
    ) {
        if (isRecording) {
            Log.w(TAG, "Already recording, stopping previous session")
            stopRecording()
        }
        
        this.sampleRate = sampleRate
        this.channelConfig = channelConfig
        this.audioFormat = audioFormat
        this.onFrameDataCallback = onFrameData
        
        try {
            initializeAudioRecord()
            startRecordingCoroutine()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            onFrameData(SpectrogramFrame(
                timestamp = System.currentTimeMillis(),
                frequencies = FloatArray(0),
                magnitudes = FloatArray(0),
                sampleRate = sampleRate,
                fftSize = 0
            ))
        }
    }
    
    /**
     * Stop recording and release resources
     */
    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
        
        Log.d(TAG, "Recording stopped")
    }
    
    /**
     * Initialize AudioRecord with optimal buffer size
     */
    private fun initializeAudioRecord() {
        // Calculate optimal buffer size
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        
        bufferSize = when {
            minBufferSize <= 0 -> {
                Log.w(TAG, "Invalid buffer size, using default")
                MIN_BUFFER_SIZE
            }
            minBufferSize < MIN_BUFFER_SIZE -> MIN_BUFFER_SIZE
            minBufferSize > MAX_BUFFER_SIZE -> MAX_BUFFER_SIZE
            else -> minBufferSize * BUFFER_SIZE_MULTIPLIER
        }
        
        Log.d(TAG, "Buffer size: $bufferSize (min: $minBufferSize)")
        
        // Create AudioRecord instance
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("Failed to initialize AudioRecord")
        }
        
        Log.d(TAG, "AudioRecord initialized successfully")
    }
    
    /**
     * Start recording coroutine for continuous audio processing
     */
    private fun startRecordingCoroutine() {
        recordingJob = scope.launch {
            try {
                audioRecord?.startRecording()
                isRecording = true
                
                Log.d(TAG, "Started recording audio")
                
                // Process audio in continuous loop
                processAudioContinuously()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in recording coroutine", e)
                isRecording = false
            }
        }
    }
    
    /**
     * Continuous audio processing loop
     */
    private suspend fun processAudioContinuously() {
        val buffer = ShortArray(bufferSize / 2) // 16-bit samples
        
        while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            try {
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (readSize > 0) {
                    // Process the audio buffer
                    processAudioBuffer(buffer, readSize)
                    
                    // Small delay to prevent overwhelming the system
                    delay(10)
                } else if (readSize < 0) {
                    Log.w(TAG, "Error reading audio data: $readSize")
                    break
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing audio buffer", e)
                break
            }
        }
        
        Log.d(TAG, "Audio processing loop ended")
    }
    
    /**
     * Process a single audio buffer and generate spectrogram frame
     */
    private suspend fun processAudioBuffer(buffer: ShortArray, size: Int) {
        try {
            // Convert short array to byte array for FFmpeg processing
            val byteBuffer = ByteBuffer.allocate(size * 2)
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
            
            for (i in 0 until size) {
                byteBuffer.putShort(buffer[i])
            }
            
            val audioData = byteBuffer.array()
            
            // Generate spectrogram frame using FFmpeg
            withContext(Dispatchers.Default) {
                spectrogramGenerator.generateRealTimeSpectrogram(
                    audioData,
                    sampleRate,
                    onFrameData = { frame ->
                        // Send frame data to callback on main thread
                        mainHandler.post {
                            onFrameDataCallback?.invoke(frame)
                        }
                    }
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio buffer", e)
        }
    }
    
    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording
    
    /**
     * Get current audio configuration
     */
    fun getAudioConfig(): AudioConfig {
        return AudioConfig(
            sampleRate = sampleRate,
            channelConfig = channelConfig,
            audioFormat = audioFormat,
            bufferSize = bufferSize
        )
    }
    
    /**
     * Update audio configuration (requires restart)
     */
    fun updateAudioConfig(
        sampleRate: Int = this.sampleRate,
        channelConfig: Int = this.channelConfig,
        audioFormat: Int = this.audioFormat
    ) {
        if (isRecording) {
            Log.w(TAG, "Cannot update config while recording, stopping first")
            stopRecording()
        }
        
        this.sampleRate = sampleRate
        this.channelConfig = channelConfig
        this.audioFormat = audioFormat
        
        Log.d(TAG, "Audio configuration updated: $sampleRate Hz, $channelConfig, $audioFormat")
    }
    
    /**
     * Get recording statistics
     */
    fun getRecordingStats(): RecordingStats {
        return RecordingStats(
            isRecording = isRecording,
            sampleRate = sampleRate,
            bufferSize = bufferSize,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Release all resources
     */
    fun release() {
        stopRecording()
        scope.cancel()
        Log.d(TAG, "RealTimeAudioProcessor released")
    }
}

/**
 * Data class for audio configuration
 */
data class AudioConfig(
    val sampleRate: Int,
    val channelConfig: Int,
    val audioFormat: Int,
    val bufferSize: Int
)

/**
 * Data class for recording statistics
 */
data class RecordingStats(
    val isRecording: Boolean,
    val sampleRate: Int,
    val bufferSize: Int,
    val timestamp: Long
)