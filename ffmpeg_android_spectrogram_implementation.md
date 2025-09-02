# FFmpeg-based Android Spectrogram Implementation

## Overview
This document outlines how to implement audio spectrograms in Android using FFmpeg, similar to iOS implementations. FFmpeg provides professional-grade audio processing capabilities and is the industry standard for multimedia processing.

## Why FFmpeg for Spectrograms?

### Advantages
- **Professional Quality**: Industry-standard library used by major applications
- **Format Support**: Handles virtually all audio formats (MP3, WAV, FLAC, AAC, OGG, etc.)
- **Optimized Algorithms**: Highly optimized FFT and signal processing
- **Cross-platform**: Same codebase can be used across iOS and Android
- **Active Development**: Continuously maintained and improved
- **Extensive Documentation**: Large community and resources

### iOS Comparison
Many iOS audio apps use FFmpeg for:
- Audio format decoding
- Real-time audio processing
- Spectrogram generation
- Audio analysis tools

## FFmpeg Integration Approaches for Android

### Approach 1: FFmpeg-kit (Recommended)
**FFmpeg-kit** is a modern, well-maintained library specifically designed for mobile platforms.

**Features:**
- Pre-built binaries for Android
- Kotlin/Java bindings
- Async processing support
- Memory efficient
- Active development and support

**Implementation:**
```gradle
dependencies {
    implementation 'com.arthenica:ffmpeg-kit-full:6.0.2'
}
```

### Approach 2: FFmpeg Android (Legacy)
**FFmpeg Android** is the older, more complex integration method.

**Features:**
- Direct FFmpeg integration
- Full control over FFmpeg features
- Requires JNI knowledge
- Larger APK size

### Approach 3: Custom FFmpeg Build
**Custom compilation** for maximum control and optimization.

**Features:**
- Optimized for specific use cases
- Minimal binary size
- Complex build process
- Full feature control

## FFmpeg Spectrogram Commands

### Basic Spectrogram Generation
```bash
ffmpeg -i input.mp3 -filter_complex "showspectrum=mode=combined:size=800x600:color=channel:scale=log" -f null -
```

### Advanced Spectrogram with Custom Parameters
```bash
ffmpeg -i input.mp3 -filter_complex "
showspectrum=
    mode=combined:
    size=1024x512:
    color=channel:
    scale=log:
    fscale=log:
    gain=1.5:
    data=1:
    orientation=vertical
" -f null -
```

### Real-time Spectrogram
```bash
ffmpeg -f lavfi -i "sine=frequency=1000:duration=10" -filter_complex "
showspectrum=
    mode=combined:
    size=800x600:
    color=channel:
    scale=log:
    fps=25
" -f null -
```

## Android Implementation with FFmpeg-kit

### 1. Project Setup
```gradle
// build.gradle (app level)
dependencies {
    implementation 'com.arthenica:ffmpeg-kit-full:6.0.2'
    implementation 'com.arthenica:ffmpeg-kit-audio:6.0.2'
}

// build.gradle (project level)
repositories {
    maven { url 'https://jitpack.io' }
    maven { url 'https://maven.google.com' }
}
```

### 2. Core Spectrogram Generator
```kotlin
class FFmpegSpectrogramGenerator {
    
    companion object {
        private const val FFT_SIZE = 1024
        private const val SAMPLE_RATE = 44100
        private const val WINDOW_SIZE = 2048
        private const val HOP_SIZE = 512
    }
    
    fun generateSpectrogram(
        audioFile: String,
        outputPath: String,
        onProgress: (Float) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val filterComplex = buildString {
            append("showspectrum=")
            append("mode=combined:")
            append("size=${FFT_SIZE}x${WINDOW_SIZE}:")
            append("color=channel:")
            append("scale=log:")
            append("fscale=log:")
            append("gain=1.5:")
            append("data=1:")
            append("orientation=vertical")
        }
        
        val command = "-i $audioFile -filter_complex \"$filterComplex\" -y $outputPath"
        
        FFmpegKit.executeAsync(command,
            { session ->
                onComplete(outputPath)
            },
            { log ->
                // Handle logs
            },
            { statistics ->
                val progress = statistics.time / 1000f // Convert to seconds
                onProgress(progress)
            }
        )
    }
    
    fun generateRealTimeSpectrogram(
        audioFile: String,
        onFrameData: (SpectrogramFrame) -> Unit
    ) {
        // Implementation for real-time processing
    }
}
```

### 3. Spectrogram Data Structure
```kotlin
data class SpectrogramFrame(
    val timestamp: Long,
    val frequencies: FloatArray,
    val magnitudes: FloatArray,
    val sampleRate: Int,
    val fftSize: Int
) {
    fun getFrequencyAtIndex(index: Int): Float {
        return index * sampleRate / fftSize
    }
    
    fun getMagnitudeAtFrequency(frequency: Float): Float {
        val index = (frequency * fftSize / sampleRate).toInt()
        return if (index in magnitudes.indices) magnitudes[index] else 0f
    }
}
```

