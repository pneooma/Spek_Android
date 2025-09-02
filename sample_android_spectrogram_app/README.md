# Android Audio Spectrogram App

A complete Android application that generates audio spectrograms using FFmpeg, similar to the Spek app functionality. This app demonstrates how to implement professional-grade audio analysis with real-time visualization capabilities.

## Features

- **Audio File Processing**: Generate spectrograms from various audio formats (MP3, WAV, FLAC, AAC, OGG)
- **Real-time Spectrogram**: Live audio recording and spectrogram generation
- **Professional Quality**: Uses FFmpeg for industry-standard audio processing
- **Custom Visualization**: Multiple color schemes and visualization options
- **Cross-platform Compatibility**: Same FFmpeg core as iOS implementations

## What is a Spectrogram?

A spectrogram is a visual representation of the spectrum of frequencies in a signal as it varies with time. For audio files, it shows:

- **X-axis**: Time progression
- **Y-axis**: Frequency (Hz)
- **Color intensity**: Amplitude/Decibels (dB)

This visualization is essential for:
- Audio analysis and quality assessment
- Music production and mastering
- Audio forensics and research
- Educational purposes

## Technical Architecture

### Core Components

1. **FFmpegSpectrogramGenerator**: Main class for audio processing using FFmpeg
2. **RealTimeAudioProcessor**: Handles live audio recording and processing
3. **SpectrogramView**: Custom view for rendering spectrogram visualizations
4. **MainActivity**: Main UI controller and user interaction

### Technology Stack

- **FFmpeg-kit**: Professional audio/video processing library
- **Android AudioRecord**: Native Android audio input
- **Kotlin Coroutines**: Asynchronous processing and background tasks
- **Custom Canvas Drawing**: High-performance spectrogram rendering

## Setup Instructions

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK API 21+ (Android 5.0+)
- Kotlin 1.8+
- Android Gradle Plugin 7.0+

### Installation

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd sample_android_spectrogram_app
   ```

2. **Open in Android Studio**:
   - Launch Android Studio
   - Open the project folder
   - Wait for Gradle sync to complete

3. **Add FFmpeg-kit dependency**:
   ```gradle
   // In app/build.gradle
   dependencies {
       implementation 'com.arthenica:ffmpeg-kit-full:6.0.2'
       implementation 'com.arthenica:ffmpeg-kit-audio:6.0.2'
   }
   ```

4. **Configure repositories**:
   ```gradle
   // In project/build.gradle
   repositories {
       maven { url 'https://jitpack.io' }
       maven { url 'https://maven.google.com' }
   }
   ```

5. **Build and run**:
   - Connect Android device or start emulator
   - Click "Run" button in Android Studio

### Permissions

The app requires the following permissions:

- `RECORD_AUDIO`: For microphone access and real-time spectrograms
- `READ_EXTERNAL_STORAGE`: For reading audio files
- `READ_MEDIA_AUDIO`: For modern Android versions (API 33+)

## Usage

### Processing Audio Files

1. **Launch the app** and grant necessary permissions
2. **Tap "Select Audio File"** to choose an audio file from your device
3. **Wait for processing** - the app will generate a spectrogram using FFmpeg
4. **View results** - the spectrogram will be displayed and saved to your device

### Real-time Spectrogram

1. **Tap "Start Live"** to begin real-time audio recording
2. **Speak or play audio** near your device's microphone
3. **Watch the live spectrogram** update in real-time
4. **Tap "Stop Live"** to end recording

### Customization

The app supports various visualization options:

- **Color Schemes**: Rainbow, Grayscale, Heat, Viridis
- **Grid Display**: Toggle frequency and time reference lines
- **Axis Labels**: Show/hide frequency and time markers
- **Resolution**: Adjust FFT size and processing parameters

## Implementation Details

### FFmpeg Integration

The app uses FFmpeg-kit for professional audio processing:

```kotlin
// Generate spectrogram using FFmpeg
val filterComplex = buildString {
    append("showspectrum=")
    append("mode=combined:")
    append("size=1024x512:")
    append("color=channel:")
    append("scale=log:")
    append("fscale=log:")
    append("gain=1.5:")
    append("data=1:")
    append("orientation=vertical")
}

