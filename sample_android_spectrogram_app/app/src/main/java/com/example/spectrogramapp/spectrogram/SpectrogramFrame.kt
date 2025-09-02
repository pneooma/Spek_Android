package com.example.spectrogramapp.spectrogram

/**
 * Represents a single frame of spectrogram data
 * @param timestamp The timestamp of this frame in milliseconds
 * @param frequencies Array of frequency values in Hz
 * @param magnitudes Array of magnitude values (typically in dB)
 * @param sampleRate The sample rate of the audio in Hz
 * @param fftSize The size of the FFT used for analysis
 */
data class SpectrogramFrame(
    val timestamp: Long,
    val frequencies: FloatArray,
    val magnitudes: FloatArray,
    val sampleRate: Int,
    val fftSize: Int
) {
    
    /**
     * Get the frequency at a specific index
     * @param index The index in the frequencies array
     * @return Frequency in Hz
     */
    fun getFrequencyAtIndex(index: Int): Float {
        return if (index in frequencies.indices) {
            frequencies[index]
        } else {
            index * sampleRate / fftSize.toFloat()
        }
    }
    
    /**
     * Get the magnitude at a specific frequency
     * @param frequency The frequency in Hz
     * @return Magnitude value
     */
    fun getMagnitudeAtFrequency(frequency: Float): Float {
        val index = (frequency * fftSize / sampleRate).toInt()
        return if (index in magnitudes.indices) {
            magnitudes[index]
        } else {
            0f
        }
    }
    
    /**
     * Get the index for a specific frequency
     * @param frequency The frequency in Hz
     * @return Array index
     */
    fun getIndexForFrequency(frequency: Float): Int {
        return (frequency * fftSize / sampleRate).toInt()
    }
    
    /**
     * Get the maximum magnitude in this frame
     * @return Maximum magnitude value
     */
    fun getMaxMagnitude(): Float {
        return magnitudes.maxOrNull() ?: 0f
    }
    
    /**
     * Get the minimum magnitude in this frame
     * @return Minimum magnitude value
     */
    fun getMinMagnitude(): Float {
        return magnitudes.minOrNull() ?: 0f
    }
    
    /**
     * Get the frequency range covered by this frame
     * @return Pair of (minFrequency, maxFrequency) in Hz
     */
    fun getFrequencyRange(): Pair<Float, Float> {
        return Pair(frequencies.firstOrNull() ?: 0f, frequencies.lastOrNull() ?: 0f)
    }
    
    /**
     * Normalize magnitudes to a 0-1 range
     * @return Array of normalized magnitudes
     */
    fun getNormalizedMagnitudes(): FloatArray {
        val maxMag = getMaxMagnitude()
        val minMag = getMinMagnitude()
        val range = maxMag - minMag
        
        return if (range > 0) {
            magnitudes.map { (it - minMag) / range }.toFloatArray()
        } else {
            magnitudes.map { 0f }.toFloatArray()
        }
    }
    
    /**
     * Convert magnitudes to decibels
     * @return Array of magnitudes in dB
     */
    fun getMagnitudesInDB(): FloatArray {
        return magnitudes.map { 
            if (it > 0) 20 * kotlin.math.log10(it) else -100f 
        }.toFloatArray()
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SpectrogramFrame

        if (timestamp != other.timestamp) return false
        if (!frequencies.contentEquals(other.frequencies)) return false
        if (!magnitudes.contentEquals(other.magnitudes)) return false
        if (sampleRate != other.sampleRate) return false
        if (fftSize != other.fftSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + frequencies.contentHashCode()
        result = 31 * result + magnitudes.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + fftSize
        return result
    }
}