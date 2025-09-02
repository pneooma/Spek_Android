package com.example.spectrogramapp.spectrogram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.images.ArtworkFactory

/**
 * Handles replacement of audio file cover art with spectrograms
 * Supports MP3, FLAC, and other audio formats
 */
class CoverArtReplacer(private val context: Context) {
    
    companion object {
        private const val TAG = "CoverArtReplacer"
        
        // Supported audio formats
        private val SUPPORTED_FORMATS = setOf("mp3", "flac", "m4a", "ogg", "wma")
        
        // Cover art dimensions
        private const val DEFAULT_COVER_ART_SIZE = 500
        private const val MAX_COVER_ART_SIZE = 1000
    }
    
    /**
     * Check if the audio format supports cover art replacement
     */
    fun isFormatSupported(filePath: String): Boolean {
        val extension = File(filePath).extension.lowercase()
        return SUPPORTED_FORMATS.contains(extension)
    }
    
    /**
     * Replace cover art with spectrogram image
     * @param audioFilePath Path to the audio file
     * @param spectrogramPath Path to the spectrogram image
     * @param backupOriginal Whether to create a backup of the original file
     * @return Success status and backup path if created
     */
    fun replaceCoverArt(
        audioFilePath: String,
        spectrogramPath: String,
        backupOriginal: Boolean = true
    ): CoverArtReplacementResult {
        return try {
            if (!isFormatSupported(audioFilePath)) {
                return CoverArtReplacementResult(
                    success = false,
                    errorMessage = "Audio format not supported for cover art replacement",
                    backupPath = null
                )
            }
            
            val audioFile = File(audioFilePath)
            val spectrogramFile = File(spectrogramPath)
            
            if (!audioFile.exists()) {
                return CoverArtReplacementResult(
                    success = false,
                    errorMessage = "Audio file not found: $audioFilePath",
                    backupPath = null
                )
            }
            
            if (!spectrogramFile.exists()) {
                return CoverArtReplacementResult(
                    success = false,
                    errorMessage = "Spectrogram file not found: $spectrogramPath",
                    backupPath = null
                )
            }
            
            // Create backup if requested
            val backupPath = if (backupOriginal) {
                createBackup(audioFilePath)
            } else null
            
            // Replace cover art based on file format
            val success = when (audioFile.extension.lowercase()) {
                "mp3" -> replaceMP3CoverArt(audioFilePath, spectrogramPath)
                "flac" -> replaceFLACCoverArt(audioFilePath, spectrogramPath)
                "m4a" -> replaceM4ACoverArt(audioFilePath, spectrogramPath)
                else -> replaceGenericCoverArt(audioFilePath, spectrogramPath)
            }
            
            if (success) {
                Log.d(TAG, "Successfully replaced cover art for: $audioFilePath")
                CoverArtReplacementResult(
                    success = true,
                    errorMessage = null,
                    backupPath = backupPath
                )
            } else {
                CoverArtReplacementResult(
                    success = false,
                    errorMessage = "Failed to replace cover art",
                    backupPath = backupPath
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing cover art for: $audioFilePath", e)
            CoverArtReplacementResult(
                success = false,
                errorMessage = "Exception: ${e.message}",
                backupPath = null
            )
        }
    }
    
    /**
     * Create a backup of the original audio file
     */
    private fun createBackup(originalPath: String): String? {
        return try {
            val originalFile = File(originalPath)
            val backupDir = File(context.getExternalFilesDir(null), "backups")
            backupDir.mkdirs()
            
            val timestamp = System.currentTimeMillis()
            val backupPath = "${backupDir.absolutePath}/${originalFile.nameWithoutExtension}_backup_$timestamp.${originalFile.extension}"
            
            originalFile.copyTo(File(backupPath), overwrite = false)
            Log.d(TAG, "Created backup: $backupPath")
            
            backupPath
        } catch (e: Exception) {
            Log.w(TAG, "Could not create backup for: $originalPath", e)
            null
        }
    }
    
    /**
     * Replace cover art for MP3 files
     */
    private fun replaceMP3CoverArt(audioFilePath: String, spectrogramPath: String): Boolean {
        return try {
            // Load spectrogram image
            val spectrogramBitmap = loadAndResizeImage(spectrogramPath)
            if (spectrogramBitmap == null) {
                Log.e(TAG, "Could not load spectrogram image: $spectrogramPath")
                return false
            }
            
            // Convert bitmap to bytes
            val imageBytes = bitmapToBytes(spectrogramBitmap)
            
            // Use JAudioTagger to replace cover art
            val audioFile = AudioFileIO.read(File(audioFilePath))
            val artwork = ArtworkFactory.createArtworkFromBytes(imageBytes)
            
            // Set the artwork
            audioFile.tagOrCreateAndSetDefault.setField(artwork)
            
            // Commit changes
            audioFile.commit()
            
            Log.d(TAG, "Successfully replaced MP3 cover art for: $audioFilePath")
            Log.d(TAG, "Spectrogram size: ${imageBytes.size} bytes")
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing MP3 cover art", e)
            false
        }
    }
    
    /**
     * Replace cover art for FLAC files
     */
    private fun replaceFLACCoverArt(audioFilePath: String, spectrogramPath: String): Boolean {
        return try {
            // FLAC uses Vorbis comments for metadata
            // Similar implementation to MP3 but with FLAC-specific handling
            val spectrogramBitmap = loadAndResizeImage(spectrogramPath)
            if (spectrogramBitmap == null) {
                Log.e(TAG, "Could not load spectrogram image: $spectrogramPath")
                return false
            }
            
            val imageBytes = bitmapToBytes(spectrogramBitmap)
            
            // Use JAudioTagger for FLAC as well
            val audioFile = AudioFileIO.read(File(audioFilePath))
            val artwork = ArtworkFactory.createArtworkFromBytes(imageBytes)
            
            // Set the artwork
            audioFile.tagOrCreateAndSetDefault.setField(artwork)
            
            // Commit changes
            audioFile.commit()
            
            Log.d(TAG, "Successfully replaced FLAC cover art for: $audioFilePath")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing FLAC cover art", e)
            false
        }
    }
    
    /**
     * Replace cover art for M4A files
     */
    private fun replaceM4ACoverArt(audioFilePath: String, spectrogramPath: String): Boolean {
        return try {
            // M4A uses MPEG-4 metadata
            // Similar implementation to MP3 but with M4A-specific handling
            val spectrogramBitmap = loadAndResizeImage(spectrogramPath)
            if (spectrogramBitmap == null) {
                Log.e(TAG, "Could not load spectrogram image: $spectrogramPath")
                return false
            }
            
            val imageBytes = bitmapToBytes(spectrogramBitmap)
            
            // Use JAudioTagger for M4A as well
            val audioFile = AudioFileIO.read(File(audioFilePath))
            val artwork = ArtworkFactory.createArtworkFromBytes(imageBytes)
            
            // Set the artwork
            audioFile.tagOrCreateAndSetDefault.setField(artwork)
            
            // Commit changes
            audioFile.commit()
            
            Log.d(TAG, "Successfully replaced M4A cover art for: $audioFilePath")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing M4A cover art", e)
            false
        }
    }
    
    /**
     * Generic cover art replacement for other formats
     */
    private fun replaceGenericCoverArt(audioFilePath: String, spectrogramPath: String): Boolean {
        Log.d(TAG, "Generic cover art replacement requested for: $audioFilePath")
        
        // For unsupported formats, we'll just log the request
        // In a full implementation, you might try to use a generic metadata library
        
        return false
    }
    
    /**
     * Load and resize image for cover art
     */
    private fun loadAndResizeImage(imagePath: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            FileInputStream(imagePath).use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            
            // Calculate sample size for memory efficiency
            val sampleSize = calculateSampleSize(
                options.outWidth,
                options.outHeight,
                DEFAULT_COVER_ART_SIZE
            )
            
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            
            FileInputStream(imagePath).use { input ->
                BitmapFactory.decodeStream(input, null, loadOptions)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image: $imagePath", e)
            null
        }
    }
    
    /**
     * Calculate optimal sample size for image loading
     */
    private fun calculateSampleSize(width: Int, height: Int, targetSize: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > targetSize || height / sampleSize > targetSize) {
            sampleSize *= 2
        }
        return sampleSize
    }
    
    /**
     * Convert bitmap to byte array
     */
    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        return try {
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error converting bitmap to bytes", e)
            ByteArray(0)
        }
    }
    
