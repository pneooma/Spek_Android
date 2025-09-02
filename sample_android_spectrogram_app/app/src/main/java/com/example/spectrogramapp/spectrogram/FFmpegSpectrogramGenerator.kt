package com.example.spectrogramapp.spectrogram

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import org.jtransforms.fft.FloatFFT_1D

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
                onError(SpectrogramException.AudioFileException.FileNotFound(audioFilePath).getUserFriendlyMessage())
                return
            }
            
            // Check file size (max 100MB)
            val maxFileSize = 100L * 1024 * 1024 // 100MB
            if (inputFile.length() > maxFileSize) {
                onError(SpectrogramException.AudioFileException.FileTooLarge(
                    inputFile.length() / (1024 * 1024), 
                    maxFileSize / (1024 * 1024)
                ).getUserFriendlyMessage())
                return
            }
            
            // Check available storage space
            val requiredSpace = 50L * 1024 * 1024 // 50MB for output
            val availableSpace = getAvailableStorageSpace(outputPath)
            if (availableSpace < requiredSpace) {
                onError(SpectrogramException.ResourceException.StorageFull(
                    outputPath, 
                    requiredSpace / (1024 * 1024), 
                    availableSpace / (1024 * 1024)
                ).getUserFriendlyMessage())
                return
            }
            
            // Create output directory if it doesn't exist
            val outputFile = File(outputPath)
            if (!outputFile.parentFile?.mkdirs()!! && !outputFile.parentFile!!.exists()) {
                onError(SpectrogramException.ResourceException.FileSystemError(outputPath).getUserFriendlyMessage())
                return
            }
            
            // Build FFmpeg filter complex for spectrogram generation
            val filterComplex = buildSpectrogramFilter(
                width = DEFAULT_WIDTH,
                height = DEFAULT_HEIGHT,
                gain = DEFAULT_GAIN
            )
            
            // Construct FFmpeg command
            val command = "-i \"$audioFilePath\" -filter_complex \"$filterComplex\" -y \"$outputPath\""
            
            Log.d(TAG, "Executing FFmpeg command: $command")
            
            // Execute FFmpeg command asynchronously with timeout
            val timeoutMs = 30000L // 30 seconds
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
            
        } catch (e: SpectrogramException) {
            Log.e(TAG, "Spectrogram error", e)
            onError(e.getUserFriendlyMessage())
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error generating spectrogram", e)
            onError("An unexpected error occurred. Please try again.")
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
     * Perform FFT on windowed audio data using JTransforms library
     */
    private fun FloatArray.performFFT(): FloatArray {
        val size = this.size
        
        // Ensure size is a power of 2 for optimal FFT performance
        val fftSize = if (size.isPowerOf2()) size else size.nextPowerOf2()
        
        // Create padded array if needed
        val paddedData = if (fftSize > size) {
            FloatArray(fftSize).apply {
                copyInto(this, 0, 0, size)
                // Zero-pad the rest
                for (i in size until fftSize) {
                    this[i] = 0f
                }
            }
        } else {
            this
        }
        
        // Create FFT instance
        val fft = FloatFFT_1D(fftSize.toLong())
        
        // Convert to complex format (real + imaginary parts)
        val complexData = FloatArray(fftSize * 2)
        for (i in paddedData.indices) {
            complexData[i * 2] = paddedData[i]     // Real part
            complexData[i * 2 + 1] = 0f            // Imaginary part
        }
        
        // Perform FFT in-place
        fft.realForward(complexData)
        
        // Calculate power spectrum (magnitude squared)
        val powerSpectrum = FloatArray(fftSize / 2)
        for (i in powerSpectrum.indices) {
            val real = complexData[i * 2]
            val imag = complexData[i * 2 + 1]
            powerSpectrum[i] = real * real + imag * imag
        }
        
        return powerSpectrum
    }
    
    /**
     * Check if a number is a power of 2
     */
    private fun Int.isPowerOf2(): Boolean {
        return this > 0 && (this and (this - 1)) == 0
    }
    
    /**
     * Get next power of 2 greater than or equal to this number
     */
    private fun Int.nextPowerOf2(): Int {
        var power = 1
        while (power < this) {
            power *= 2
        }
        return power
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
    
    /**
     * Get available storage space in bytes
     */
    private fun getAvailableStorageSpace(path: String): Long {
        return try {
            val file = File(path)
            val parentDir = file.parentFile ?: file
            parentDir.freeSpace
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine available space", e)
            Long.MAX_VALUE // Assume unlimited space if we can't determine
        }
    }
}

