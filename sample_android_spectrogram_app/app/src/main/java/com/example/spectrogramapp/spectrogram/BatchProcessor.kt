package com.example.spectrogramapp.spectrogram

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import com.example.spectrogramapp.spectrogram.CoverArtReplacer

/**
 * Handles batch processing of multiple audio files
 * Generates spectrograms for each file and optionally replaces cover art
 */
class BatchProcessor(
    private val context: Context,
    private val spectrogramGenerator: FFmpegSpectrogramGenerator
) {
    private val coverArtReplacer = CoverArtReplacer(context)
    
    companion object {
        private const val TAG = "BatchProcessor"
        
        // Batch processing constants
        private const val MAX_CONCURRENT_JOBS = 3
        private const val BATCH_TIMEOUT_MS = 300000L // 5 minutes
        private const val PROGRESS_UPDATE_INTERVAL_MS = 100L
    }
    
    // Processing state
    private var isProcessing = false
    private var currentBatchId: String? = null
    
    // Job management
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    
    // Batch progress tracking
    private val batchProgress = ConcurrentHashMap<String, BatchProgress>()
    
    // Callbacks
    private var onBatchProgress: ((String, BatchProgress) -> Unit)? = null
    private var onBatchComplete: ((String, List<BatchResult>) -> Unit)? = null
    private var onBatchError: ((String, String) -> Unit)? = null
    
    /**
     * Process a batch of audio files
     * @param audioFiles List of audio file paths to process
     * @param outputDirectory Directory to save spectrograms
     * @param replaceCoverArt Whether to replace cover art with spectrograms
     * @param batchId Unique identifier for this batch
     */
    fun processBatch(
        audioFiles: List<String>,
        outputDirectory: String,
        replaceCoverArt: Boolean = false,
        batchId: String = generateBatchId()
    ) {
        if (isProcessing) {
            Log.w(TAG, "Batch processing already in progress")
            return
        }
        
        if (audioFiles.isEmpty()) {
            onBatchError?.invoke(batchId, "No audio files provided")
            return
        }
        
        currentBatchId = batchId
        isProcessing = true
        
        // Initialize batch progress
        val progress = BatchProgress(
            batchId = batchId,
            totalFiles = audioFiles.size,
            processedFiles = 0,
            successfulFiles = 0,
            failedFiles = 0,
            currentFile = "",
            overallProgress = 0f
        )
        
        batchProgress[batchId] = progress
        onBatchProgress?.invoke(batchId, progress)
        
        Log.d(TAG, "Starting batch processing: $batchId with ${audioFiles.size} files")
        
        scope.launch {
            try {
                val results = mutableListOf<BatchResult>()
                val semaphore = Semaphore(MAX_CONCURRENT_JOBS)
                
                // Process files with limited concurrency
                val jobs = audioFiles.mapIndexed { index, filePath ->
                    async {
                        semaphore.withPermit {
                            processSingleFile(
                                filePath = filePath,
                                outputDirectory = outputDirectory,
                                replaceCoverArt = replaceCoverArt,
                                fileIndex = index,
                                batchId = batchId
                            )
                        }
                    }
                }
                
                // Wait for all jobs to complete
                val jobResults = jobs.awaitAll()
                results.addAll(jobResults.filterNotNull())
                
                // Finalize batch
                finalizeBatch(batchId, results)
                
            } catch (e: Exception) {
                Log.e(TAG, "Batch processing failed", e)
                onBatchError?.invoke(batchId, "Batch processing failed: ${e.message}")
            } finally {
                isProcessing = false
                currentBatchId = null
            }
        }
    }
    
    /**
     * Process a single audio file in the batch
     */
    private suspend fun processSingleFile(
        filePath: String,
        outputDirectory: String,
        replaceCoverArt: Boolean,
        fileIndex: Int,
        batchId: String
    ): BatchResult? {
        return try {
            val fileName = File(filePath).nameWithoutExtension
            val outputPath = "$outputDirectory/${fileName}_spectrogram.png"
            
            Log.d(TAG, "Processing file $fileIndex: $fileName")
            
            // Update progress
            updateFileProgress(batchId, fileIndex, fileName, 0f)
            
            // Generate spectrogram
            val result = suspendCancellableCoroutine<BatchResult> { continuation ->
                spectrogramGenerator.generateSpectrogram(
                    audioFilePath = filePath,
                    outputPath = outputPath,
                    onProgress = { progress ->
                        updateFileProgress(batchId, fileIndex, fileName, progress)
                    },
                    onComplete = { outputPath ->
                        val batchResult = BatchResult(
                            originalPath = filePath,
                            spectrogramPath = outputPath,
                            fileName = fileName,
                            success = true,
                            errorMessage = null
                        )
                        
                        if (replaceCoverArt) {
                            // Replace cover art with spectrogram
                            val replacementResult = coverArtReplacer.replaceCoverArt(
                                audioFilePath = filePath,
                                spectrogramPath = outputPath,
                                backupOriginal = true
                            )
                            
                            if (replacementResult.success) {
                                Log.d(TAG, "Cover art replaced successfully for: $filePath")
                            } else {
                                Log.w(TAG, "Cover art replacement failed for: $filePath: ${replacementResult.errorMessage}")
                            }
                        }
                        
                        continuation.resume(batchResult) {}
                    },
                    onError = { error ->
                        val batchResult = BatchResult(
                            originalPath = filePath,
                            spectrogramPath = null,
                            fileName = fileName,
                            success = false,
                            errorMessage = error
                        )
                        continuation.resume(batchResult) {}
                    }
                )
            }
            
            // Update batch progress
            updateBatchProgress(batchId, result.success)
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing file: $filePath", e)
            
            val result = BatchResult(
                originalPath = filePath,
                spectrogramPath = null,
                fileName = File(filePath).nameWithoutExtension,
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
            
            updateBatchProgress(batchId, false)
            result
        }
    }
    

    
    /**
     * Update progress for a specific file
     */
    private fun updateFileProgress(
        batchId: String,
        fileIndex: Int,
        fileName: String,
        progress: Float
    ) {
        batchProgress[batchId]?.let { currentProgress ->
            val updatedProgress = currentProgress.copy(
                currentFile = fileName,
                overallProgress = (fileIndex + progress) / currentProgress.totalFiles
            )
            
            batchProgress[batchId] = updatedProgress
            onBatchProgress?.invoke(batchId, updatedProgress)
        }
    }
    
    /**
     * Update overall batch progress
     */
    private fun updateBatchProgress(batchId: String, fileSuccess: Boolean) {
        batchProgress[batchId]?.let { currentProgress ->
            val updatedProgress = currentProgress.copy(
                processedFiles = currentProgress.processedFiles + 1,
                successfulFiles = currentProgress.successfulFiles + (if (fileSuccess) 1 else 0),
                failedFiles = currentProgress.failedFiles + (if (fileSuccess) 0 else 1),
                overallProgress = (currentProgress.processedFiles + 1).toFloat() / currentProgress.totalFiles
            )
            
            batchProgress[batchId] = updatedProgress
            onBatchProgress?.invoke(batchId, updatedProgress)
        }
    }
    
    /**
     * Finalize batch processing
     */
    private fun finalizeBatch(batchId: String, results: List<BatchResult>) {
        val progress = batchProgress[batchId]
        if (progress != null) {
            val finalProgress = progress.copy(
                processedFiles = progress.totalFiles,
                overallProgress = 1f,
                currentFile = "Complete"
            )
            
            batchProgress[batchId] = finalProgress
            onBatchProgress?.invoke(batchId, finalProgress)
        }
        
        Log.d(TAG, "Batch completed: $batchId, ${results.size} results")
        onBatchComplete?.invoke(batchId, results)
    }
    
    /**
     * Cancel current batch processing
     */
    fun cancelBatch(batchId: String) {
        activeJobs[batchId]?.cancel()
        activeJobs.remove(batchId)
        
        batchProgress[batchId]?.let { progress ->
            val cancelledProgress = progress.copy(
                currentFile = "Cancelled",
                overallProgress = progress.processedFiles.toFloat() / progress.totalFiles
            )
            
            batchProgress[batchId] = cancelledProgress
            onBatchProgress?.invoke(batchId, cancelledProgress)
        }
        
        if (currentBatchId == batchId) {
            isProcessing = false
            currentBatchId = null
        }
        
        Log.d(TAG, "Batch cancelled: $batchId")
    }
    
    /**
     * Get current batch progress
     */
    fun getBatchProgress(batchId: String): BatchProgress? {
        return batchProgress[batchId]
    }
    
    /**
     * Check if batch is currently processing
     */
    fun isBatchProcessing(batchId: String): Boolean {
        return currentBatchId == batchId && isProcessing
    }
    
    /**
     * Set batch progress callback
     */
    fun setBatchProgressCallback(callback: (String, BatchProgress) -> Unit) {
        onBatchProgress = callback
    }
    
    /**
     * Set batch complete callback
     */
    fun setBatchCompleteCallback(callback: (String, List<BatchResult>) -> Unit) {
        onBatchComplete = callback
    }
    
    /**
     * Set batch error callback
     */
    fun setBatchErrorCallback(callback: (String, String) -> Unit) {
        onBatchError = callback
    }
    
    /**
     * Generate unique batch ID
     */
    private fun generateBatchId(): String {
        return "batch_${System.currentTimeMillis()}_${(0..9999).random()}"
    }
    
    /**
     * Release resources
     */
    fun release() {
        scope.cancel()
        activeJobs.clear()
        batchProgress.clear()
        isProcessing = false
        currentBatchId = null
        Log.d(TAG, "BatchProcessor released")
    }
}

/**
 * Data class for batch processing progress
 */
data class BatchProgress(
    val batchId: String,
    val totalFiles: Int,
    val processedFiles: Int,
    val successfulFiles: Int,
    val failedFiles: Int,
    val currentFile: String,
    val overallProgress: Float
) {
    val successRate: Float
        get() = if (processedFiles > 0) successfulFiles.toFloat() / processedFiles else 0f
    
    val isComplete: Boolean
        get() = processedFiles >= totalFiles
}

/**
 * Data class for batch processing results
 */
data class BatchResult(
    val originalPath: String,
    val spectrogramPath: String?,
    val fileName: String,
    val success: Boolean,
    val errorMessage: String?
)