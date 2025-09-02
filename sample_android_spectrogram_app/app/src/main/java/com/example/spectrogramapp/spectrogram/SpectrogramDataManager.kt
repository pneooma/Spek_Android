package com.example.spectrogramapp.spectrogram

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Memory-efficient manager for spectrogram data with pagination and streaming support
 */
class SpectrogramDataManager {
    
    companion object {
        private const val TAG = "SpectrogramDataManager"
        
        // Memory management constants
        private const val MAX_MEMORY_USAGE_MB = 100L // 100MB max memory usage
        private const val DEFAULT_PAGE_SIZE = 100 // Number of frames per page
        private const val MAX_CACHED_PAGES = 5 // Maximum number of pages to keep in memory
        private const val MEMORY_CHECK_INTERVAL_MS = 5000L // Check memory every 5 seconds
    }
    
    // Data storage with pagination
    private val framePages = ConcurrentHashMap<Int, List<SpectrogramFrame>>()
    private val pageTimestamps = ConcurrentHashMap<Int, Long>()
    
    // Memory management
    private var currentMemoryUsage = 0L
    private var totalFrames = 0
    private var pageSize = DEFAULT_PAGE_SIZE
    private var currentPage = 0
    

    
    // Coroutine scope for background operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var memoryMonitorJob: Job? = null
    
    // Callbacks
    private var onMemoryWarning: ((String) -> Unit)? = null
    private var onPageLoaded: ((Int, List<SpectrogramFrame>) -> Unit)? = null
    
    init {
        startMemoryMonitoring()
    }
    
    /**
     * Add spectrogram frames with automatic memory management
     */
    fun addFrames(frames: List<SpectrogramFrame>) {
        if (frames.isEmpty()) return
        
        scope.launch {
            try {
                // Check memory before adding
                if (!checkMemoryAvailability(frames)) {
                    onMemoryWarning?.invoke("Low memory, clearing old data")
                    clearOldestPages()
                }
                
                // Add frames to appropriate pages
                val pages = frames.chunked(pageSize)
                pages.forEachIndexed { pageIndex, pageFrames ->
                    val globalPageIndex = currentPage + pageIndex
                    framePages[globalPageIndex] = pageFrames
                    pageTimestamps[globalPageIndex] = System.currentTimeMillis()
                    
                    // Notify page loaded
                    onPageLoaded?.invoke(globalPageIndex, pageFrames)
                }
                
                currentPage += pages.size
                totalFrames += frames.size
                
                // Update memory usage
                updateMemoryUsage()
                
                Log.d(TAG, "Added ${frames.size} frames, total: $totalFrames, pages: ${framePages.size}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error adding frames", e)
            }
        }
    }
    
    /**
     * Get frames for a specific page
     */
    fun getPage(pageIndex: Int): List<SpectrogramFrame>? {
        return framePages[pageIndex]
    }
    
    /**
     * Get frames for a time range with pagination
     */
    fun getFramesInRange(
        startTime: Long,
        endTime: Long,
        maxFrames: Int = pageSize
    ): List<SpectrogramFrame> {
        val result = mutableListOf<SpectrogramFrame>()
        var frameCount = 0
        
        // Find pages that contain frames in the time range
        for (pageIndex in framePages.keys.sorted()) {
            val pageFrames = framePages[pageIndex] ?: continue
            
            for (frame in pageFrames) {
                if (frame.timestamp in startTime..endTime && frameCount < maxFrames) {
                    result.add(frame)
                    frameCount++
                }
            }
            
            if (frameCount >= maxFrames) break
        }
        
        return result
    }
    

    
    /**
     * Clear all data and reset state
     */
    fun clear() {
        framePages.clear()
        pageTimestamps.clear()
        currentMemoryUsage = 0L
        totalFrames = 0
        currentPage = 0
        Log.d(TAG, "Cleared all data")
    }
    