    /**
     * Restore original file from backup
     */
    fun restoreFromBackup(backupPath: String, targetPath: String): Boolean {
        return try {
            val backupFile = File(backupPath)
            val targetFile = File(targetPath)
            
            if (!backupFile.exists()) {
                Log.e(TAG, "Backup file not found: $backupPath")
                return false
            }
            
            // Remove current file if it exists
            if (targetFile.exists()) {
                targetFile.delete()
            }
            
            // Restore from backup
            backupFile.copyTo(targetFile, overwrite = false)
            
            Log.d(TAG, "Restored file from backup: $targetPath")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from backup", e)
            false
        }
    }
    
    /**
     * Get supported audio formats
     */
    fun getSupportedFormats(): Set<String> {
        return SUPPORTED_FORMATS.toSet()
    }
    
    /**
     * Check if a file has embedded cover art
     */
    fun hasEmbeddedCoverArt(filePath: String): Boolean {
        // This is a placeholder implementation
        // In a full implementation, you would check the actual metadata
        
        return try {
            val file = File(filePath)
            if (!file.exists() || !isFormatSupported(filePath)) {
                return false
            }
            
            // TODO: Implement actual cover art detection
            // For now, assume all supported files might have cover art
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for embedded cover art", e)
            false
        }
    }
}

/**
 * Result of cover art replacement operation
 */
data class CoverArtReplacementResult(
    val success: Boolean,
    val errorMessage: String?,
    val backupPath: String?
)