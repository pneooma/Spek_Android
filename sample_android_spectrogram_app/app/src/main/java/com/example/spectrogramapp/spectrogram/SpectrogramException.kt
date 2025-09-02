package com.example.spectrogramapp.spectrogram

/**
 * Custom exception class for spectrogram-related errors
 */
sealed class SpectrogramException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    
    /**
     * Audio file related errors
     */
    sealed class AudioFileException(message: String, cause: Throwable? = null) : SpectrogramException(message, cause) {
        class FileNotFound(path: String) : AudioFileException("Audio file not found: $path")
        class FileCorrupted(path: String, cause: Throwable? = null) : AudioFileException("Audio file is corrupted: $path", cause)
        class UnsupportedFormat(format: String) : AudioFileException("Unsupported audio format: $format")
        class FileTooLarge(size: Long, maxSize: Long) : AudioFileException("File too large: ${size}MB (max: ${maxSize}MB)")
    }
    
    /**
     * FFmpeg related errors
     */
    sealed class FFmpegException(message: String, cause: Throwable? = null) : SpectrogramException(message, cause) {
        class ExecutionFailed(command: String, exitCode: Int, errorOutput: String) : 
            FFmpegException("FFmpeg execution failed (exit code: $exitCode): $command\nError: $errorOutput")
        class InvalidCommand(command: String) : FFmpegException("Invalid FFmpeg command: $command")
        class Timeout(command: String, timeoutMs: Long) : FFmpegException("FFmpeg execution timed out after ${timeoutMs}ms: $command")
    }
    
    /**
     * Audio processing errors
     */
    sealed class AudioProcessingException(message: String, cause: Throwable? = null) : SpectrogramException(message, cause) {
        class InvalidSampleRate(sampleRate: Int) : AudioProcessingException("Invalid sample rate: $sampleRate Hz")
        class InvalidBufferSize(size: Int, minSize: Int, maxSize: Int) : 
            AudioProcessingException("Invalid buffer size: $size (must be between $minSize and $maxSize)")
        class RecordingFailed(reason: String, cause: Throwable? = null) : 
            AudioProcessingException("Audio recording failed: $reason", cause)
        class FFTFailed(size: Int, cause: Throwable? = null) : 
            AudioProcessingException("FFT computation failed for size: $size", cause)
    }
    
    /**
     * Memory and resource errors
     */
    sealed class ResourceException(message: String, cause: Throwable? = null) : SpectrogramException(message, cause) {
        class OutOfMemory(requested: Long, available: Long) : 
            ResourceException("Out of memory: requested ${requested}MB, available ${available}MB")
        class StorageFull(path: String, required: Long, available: Long) : 
            ResourceException("Storage full at $path: required ${required}MB, available ${available}MB")
        class FileSystemError(path: String, cause: Throwable? = null) : 
            ResourceException("File system error at $path", cause)
    }
    
    /**
     * Permission and security errors
     */
    sealed class PermissionException(message: String, cause: Throwable? = null) : SpectrogramException(message, cause) {
        class MicrophoneAccessDenied : PermissionException("Microphone access denied")
        class StorageAccessDenied : PermissionException("Storage access denied")
        class FileReadPermission(path: String) : PermissionException("No read permission for file: $path")
        class FileWritePermission(path: String) : PermissionException("No write permission for file: $path")
    }
    
    /**
     * Network and external service errors
     */
    sealed class NetworkException(message: String, cause: Throwable? = null) : SpectrogramException(message, cause) {
        class ConnectionFailed(url: String, cause: Throwable? = null) : 
            NetworkException("Connection failed to: $url", cause)
        class Timeout(url: String, timeoutMs: Long) : NetworkException("Request timed out to: $url after ${timeoutMs}ms")
        class ServiceUnavailable(service: String) : NetworkException("Service unavailable: $service")
    }
    
    /**
     * Get user-friendly error message
     */
    fun getUserFriendlyMessage(): String {
        return when (this) {
            is AudioFileException.FileNotFound -> "The audio file could not be found. Please check the file path and try again."
            is AudioFileException.FileCorrupted -> "The audio file appears to be corrupted. Please try a different file."
            is AudioFileException.UnsupportedFormat -> "This audio format is not supported. Please convert to MP3, WAV, or FLAC."
            is AudioFileException.FileTooLarge -> "The file is too large to process. Please use a smaller audio file."
            is FFmpegException.ExecutionFailed -> "Failed to process the audio file. Please try again or check the file format."
            is FFmpegException.InvalidCommand -> "Internal processing error. Please restart the app and try again."
            is FFmpegException.Timeout -> "Processing took too long. Please try a shorter audio file."
            is AudioProcessingException.InvalidSampleRate -> "Unsupported audio sample rate. Please use 44.1kHz or 48kHz."
            is AudioProcessingException.InvalidBufferSize -> "Audio processing error. Please restart the app."
            is AudioProcessingException.RecordingFailed -> "Failed to start audio recording. Please check microphone permissions."
            is AudioProcessingException.FFTFailed -> "Audio analysis failed. Please try again."
            is ResourceException.OutOfMemory -> "Not enough memory to process this file. Please close other apps and try again."
            is ResourceException.StorageFull -> "Not enough storage space. Please free up some space and try again."
            is ResourceException.FileSystemError -> "File system error. Please check your device storage."
            is PermissionException.MicrophoneAccessDenied -> "Microphone access is required. Please grant permission in settings."
            is PermissionException.StorageAccessDenied -> "Storage access is required. Please grant permission in settings."
            is PermissionException.FileReadPermission -> "Cannot read the selected file. Please check permissions."
            is PermissionException.FileWritePermission -> "Cannot save the spectrogram. Please check storage permissions."
            is NetworkException.ConnectionFailed -> "Network connection failed. Please check your internet connection."
            is NetworkException.Timeout -> "Network request timed out. Please try again."
            is NetworkException.ServiceUnavailable -> "Service temporarily unavailable. Please try again later."
            else -> "An unexpected error occurred. Please try again."
        }
    }
    
    /**
     * Get error severity level
     */
    fun getSeverity(): ErrorSeverity {
        return when (this) {
            is AudioFileException.FileNotFound,
            is AudioFileException.FileCorrupted,
            is AudioFileException.UnsupportedFormat -> ErrorSeverity.USER_ERROR
            
            is FFmpegException.ExecutionFailed,
            is FFmpegException.InvalidCommand -> ErrorSeverity.SYSTEM_ERROR
            
            is AudioProcessingException.InvalidSampleRate,
            is AudioProcessingException.InvalidBufferSize -> ErrorSeverity.CONFIGURATION_ERROR
            
            is ResourceException.OutOfMemory,
            is ResourceException.StorageFull -> ErrorSeverity.CRITICAL_ERROR
            
            is PermissionException.MicrophoneAccessDenied,
            is PermissionException.StorageAccessDenied -> ErrorSeverity.PERMISSION_ERROR
            
            is NetworkException.ConnectionFailed,
            is NetworkException.Timeout -> ErrorSeverity.NETWORK_ERROR
            
            else -> ErrorSeverity.UNKNOWN_ERROR
        }
    }
}

/**
 * Error severity levels
 */
enum class ErrorSeverity {
    USER_ERROR,           // User can fix by changing input
    CONFIGURATION_ERROR,  // App configuration issue
    PERMISSION_ERROR,     // Permission related
    NETWORK_ERROR,        // Network connectivity issue
    SYSTEM_ERROR,         // System/library error
    CRITICAL_ERROR,       // Critical resource issue
    UNKNOWN_ERROR         // Unknown error type
}