val command = "-i $audioFile -filter_complex \"$filterComplex\" -y $outputPath"
FFmpegKit.executeAsync(command, ...)
```

### Real-time Audio Processing

Live audio is processed using Android's AudioRecord API:

```kotlin
class RealTimeAudioProcessor {
    private var audioRecord: AudioRecord? = null
    
    fun startRecording(onFrameData: (SpectrogramFrame) -> Unit) {
        // Initialize audio recording
        // Process audio buffers in real-time
        // Generate spectrogram frames
    }
}
```

### Custom Visualization

The SpectrogramView renders spectrograms using custom Canvas drawing:

```kotlin
class SpectrogramView : View {
    override fun onDraw(canvas: Canvas) {
        // Draw spectrogram cells
        // Apply color mapping
        // Render grid and labels
    }
}
```

## Performance Optimization

### Memory Management

- **Streaming Processing**: Large files are processed in chunks
- **Object Pooling**: FFT objects are reused to minimize garbage collection
- **Caching**: Color mappings are cached for efficient rendering

### Background Processing

- **Coroutines**: Audio processing runs on background threads
- **Async FFmpeg**: Non-blocking audio analysis
- **UI Updates**: Main thread only handles visualization updates

### Rendering Optimization

- **Hardware Acceleration**: Canvas drawing is hardware accelerated
- **Efficient Algorithms**: Optimized FFT and window function implementations
- **Smart Redraw**: Only redraw when data changes

## Comparison with iOS

### Similarities

- **Same FFmpeg Core**: Identical audio processing algorithms
- **Format Support**: Same audio format compatibility
- **Quality**: Identical output quality and performance

### Differences

- **Platform APIs**: Different audio input/output mechanisms
- **UI Framework**: Different rendering approaches
- **Memory Management**: Android-specific optimizations

## Troubleshooting

### Common Issues

1. **Permission Denied**:
   - Ensure microphone and storage permissions are granted
   - Check Android version compatibility

2. **FFmpeg Errors**:
   - Verify FFmpeg-kit dependency is properly added
   - Check audio file format compatibility

3. **Performance Issues**:
   - Reduce FFT size for better performance
   - Close other apps to free memory
   - Use lower sample rates for real-time processing

### Debug Information

Enable logging to troubleshoot issues:

```kotlin
// In your Application class or MainActivity
if (BuildConfig.DEBUG) {
    Log.d("SpectrogramApp", "Debug mode enabled")
}
```

## Advanced Features

### Custom FFT Parameters

```kotlin
spectrogramGenerator.generateSpectrogramWithParams(
    audioFile = "input.mp3",
    outputPath = "output.png",
    width = 2048,
    height = 1024,
    fftSize = 4096,
    gain = 2.0f
)
```

### Multiple Color Schemes

```kotlin
spectrogramView.updateVisualizationParams(
    scheme = ColorScheme.VIRIDIS,
    grid = true,
    labels = true
)
```

### Audio Configuration

```kotlin
realTimeProcessor.updateAudioConfig(
    sampleRate = 48000,
    channelConfig = AudioFormat.CHANNEL_IN_STEREO
)
```

## Contributing

### Development Setup

1. **Fork the repository**
2. **Create a feature branch**: `git checkout -b feature-name`
3. **Make your changes** and test thoroughly
4. **Submit a pull request** with detailed description

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comprehensive documentation
- Include unit tests for new features

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- **FFmpeg**: Professional multimedia processing library
- **FFmpeg-kit**: Mobile-optimized FFmpeg integration
- **Android AudioRecord**: Native Android audio input API
- **Spek App**: Inspiration for spectrogram visualization

## Support

For questions, issues, or contributions:

- **GitHub Issues**: Report bugs and request features
- **Documentation**: Check the code comments and this README
- **Community**: Join Android development forums

---

**Note**: This implementation demonstrates professional audio processing capabilities. For production use, consider additional error handling, performance optimization, and user experience improvements.