    /**
     * Clear oldest pages to free memory
     */
    private fun clearOldestPages() {
        val sortedPages = pageTimestamps.entries.sortedBy { it.value }
        val pagesToRemove = min(sortedPages.size - MAX_CACHED_PAGES, sortedPages.size / 2)
        
        for (i in 0 until pagesToRemove) {
            val pageIndex = sortedPages[i].key
            framePages.remove(pageIndex)
            pageTimestamps.remove(pageIndex)
        }
        
        Log.d(TAG, "Cleared $pagesToRemove oldest pages")
    }
    
    /**
     * Check if we have enough memory for new frames
     */
    private fun checkMemoryAvailability(newFrames: List<SpectrogramFrame>): Boolean {
        val estimatedNewMemory = estimateMemoryUsage(newFrames)
        val totalMemoryNeeded = currentMemoryUsage + estimatedNewMemory
        
        return totalMemoryNeeded <= MAX_MEMORY_USAGE_MB * 1024 * 1024
    }
    
    /**
     * Estimate memory usage of frames
     */
    private fun estimateMemoryUsage(frames: List<SpectrogramFrame>): Long {
        if (frames.isEmpty()) return 0L
        
        val sampleFrame = frames.first()
        val frameSize = sampleFrame.frequencies.size * 4 + // Float array (4 bytes per float)
                       sampleFrame.magnitudes.size * 4 +  // Float array (4 bytes per float)
                       16 // Long + Int + Int + Int (timestamp, sampleRate, fftSize)
        
        return frames.size * frameSize.toLong()
    }
    
    /**
     * Update current memory usage
     */
    private fun updateMemoryUsage() {
        currentMemoryUsage = estimateMemoryUsage(framePages.values.flatten())
    }
    
    /**
     * Start memory monitoring
     */
    private fun startMemoryMonitoring() {
        memoryMonitorJob = scope.launch {
            while (isActive) {
                try {
                    delay(MEMORY_CHECK_INTERVAL_MS)
                    
                    // Check memory usage
                    val memoryUsageMB = currentMemoryUsage / (1024 * 1024)
                    if (memoryUsageMB > MAX_MEMORY_USAGE_MB * 0.8) {
                        Log.w(TAG, "Memory usage high: ${memoryUsageMB}MB")
                        onMemoryWarning?.invoke("High memory usage, consider clearing data")
                    }
                    
                    // Clean up old pages if needed
                    if (framePages.size > MAX_CACHED_PAGES) {
                        clearOldestPages()
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in memory monitoring", e)
                }
            }
        }
    }
    
    /**
     * Set memory warning callback
     */
    fun setMemoryWarningCallback(callback: (String) -> Unit) {
        onMemoryWarning = callback
    }
    
    /**
     * Set page loaded callback
     */
    fun setPageLoadedCallback(callback: (Int, List<SpectrogramFrame>) -> Unit) {
        onPageLoaded = callback
    }
    

    
    /**
     * Get memory usage statistics
     */
    fun getMemoryStats(): MemoryStats {
        return MemoryStats(
            currentUsageMB = currentMemoryUsage / (1024 * 1024),
            maxUsageMB = MAX_MEMORY_USAGE_MB,
            totalFrames = totalFrames,
            cachedPages = framePages.size,
            maxCachedPages = MAX_CACHED_PAGES
        )
    }
    
    /**
     * Release resources
     */
    fun release() {
        memoryMonitorJob?.cancel()
        scope.cancel()
        clear()
        Log.d(TAG, "Released SpectrogramDataManager")
    }
}

/**
 * Memory usage statistics
 */
data class MemoryStats(
    val currentUsageMB: Long,
    val maxUsageMB: Long,
    val totalFrames: Int,
    val cachedPages: Int,
    val maxCachedPages: Int
) {
    val memoryUsagePercent: Float
        get() = (currentUsageMB.toFloat() / maxUsageMB.toFloat()) * 100f
    
    val isMemoryHigh: Boolean
        get() = memoryUsagePercent > 80f
}