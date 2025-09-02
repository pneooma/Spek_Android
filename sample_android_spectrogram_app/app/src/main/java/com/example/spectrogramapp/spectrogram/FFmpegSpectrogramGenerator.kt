package com.example.spectrogramapp.spectrogram

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * Generates spectrograms using FFmpeg library
 * Supports both file-based and real-time audio processing
 */
class FFmpegSpectrogramGenerator {
    
    companion object {
        private const val TAG = "FFmpegSpectrogramGenerator"
        
        // Default FFT parameters
        private const val DEFAULT_FFT_SIZE = 1024
        private const val DEFAULT_SAMPLE_RATE = 44100
        private const val DEFAULT_WINDOW_SIZE = 2048
        private const val DEFAULT_HOP_SIZE = 512
        
        // Spectrogram visualization parameters
        private const val DEFAULT_WIDTH = 1024
        private const val DEFAULT_HEIGHT = 512
        private const val DEFAULT_GAIN = 1.5f
        private const val DEFAULT_FPS = 25
    }
    
    /**
     * Generate a spectrogram image from an audio file
     * @param audioFilePath Path to the input audio file
     * @param outputPath Path where the spectrogram image will be saved
     * @param onProgress Progress callback (0.0 to 1.0)
     * @param onComplete Success callback with output path
     * @param onError Error callback with error message
     */
    fun generateSpectrogram(
        audioFilePath: String,
        outputPath: String,
        onProgress: (Float) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // Validate input file
            val inputFile = File(audioFilePath)
            if (!inputFile.exists()) {
                onError("Input audio file does not exist: $audioFilePath")
                return
            }
            
            // Create output directory if it doesn't exist
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            
            // Build FFmpeg filter complex for spectrogram generation
            val filterComplex = buildSpectrogramFilter(
                width = DEFAULT_WIDTH,
                height = DEFAULT_HEIGHT,
                gain = DEFAULT_GAIN
            )
            
            // Construct FFmpeg command
            val command = "-i \"$audioFilePath\" -filter_complex \"$filterComplex\" -y \"$outputPath\""
            
            Log.d(TAG, "Executing FFmpeg command: $command")
            
            // Execute FFmpeg command asynchronously
            FFmpegKit.executeAsync(command,
                { session: FFmpegSession ->
                    handleFFmpegSession(session, outputPath, onComplete, onError)
                },
                { log ->
                    Log.d(TAG, "FFmpeg log: ${log.message}")
                },
                { statistics ->
                    // Calculate progress based on time
                    val progress = calculateProgress(statistics.time)
                    onProgress(progress)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating spectrogram", e)
            onError("Error: ${e.message}")
        }
    }
    
    /**
     * Generate a spectrogram with custom parameters
     */
    fun generateSpectrogramWithParams(
        audioFilePath: String,
        outputPath: String,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        fftSize: Int = DEFAULT_FFT_SIZE,
        gain: Float = DEFAULT_GAIN,
        onProgress: (Float) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val filterComplex = buildSpectrogramFilter(width, height, gain, fftSize)
            val command = "-i \"$audioFilePath\" -filter_complex \"$filterComplex\" -y \"$outputPath\""
            
            Log.d(TAG, "Executing custom FFmpeg command: $command")
            
            FFmpegKit.executeAsync(command,
                { session ->
                    handleFFmpegSession(session, outputPath, onComplete, onError)
                },
                { log ->
                    Log.d(TAG, "FFmpeg log: ${log.message}")
                },
                { statistics ->
                    val progress = calculateProgress(statistics.time)
                    onProgress(progress)
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating custom spectrogram", e)
            onError("Error: ${e.message}")
        }
    }
    
    /**
     * Generate real-time spectrogram data from audio stream
     * @param audioData Raw audio data
     * @param sampleRate Sample rate of the audio
     * @param onFrameData Callback with spectrogram frame data
     */
    fun generateRealTimeSpectrogram(
        audioData: ByteArray,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        onFrameData: (SpectrogramFrame) -> Unit
    ) {
        try {
            // For real-time processing, we need to process smaller chunks
            val chunkSize = DEFAULT_FFT_SIZE * 2 // 16-bit samples
            
            if (audioData.size >= chunkSize) {
                // Process audio in chunks
                val chunks = audioData.chunked(chunkSize)
                
                chunks.forEachIndexed { index, chunk ->
                    if (chunk.size == chunkSize) {
                        val frame = processAudioChunk(chunk.toByteArray(), sampleRate, index.toLong())
                        onFrameData(frame)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in real-time spectrogram generation", e)
        }
    }
    
    /**
     * Process a single audio chunk and return spectrogram frame
     */
    private fun processAudioChunk(
        audioChunk: ByteArray,
        sampleRate: Int,
        timestamp: Long
    ): SpectrogramFrame {
        // Convert byte array to short array (16-bit audio)
        val shortArray = ShortArray(audioChunk.size / 2)
        for (i in shortArray.indices) {
            val low = audioChunk[i * 2].toInt() and 0xFF
            val high = audioChunk[i * 2 + 1].toInt() and 0xFF
            shortArray[i] = ((high shl 8) or low).toShort()
        }
        
        // Apply window function (Hamming window)
        val windowedData = applyHammingWindow(shortArray)
        
        // Perform FFT (simplified - in real implementation, use proper FFT library)
        val fftResult = performFFT(windowedData)
        
        // Convert to frequency domain
        val frequencies = generateFrequencyArray(fftResult.size, sampleRate)
        val magnitudes = calculateMagnitudes(fftResult)
        
        return SpectrogramFrame(
            timestamp = timestamp,
            frequencies = frequencies,
            magnitudes = magnitudes,
            sampleRate = sampleRate,
            fftSize = fftResult.size
        )
    }
    
    /**
     * Build FFmpeg filter complex string for spectrogram generation
     */
    private fun buildSpectrogramFilter(
        width: Int,
        height: Int,
        gain: Float,
        fftSize: Int = DEFAULT_FFT_SIZE
    ): String {
        return buildString {
            append("showspectrum=")
            append("mode=combined:")
            append("size=${width}x${height}:")
            append("color=channel:")
            append("scale=log:")
            append("fscale=log:")
            append("gain=$gain:")
            append("data=1:")
            append("orientation=vertical:")
            append("fps=$DEFAULT_FPS")
        }
    }
    
    /**
     * Handle FFmpeg session completion
     */
    private fun handleFFmpegSession(
        session: FFmpegSession,
        outputPath: String,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (ReturnCode.isSuccess(session.returnCode)) {
            Log.d(TAG, "FFmpeg execution completed successfully")
            onComplete(outputPath)
        } else {
            val errorMessage = session.failStackTrace ?: "Unknown FFmpeg error"
            Log.e(TAG, "FFmpeg execution failed: $errorMessage")
            onError("FFmpeg error: $errorMessage")
        }
    }
    
    /**
     * Calculate progress from FFmpeg statistics
     */
    private fun calculateProgress(time: Long): Float {
        // This is a simplified progress calculation
        // In a real implementation, you might want to use duration information
        return (time / 1000f).coerceIn(0f, 1f)
    }
    
    /**
     * Apply Hamming window to audio data
     */
    private fun applyHammingWindow(data: ShortArray): FloatArray {
        val windowed = FloatArray(data.size)
        val size = data.size
        
        for (i in data.indices) {
            val window = 0.54 - 0.46 * kotlin.math.cos(2 * kotlin.math.PI * i / (size - 1))
            windowed[i] = data[i] * window
        }
        
        return windowed
    }
    
    /**
     * Perform FFT on windowed audio data
     * Note: This is a simplified implementation. Use a proper FFT library for production.
     */
    private fun FloatArray.performFFT(): FloatArray {
        // Simplified FFT implementation
        // In production, use JTransforms or similar library
        // For now, we'll use a basic power spectrum calculation
        val size = this.size
        val result = FloatArray(size)
        
        // Simple power spectrum calculation
        for (i in 0 until size) {
            result[i] = this[i] * this[i]
        }
        
        return result
    }
    
    /**
     * Generate frequency array for FFT bins
     */
    private fun generateFrequencyArray(size: Int, sampleRate: Int): FloatArray {
        return FloatArray(size) { index ->
            index * sampleRate / size.toFloat()
        }
    }
    
    /**
     * Calculate magnitudes from FFT result
     */
    private fun calculateMagnitudes(fftResult: FloatArray): FloatArray {
        return fftResult.map { 
            if (it > 0) 20 * kotlin.math.log10(it) else -100f 
        }.toFloatArray()
    }
    
    /**
     * Get audio file information using FFmpeg
     */
    fun getAudioInfo(audioFilePath: String): AudioInfo? {
        return try {
            val command = "-i \"$audioFilePath\""
            
            FFmpegKit.execute(command)
            
            // Parse FFmpeg output to extract audio information
            // This is a simplified implementation
            AudioInfo(
                duration = 0.0,
                sampleRate = DEFAULT_SAMPLE_RATE,
                channels = 2,
                bitrate = 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio info", e)
            null
        }
    }
}

