package com.example.spectrogramapp.spectrogram

/**
 * Data class for audio file information
 */
data class AudioInfo(
    val duration: Double,      // Duration in seconds
    val sampleRate: Int,       // Sample rate in Hz
    val channels: Int,         // Number of channels
    val bitrate: Int           // Bitrate in kbps
)