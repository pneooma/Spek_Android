package com.example.spectrogramapp.spectrogram

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import kotlin.math.*

/**
 * Custom View for rendering spectrogram visualizations
 * Supports both static and real-time spectrogram data
 */
class SpectrogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    companion object {
        private const val TAG = "SpectrogramView"
        
        // Default visualization parameters
        private const val DEFAULT_MAX_FREQUENCY = 22050f  // Hz (Nyquist frequency for 44.1kHz)
        private const val DEFAULT_MIN_DB = -80f           // Minimum decibel value
        private const val DEFAULT_MAX_DB = 0f             // Maximum decibel value
        private const val DEFAULT_COLOR_SCHEME = ColorScheme.RAINBOW
        private const val DEFAULT_GRID_ENABLED = true
        private const val DEFAULT_AXIS_LABELS_ENABLED = true
    }
    
    // Spectrogram data
    private val spectrogramData = mutableListOf<SpectrogramFrame>()
    private var maxMagnitude = 0f
    private var minMagnitude = 0f
    
    // Visualization parameters
    private var maxFrequency = DEFAULT_MAX_FREQUENCY
    private var minDB = DEFAULT_MIN_DB
    private var maxDB = DEFAULT_MAX_DB
    private var colorScheme = DEFAULT_COLOR_SCHEME
    private var gridEnabled = DEFAULT_GRID_ENABLED
    private var axisLabelsEnabled = DEFAULT_AXIS_LABELS_ENABLED
    
    // Drawing objects
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Color mapping cache
    private val colorCache = mutableMapOf<Float, Int>()
    
    init {
        initializePaints()
    }
    
    /**
     * Initialize paint objects with default styles
     */
    private fun initializePaints() {
        // Main spectrogram paint
        paint.style = Paint.Style.FILL
        
        // Text paint for labels
        textPaint.apply {
            color = Color.WHITE
            textSize = 12f
            textAlign = Paint.Align.LEFT
        }
        
        // Grid paint
        gridPaint.apply {
            color = Color.GRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
            alpha = 128
        }
        
        // Axis paint
        axisPaint.apply {
            color = Color.WHITE
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (spectrogramData.isEmpty()) {
            drawEmptyState(canvas)
            return
        }
        
        // Clear background
        canvas.drawColor(Color.BLACK)
        
        // Draw spectrogram
        drawSpectrogram(canvas)
        
        // Draw grid and labels
        if (gridEnabled) {
            drawGrid(canvas)
        }
        
        if (axisLabelsEnabled) {
            drawAxisLabels(canvas)
        }
    }
    
    /**
     * Draw the main spectrogram visualization
     */
    private fun drawSpectrogram(canvas: Canvas) {
        if (spectrogramData.isEmpty()) return
        
        val frameCount = spectrogramData.size
        val frequencyCount = spectrogramData[0].frequencies.size
        
        if (frameCount == 0 || frequencyCount == 0) return
        
        val cellWidth = width.toFloat() / frameCount
        val cellHeight = height.toFloat() / frequencyCount
        
        // Draw each cell of the spectrogram
        for (timeIndex in spectrogramData.indices) {
            val frame = spectrogramData[timeIndex]
            val x = timeIndex * cellWidth
            
            for (freqIndex in frame.frequencies.indices) {
                val y = height - (freqIndex * cellHeight)
                val magnitude = frame.magnitudes[freqIndex]
                
                // Get color for this magnitude
                val color = getColorForMagnitude(magnitude)
                paint.color = color
                
                // Draw the cell
                canvas.drawRect(x, y, x + cellWidth, y + cellHeight, paint)
            }
        }
    }
    
    /**
     * Draw grid lines for frequency and time reference
     */
    private fun drawGrid(canvas: Canvas) {
        val frameCount = spectrogramData.size
        val frequencyCount = spectrogramData[0].frequencies.size
        
        if (frameCount == 0 || frequencyCount == 0) return
        
        val cellWidth = width.toFloat() / frameCount
        val cellHeight = height.toFloat() / frequencyCount
        
        // Vertical lines (time divisions)
        for (i in 0..frameCount step max(1, frameCount / 10)) {
            val x = i * cellWidth
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
        }
        
        // Horizontal lines (frequency divisions)
        for (i in 0..frequencyCount step max(1, frequencyCount / 10)) {
            val y = height - (i * cellHeight)
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
    }
    
    /**
     * Draw axis labels for frequency and time
     */
    private fun drawAxisLabels(canvas: Canvas) {
        val frameCount = spectrogramData.size
        val frequencyCount = spectrogramData[0].frequencies.size
        
        if (frameCount == 0 || frequencyCount == 0) return
        
        val cellWidth = width.toFloat() / frameCount
        val cellHeight = height.toFloat() / frequencyCount
        
        // Frequency labels (left side)
        for (i in 0..frequencyCount step max(1, frequencyCount / 8)) {
            val y = height - (i * cellHeight)
            val frequency = spectrogramData[0].frequencies[i]
            val label = formatFrequency(frequency)
            
            canvas.drawText(label, 8f, y - 5f, textPaint)
        }
        
        // Time labels (bottom)
        for (i in 0..frameCount step max(1, frameCount / 8)) {
            val x = i * cellWidth
            val time = i * spectrogramData[0].fftSize / spectrogramData[0].sampleRate.toFloat()
            val label = formatTime(time)
            
            canvas.save()
            canvas.rotate(-90f, x, height - 20f)
            canvas.drawText(label, x, height - 20f, textPaint)
            canvas.restore()
        }
    }
    
    /**
     * Draw empty state when no data is available
     */
    private fun drawEmptyState(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 16f
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        canvas.drawText("No spectrogram data", centerX, centerY, textPaint)
        textPaint.textSize = 12f
        canvas.drawText("Select an audio file or start real-time recording", centerX, centerY + 30f, textPaint)
        
        textPaint.textAlign = Paint.Align.LEFT
    }
    
    /**
     * Get color for a given magnitude value
     */
    private fun getColorForMagnitude(magnitude: Float): Int {
        // Check cache first
        colorCache[magnitude]?.let { return it }
        
        // Normalize magnitude to 0-1 range
        val normalizedMagnitude = if (maxMagnitude > minMagnitude) {
            (magnitude - minMagnitude) / (maxMagnitude - minMagnitude)
        } else {
            0f
        }.coerceIn(0f, 1f)
        
        // Generate color based on scheme
        val color = when (colorScheme) {
            ColorScheme.RAINBOW -> getRainbowColor(normalizedMagnitude)
            ColorScheme.GRAYSCALE -> getGrayscaleColor(normalizedMagnitude)
            ColorScheme.HEAT -> getHeatColor(normalizedMagnitude)
            ColorScheme.VIRIDIS -> getViridisColor(normalizedMagnitude)
        }
        
        // Cache the color
        colorCache[magnitude] = color
        return color
    }
    
    /**
     * Get rainbow color scheme
     */
    private fun getRainbowColor(normalizedValue: Float): Int {
        val hue = normalizedValue * 240f  // 0 = red, 120 = green, 240 = blue
        return Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
    }
    
    /**
     * Get grayscale color scheme
     */
    private fun getGrayscaleColor(normalizedValue: Float): Int {
        val intensity = (normalizedValue * 255).toInt()
        return Color.rgb(intensity, intensity, intensity)
    }
    
    /**
     * Get heat color scheme (blue -> red)
     */
    private fun getHeatColor(normalizedValue: Float): Int {
        return when {
            normalizedValue < 0.25f -> {
                val intensity = (normalizedValue * 4 * 255).toInt()
                Color.rgb(0, 0, intensity)
            }
            normalizedValue < 0.5f -> {
                val intensity = ((normalizedValue - 0.25f) * 4 * 255).toInt()
                Color.rgb(0, intensity, 255)
            }
            normalizedValue < 0.75f -> {
                val intensity = ((normalizedValue - 0.5f) * 4 * 255).toInt()
                Color.rgb(intensity, 255, 255 - intensity)
            }
            else -> {
                val intensity = ((normalizedValue - 0.75f) * 4 * 255).toInt()
                Color.rgb(255, 255 - intensity, 0)
            }
        }
    }
    
    /**
     * Get viridis color scheme (scientific visualization standard)
     */
    private fun getViridisColor(normalizedValue: Float): Int {
        // Simplified viridis implementation
        return when {
            normalizedValue < 0.25f -> Color.rgb(68, 1, 84)
            normalizedValue < 0.5f -> Color.rgb(49, 104, 142)
            normalizedValue < 0.75f -> Color.rgb(53, 183, 121)
            else -> Color.rgb(254, 231, 36)
        }
    }
    
    /**
     * Format frequency for display
     */
    private fun formatFrequency(frequency: Float): String {
        return when {
            frequency >= 1000f -> "${(frequency / 1000f).toInt()}k"
            else -> frequency.toInt().toString()
        }
    }
    
    /**
     * Format time for display
     */
    private fun formatTime(seconds: Float): String {
        return "%.1fs".format(seconds)
    }
    
    /**
     * Update spectrogram data and redraw
     */
    fun updateSpectrogramData(newData: List<SpectrogramFrame>) {
        spectrogramData.clear()
        spectrogramData.addAll(newData)
        
        // Calculate magnitude range for normalization
        if (spectrogramData.isNotEmpty()) {
            maxMagnitude = spectrogramData.maxOfOrNull { frame ->
                frame.magnitudes.maxOrNull() ?: 0f
            } ?: 0f
            
            minMagnitude = spectrogramData.minOfOrNull { frame ->
                frame.magnitudes.minOrNull() ?: 0f
            } ?: 0f
        }
        
        // Clear color cache for new data
        colorCache.clear()
        
        // Request redraw
        invalidate()
        
        Log.d(TAG, "Updated spectrogram data: ${spectrogramData.size} frames")
    }
    
    /**
     * Set spectrogram data (alias for updateSpectrogramData)
     */
    fun setSpectrogramData(data: List<SpectrogramFrame>) {
        updateSpectrogramData(data)
    }
    
    /**
     * Update visualization parameters
     */
    fun updateVisualizationParams(
        maxFreq: Float = maxFrequency,
        minDb: Float = minDB,
        maxDb: Float = maxDB,
        scheme: ColorScheme = colorScheme,
        grid: Boolean = gridEnabled,
        labels: Boolean = axisLabelsEnabled
    ) {
        maxFrequency = maxFreq
        minDB = minDb
        maxDB = maxDb
        colorScheme = scheme
        gridEnabled = grid
        axisLabelsEnabled = labels
        
        // Clear cache and redraw
        colorCache.clear()
        invalidate()
    }
    
    /**
     * Clear all data and reset view
     */
    fun clear() {
        spectrogramData.clear()
        colorCache.clear()
        maxMagnitude = 0f
        minMagnitude = 0f
        invalidate()
    }
}

/**
 * Color scheme options for spectrogram visualization
 */
enum class ColorScheme {
    RAINBOW,    // Traditional rainbow mapping
    GRAYSCALE,  // Black to white
    HEAT,       // Blue to red heat map
    VIRIDIS     // Scientific viridis colormap
}