### 4. Real-time Audio Processing
```kotlin
class RealTimeAudioProcessor {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val fftProcessor = FFmpegSpectrogramGenerator()
    
    fun startRecording(
        sampleRate: Int = 44100,
        channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
        audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
    ) {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        
        audioRecord?.startRecording()
        isRecording = true
        
        processAudioInRealTime()
    }
    
    private fun processAudioInRealTime() {
        val buffer = ShortArray(2048)
        
        while (isRecording) {
            val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (readSize > 0) {
                // Process audio buffer with FFmpeg
                processAudioBuffer(buffer, readSize)
            }
        }
    }
    
    private fun processAudioBuffer(buffer: ShortArray, size: Int) {
        // Convert to bytes and process with FFmpeg
        val byteBuffer = ByteBuffer.allocate(size * 2)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        
        for (i in 0 until size) {
            byteBuffer.putShort(buffer[i])
        }
        
        // Process with FFmpeg for real-time spectrogram
        fftProcessor.processRealTimeAudio(byteBuffer.array())
    }
    
    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
```

### 5. Visualization Component
```kotlin
class SpectrogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spectrogramData = mutableListOf<SpectrogramFrame>()
    private var maxMagnitude = 0f
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSpectrogram(canvas)
    }
    
    private fun drawSpectrogram(canvas: Canvas) {
        if (spectrogramData.isEmpty()) return
        
        val cellWidth = width.toFloat() / spectrogramData.size
        val cellHeight = height.toFloat() / spectrogramData[0].frequencies.size
        
        for (timeIndex in spectrogramData.indices) {
            val frame = spectrogramData[timeIndex]
            val x = timeIndex * cellWidth
            
            for (freqIndex in frame.frequencies.indices) {
                val y = height - (freqIndex * cellHeight)
                val magnitude = frame.magnitudes[freqIndex]
                
                // Color mapping based on magnitude
                val color = getColorForMagnitude(magnitude)
                paint.color = color
                
                canvas.drawRect(x, y, x + cellWidth, y + cellHeight, paint)
            }
        }
    }
    
    private fun getColorForMagnitude(magnitude: Float): Int {
        val normalizedMagnitude = magnitude / maxMagnitude
        return when {
            normalizedMagnitude < 0.2 -> Color.rgb(0, 0, 255)      // Blue
            normalizedMagnitude < 0.4 -> Color.rgb(0, 255, 255)    // Cyan
            normalizedMagnitude < 0.6 -> Color.rgb(0, 255, 0)      // Green
            normalizedMagnitude < 0.8 -> Color.rgb(255, 255, 0)    // Yellow
            else -> Color.rgb(255, 0, 0)                           // Red
        }
    }
    
    fun updateSpectrogramData(newData: List<SpectrogramFrame>) {
        spectrogramData.clear()
        spectrogramData.addAll(newData)
        
        // Find maximum magnitude for normalization
        maxMagnitude = spectrogramData.maxOfOrNull { frame ->
            frame.magnitudes.maxOrNull() ?: 0f
        } ?: 0f
        
        invalidate()
    }
}
```

## Performance Optimization

### 1. Memory Management
```kotlin
class SpectrogramCache {
    private val maxCacheSize = 100
    private val cache = LinkedHashMap<String, SpectrogramData>(maxCacheSize, 0.75f, true)
    
    fun get(key: String): SpectrogramData? = cache[key]
    
    fun put(key: String, data: SpectrogramData) {
        if (cache.size >= maxCacheSize) {
            val firstKey = cache.keys.first()
            cache.remove(firstKey)
        }
        cache[key] = data
    }
}
```

### 2. Background Processing
```kotlin
class SpectrogramProcessor {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun processAudioFile(
        audioFile: String,
        onProgress: (Float) -> Unit,
        onComplete: (SpectrogramData) -> Unit
    ) {
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    // FFmpeg processing here
                    generateSpectrogram(audioFile)
                }
                
                withContext(Dispatchers.Main) {
                    onComplete(result)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
```

## Comparison with iOS Implementation

### Similarities
- **Same FFmpeg core**: Identical audio processing algorithms
- **Format support**: Same audio format compatibility
- **Performance**: Similar processing speed
- **Quality**: Identical output quality

### Differences
- **Platform APIs**: Different audio input/output mechanisms
- **UI Framework**: Different rendering approaches
- **Memory management**: Android-specific optimizations
- **Background processing**: Different lifecycle management

## Conclusion

Using FFmpeg for Android spectrogram generation provides:

1. **Professional Quality**: Industry-standard audio processing
2. **Cross-platform Compatibility**: Shared knowledge with iOS development
3. **Extensive Format Support**: Handle virtually any audio format
4. **Performance**: Optimized algorithms and efficient processing
5. **Community Support**: Large developer community and resources

The FFmpeg-kit approach is recommended for most Android applications, providing the best balance of ease of use, performance, and feature completeness.

## Next Steps
1. Set up FFmpeg-kit in Android project
2. Implement basic spectrogram generation
3. Add real-time audio processing
4. Create visualization components
5. Optimize performance and add advanced features