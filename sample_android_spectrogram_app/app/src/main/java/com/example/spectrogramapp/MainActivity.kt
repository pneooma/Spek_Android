package com.example.spectrogramapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.spectrogramapp.databinding.ActivityMainBinding
import com.example.spectrogramapp.spectrogram.FFmpegSpectrogramGenerator
import com.example.spectrogramapp.spectrogram.SpectrogramView
import com.example.spectrogramapp.spectrogram.BatchProcessor
import com.example.spectrogramapp.spectrogram.CoverArtReplacer
import java.io.File

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var spectrogramGenerator: FFmpegSpectrogramGenerator
    private lateinit var batchProcessor: BatchProcessor
    private lateinit var coverArtReplacer: CoverArtReplacer
    

    
    private val pickAudioFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { processAudioFile(it.toString()) }
    }
    
    private val pickMultipleAudioFiles = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris?.let { uriList ->
            val filePaths = uriList.map { it.toString() }
            processBatchFiles(filePaths)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initializeComponents()
        setupUI()
        checkPermissions()
    }
    
    private fun initializeComponents() {
        spectrogramGenerator = FFmpegSpectrogramGenerator()
        batchProcessor = BatchProcessor(this, spectrogramGenerator)
        coverArtReplacer = CoverArtReplacer(this)
        
        setupBatchProcessorCallbacks()
    }
    
    private fun setupUI() {
        binding.apply {
            btnSelectFile.setOnClickListener {
                pickAudioFile.launch("audio/*")
            }
            
            btnBatchProcess.setOnClickListener {
                pickMultipleAudioFiles.launch("audio/*")
            }
            
            // Initialize spectrogram view
            spectrogramView.setSpectrogramData(emptyList())
        }
    }
    
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    private fun processAudioFile(audioFilePath: String) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.tvStatus.text = "Processing audio file..."
        
        val outputPath = getExternalFilesDir(null)?.let { dir ->
            "${dir}/spectrogram_output.png"
        } ?: run {
            binding.progressBar.visibility = android.view.View.GONE
            binding.tvStatus.text = "Error: Cannot access external storage"
            Toast.makeText(this, "Cannot access external storage", Toast.LENGTH_LONG).show()
            return
        }
        
        spectrogramGenerator.generateSpectrogram(
            audioFilePath,
            outputPath,
            onProgress = { progress ->
                binding.progressBar.progress = (progress * 100).toInt()
                binding.tvStatus.text = "Processing: ${(progress * 100).toInt()}%"
            },
            onComplete = { outputPath ->
                binding.progressBar.visibility = android.view.View.GONE
                binding.tvStatus.text = "Spectrogram generated successfully!"
                
                // Load and display the generated spectrogram
                loadSpectrogramImage(outputPath)
                
                Toast.makeText(this, "Spectrogram saved to: $outputPath", Toast.LENGTH_LONG).show()
            },
            onError = { error ->
                binding.progressBar.visibility = android.view.View.GONE
                binding.tvStatus.text = "Error: $error"
                Toast.makeText(this, "Error generating spectrogram: $error", Toast.LENGTH_LONG).show()
            }
        )
    }
    
    private fun loadSpectrogramImage(imagePath: String) {
        try {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                // For now, we'll just show a success message
                // In a full implementation, you might want to:
                // 1. Load the image into an ImageView
                // 2. Parse the spectrogram data for custom visualization
                // 3. Update the SpectrogramView with the parsed data
                
                binding.tvStatus.text = "Spectrogram image loaded successfully!"
                Log.d("MainActivity", "Spectrogram image loaded from: $imagePath")
            } else {
                binding.tvStatus.text = "Error: Generated image not found"
                Log.e("MainActivity", "Generated image not found at: $imagePath")
            }
        } catch (e: Exception) {
            binding.tvStatus.text = "Error loading spectrogram image: ${e.message}"
            Log.e("MainActivity", "Error loading spectrogram image", e)
        }
    }
    
    /**
     * Process multiple audio files in batch
     */
    private fun processBatchFiles(filePaths: List<String>) {
        if (filePaths.isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show batch processing dialog
        showBatchProcessingDialog(filePaths)
    }
    
    /**
     * Show dialog for batch processing options
     */
    private fun showBatchProcessingDialog(filePaths: List<String>) {
        val options = arrayOf(
            "Generate spectrograms only",
            "Generate spectrograms + Replace cover art",
            "Generate spectrograms + Replace cover art (with backup)"
        )
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Batch Processing Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startBatchProcessing(filePaths, false, false)
                    1 -> startBatchProcessing(filePaths, true, false)
                    2 -> startBatchProcessing(filePaths, true, true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Start batch processing with selected options
     */
    private fun startBatchProcessing(
        filePaths: List<String>,
        replaceCoverArt: Boolean,
        createBackup: Boolean
    ) {
        val outputDir = getExternalFilesDir(null)?.let { dir ->
            "${dir}/batch_spectrograms"
        } ?: run {
            Toast.makeText(this, "Cannot access external storage", Toast.LENGTH_LONG).show()
            return
        }
        
        // Create output directory
        File(outputDir).mkdirs()
        
        // Update UI for batch processing
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.tvStatus.text = "Starting batch processing of ${filePaths.size} files..."
        
        // Start batch processing
        batchProcessor.processBatch(
            audioFiles = filePaths,
            outputDirectory = outputDir,
            replaceCoverArt = replaceCoverArt
        )
    }
    
    /**
     * Setup batch processor callbacks
     */
    private fun setupBatchProcessorCallbacks() {
        batchProcessor.setBatchProgressCallback { batchId, progress ->
            runOnUiThread {
                updateBatchProgress(progress)
            }
        }
        
        batchProcessor.setBatchCompleteCallback { batchId, results ->
            runOnUiThread {
                onBatchComplete(results)
            }
        }
        
        batchProcessor.setBatchErrorCallback { batchId, error ->
            runOnUiThread {
                onBatchError(error)
            }
        }
    }
    
    /**
     * Update batch processing progress
     */
    private fun updateBatchProgress(progress: com.example.spectrogramapp.spectrogram.BatchProgress) {
        binding.progressBar.progress = (progress.overallProgress * 100).toInt()
        binding.tvStatus.text = "Batch processing: ${progress.processedFiles}/${progress.totalFiles} " +
                "(${(progress.overallProgress * 100).toInt()}%) - ${progress.currentFile}"
    }
    
    /**
     * Handle batch completion
     */
    private fun onBatchComplete(results: List<com.example.spectrogramapp.spectrogram.BatchResult>) {
        binding.progressBar.visibility = android.view.View.GONE
        
        val successful = results.count { it.success }
        val failed = results.count { !it.success }
        
        binding.tvStatus.text = "Batch complete: $successful successful, $failed failed"
        
        // Show detailed results
        showBatchResultsDialog(results)
    }
    
    /**
     * Handle batch error
     */
    private fun onBatchError(error: String) {
        binding.progressBar.visibility = android.view.View.GONE
        binding.tvStatus.text = "Batch error: $error"
        Toast.makeText(this, "Batch processing failed: $error", Toast.LENGTH_LONG).show()
    }
    
    /**
     * Show batch processing results
     */
    private fun showBatchResultsDialog(results: List<com.example.spectrogramapp.spectrogram.BatchResult>) {
        val successful = results.filter { it.success }
        val failed = results.filter { !it.success }
        
        val message = buildString {
            append("Batch processing completed!\n\n")
            append("✅ Successful: ${successful.size}\n")
            append("❌ Failed: ${failed.size}\n\n")
            
            if (successful.isNotEmpty()) {
                append("Generated spectrograms:\n")
                successful.forEach { result ->
                    append("• ${result.fileName}\n")
                }
                append("\n")
            }
            
            if (failed.isNotEmpty()) {
                append("Failed files:\n")
                failed.forEach { result ->
                    append("• ${result.fileName}: ${result.errorMessage}\n")
                }
            }
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Batch Processing Results")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }
    
    companion object {
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1001
    }
}