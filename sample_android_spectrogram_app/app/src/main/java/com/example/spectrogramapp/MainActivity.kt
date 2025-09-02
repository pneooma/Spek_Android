package com.example.spectrogramapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.spectrogramapp.databinding.ActivityMainBinding
import com.example.spectrogramapp.spectrogram.FFmpegSpectrogramGenerator
import com.example.spectrogramapp.spectrogram.RealTimeAudioProcessor
import com.example.spectrogramapp.spectrogram.SpectrogramView

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var spectrogramGenerator: FFmpegSpectrogramGenerator
    private lateinit var realTimeProcessor: RealTimeAudioProcessor
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRealTimeSpectrogram()
        } else {
            Toast.makeText(this, "Permission required for microphone access", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val pickAudioFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { processAudioFile(it.toString()) }
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
        realTimeProcessor = RealTimeAudioProcessor()
    }
    
    private fun setupUI() {
        binding.apply {
            btnSelectFile.setOnClickListener {
                pickAudioFile.launch("audio/*")
            }
            
            btnRealTime.setOnClickListener {
                if (checkMicrophonePermission()) {
                    startRealTimeSpectrogram()
                } else {
                    requestMicrophonePermission()
                }
            }
            
            btnStopRealTime.setOnClickListener {
                stopRealTimeSpectrogram()
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
    
    private fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestMicrophonePermission() {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    
    private fun processAudioFile(audioFilePath: String) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.tvStatus.text = "Processing audio file..."
        
        val outputPath = "${getExternalFilesDir(null)}/spectrogram_output.png"
        
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
        // Implementation to load and display the generated spectrogram image
        // This would typically involve loading the image into an ImageView
        // or parsing the spectrogram data for custom visualization
    }
    
    private fun startRealTimeSpectrogram() {
        binding.btnRealTime.isEnabled = false
        binding.btnStopRealTime.isEnabled = true
        binding.tvStatus.text = "Real-time spectrogram active"
        
        realTimeProcessor.startRecording(
            onFrameData = { frame ->
                // Update the spectrogram view with new frame data
                runOnUiThread {
                    updateRealTimeSpectrogram(frame)
                }
            }
        )
    }
    
    private fun stopRealTimeSpectrogram() {
        realTimeProcessor.stopRecording()
        binding.btnRealTime.isEnabled = true
        binding.btnStopRealTime.isEnabled = false
        binding.tvStatus.text = "Real-time spectrogram stopped"
    }
    
    private fun updateRealTimeSpectrogram(frame: SpectrogramFrame) {
        // Update the spectrogram view with real-time data
        binding.spectrogramView.updateSpectrogramData(listOf(frame))
    }
    
    override fun onDestroy() {
        super.onDestroy()
        realTimeProcessor.stopRecording()
    }
    
    companion object {
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1001
    }
}