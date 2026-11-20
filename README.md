# Spek for Android: spectrogram research

Research and a sample app for generating **audio spectrograms on Android**, similar to the desktop app [Spek](https://www.spek.cc). A spectrogram shows time on the X axis, frequency on the Y axis and loudness as colour, which makes it easy to spot fake "lossless" files and low-bitrate transcodes.

This work fed into the spectrogram feature of **MobileDigger**.

## Contents

| Path | What it is |
|---|---|
| [`android_spectrogram_investigation.md`](android_spectrogram_investigation.md) | Feasibility study: audio decoding, FFT, window functions (Hann, Hamming, Blackman-Harris), Android API and performance considerations, and implementation options compared |
| [`ffmpeg_android_spectrogram_implementation.md`](ffmpeg_android_spectrogram_implementation.md) | Detailed plan for an FFmpeg-based implementation |
| [`sample_android_spectrogram_app/`](sample_android_spectrogram_app/) | Sample Kotlin app (see its own README) |

## Sample app

```
sample_android_spectrogram_app/app/src/main/java/com/example/spectrogramapp/
  MainActivity.kt
  spectrogram/
    FFmpegSpectrogramGenerator.kt   decode a file with FFmpeg → FFT frames
    RealTimeAudioProcessor.kt       live microphone input → FFT frames
    SpectrogramFrame.kt             one column of magnitude data
    SpectrogramView.kt              custom View that renders the spectrogram
```

To try it, open `sample_android_spectrogram_app/` in Android Studio and run it on a device. It needs the `RECORD_AUDIO` permission for live mode and storage access for files.
