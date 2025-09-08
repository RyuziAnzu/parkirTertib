package com.example.parkirtertib.encryption

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.min

/**
 * Core file encryption and decryption service with chunk-based processing
 */
class FileEncryptionService(private val keyManager: SecureKeyManager) {
    
    companion object {
        private const val TAG = "FileEncryptionService"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val CHUNK_SIZE = 64 * 1024 // 64KB chunks for memory efficiency
        private const val METADATA_HEADER_SIZE = 64 // Reserved for metadata
    }
    
    data class EncryptionResult(
        val success: Boolean,
        val outputPath: String? = null,
        val originalChecksum: String? = null,
        val encryptedChecksum: String? = null,
        val error: String? = null
    )
    
    data class DecryptionResult(
        val success: Boolean,
        val outputPath: String? = null,
        val originalChecksum: String? = null,
        val decryptedChecksum: String? = null,
        val integrityVerified: Boolean = false,
        val error: String? = null
    )
    
    interface ProgressCallback {
        fun onProgress(progress: Int, bytesProcessed: Long, totalBytes: Long)
        fun onComplete()
        fun onError(error: String)
    }
    
    /**
     * Encrypts a file with progress tracking and chunk-based processing
     */
    suspend fun encryptFile(
        inputPath: String,
        outputPath: String,
        progressCallback: ProgressCallback? = null
    ): EncryptionResult = withContext(Dispatchers.IO) {
        
        try {
            val inputFile = File(inputPath)
            if (!inputFile.exists()) {
                val error = "Input file does not exist: $inputPath"
                progressCallback?.onError(error)
                return@withContext EncryptionResult(false, error = error)
            }
            
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            
            val secretKey = keyManager.getOrCreateEncryptionKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val totalBytes = inputFile.length()
            var bytesProcessed = 0L
            
            Log.d(TAG, "Starting encryption of file: $inputPath (${totalBytes} bytes)")
            
            // Calculate original file checksum
            val originalChecksum = calculateFileChecksum(inputPath)
            
            FileInputStream(inputFile).use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    
                    // Write metadata header (IV and original file info)
                    writeEncryptionHeader(outputStream, iv, originalChecksum, totalBytes)
                    
                    val buffer = ByteArray(CHUNK_SIZE)
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        val chunkToProcess = if (bytesRead < CHUNK_SIZE) {
                            buffer.copyOf(bytesRead)
                        } else {
                            buffer
                        }
                        
                        val encryptedChunk = cipher.update(chunkToProcess)
                        if (encryptedChunk != null) {
                            outputStream.write(encryptedChunk)
                        }
                        
                        bytesProcessed += bytesRead
                        val progress = ((bytesProcessed.toDouble() / totalBytes) * 100).toInt()
                        progressCallback?.onProgress(progress, bytesProcessed, totalBytes)
                    }
                    
                    // Finalize encryption
                    val finalChunk = cipher.doFinal()
                    if (finalChunk != null) {
                        outputStream.write(finalChunk)
                    }
                }
            }
            
            val encryptedChecksum = calculateFileChecksum(outputPath)
            progressCallback?.onComplete()
            
            Log.d(TAG, "File encryption completed successfully")
            EncryptionResult(
                success = true,
                outputPath = outputPath,
                originalChecksum = originalChecksum,
                encryptedChecksum = encryptedChecksum
            )
            
        } catch (e: Exception) {
            val error = "Encryption failed: ${e.message}"
            Log.e(TAG, error, e)
            progressCallback?.onError(error)
            EncryptionResult(false, error = error)
        }
    }
    
    /**
     * Decrypts a file with progress tracking and integrity verification
     */
    suspend fun decryptFile(
        inputPath: String,
        outputPath: String,
        progressCallback: ProgressCallback? = null
    ): DecryptionResult = withContext(Dispatchers.IO) {
        
        try {
            val inputFile = File(inputPath)
            if (!inputFile.exists()) {
                val error = "Encrypted file does not exist: $inputPath"
                progressCallback?.onError(error)
                return@withContext DecryptionResult(false, error = error)
            }
            
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            
            val secretKey = keyManager.getOrCreateEncryptionKey()
            
            FileInputStream(inputFile).use { inputStream ->
                
                // Read encryption header
                val headerInfo = readEncryptionHeader(inputStream)
                val iv = headerInfo.first
                val originalChecksum = headerInfo.second
                val originalSize = headerInfo.third
                
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
                
                var bytesProcessed = 0L
                
                Log.d(TAG, "Starting decryption of file: $inputPath")
                
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(CHUNK_SIZE + GCM_TAG_LENGTH) // Account for GCM tag
                    var bytesRead: Int
                    val remainingBytes = inputFile.length() - METADATA_HEADER_SIZE
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        val isLastChunk = bytesProcessed + bytesRead >= remainingBytes
                        
                        val decryptedChunk = if (isLastChunk) {
                            cipher.doFinal(buffer, 0, bytesRead)
                        } else {
                            cipher.update(buffer, 0, bytesRead)
                        }
                        
                        if (decryptedChunk != null) {
                            outputStream.write(decryptedChunk)
                        }
                        
                        bytesProcessed += bytesRead
                        val progress = ((bytesProcessed.toDouble() / remainingBytes) * 100).toInt()
                        progressCallback?.onProgress(progress, bytesProcessed, remainingBytes)
                        
                        if (isLastChunk) break
                    }
                }
            }
            
            // Verify integrity
            val decryptedChecksum = calculateFileChecksum(outputPath)
            val integrityVerified = originalChecksum == decryptedChecksum
            
            progressCallback?.onComplete()
            
            if (integrityVerified) {
                Log.d(TAG, "File decryption completed successfully with verified integrity")
            } else {
                Log.w(TAG, "File decryption completed but integrity verification failed")
            }
            
            DecryptionResult(
                success = true,
                outputPath = outputPath,
                originalChecksum = originalChecksum,
                decryptedChecksum = decryptedChecksum,
                integrityVerified = integrityVerified
            )
            
        } catch (e: Exception) {
            val error = "Decryption failed: ${e.message}"
            Log.e(TAG, error, e)
            progressCallback?.onError(error)
            DecryptionResult(false, error = error)
        }
    }
    
    /**
     * Writes encryption metadata header to the output stream
     */
    private fun writeEncryptionHeader(
        outputStream: OutputStream,
        iv: ByteArray,
        originalChecksum: String,
        originalSize: Long
    ) {
        val header = ByteArray(METADATA_HEADER_SIZE)
        
        // Write IV (12 bytes)
        System.arraycopy(iv, 0, header, 0, GCM_IV_LENGTH)
        
        // Write original size (8 bytes)
        val sizeBytes = originalSize.toString().toByteArray()
        val sizeLength = min(sizeBytes.size, 8)
        System.arraycopy(sizeBytes, 0, header, GCM_IV_LENGTH, sizeLength)
        
        // Write checksum (remaining bytes)
        val checksumBytes = originalChecksum.toByteArray()
        val checksumLength = min(checksumBytes.size, METADATA_HEADER_SIZE - GCM_IV_LENGTH - 8)
        System.arraycopy(checksumBytes, 0, header, GCM_IV_LENGTH + 8, checksumLength)
        
        outputStream.write(header)
    }
    
    /**
     * Reads encryption metadata header from the input stream
     */
    private fun readEncryptionHeader(inputStream: InputStream): Triple<ByteArray, String, Long> {
        val header = ByteArray(METADATA_HEADER_SIZE)
        inputStream.read(header)
        
        // Extract IV
        val iv = ByteArray(GCM_IV_LENGTH)
        System.arraycopy(header, 0, iv, 0, GCM_IV_LENGTH)
        
        // Extract original size
        val sizeBytes = ByteArray(8)
        System.arraycopy(header, GCM_IV_LENGTH, sizeBytes, 0, 8)
        val originalSize = String(sizeBytes).trim { it <= ' ' || it == '\u0000' }.toLongOrNull() ?: 0L
        
        // Extract checksum
        val checksumBytes = ByteArray(METADATA_HEADER_SIZE - GCM_IV_LENGTH - 8)
        System.arraycopy(header, GCM_IV_LENGTH + 8, checksumBytes, 0, checksumBytes.size)
        val originalChecksum = String(checksumBytes).trim { it <= ' ' || it == '\u0000' }
        
        return Triple(iv, originalChecksum, originalSize)
    }
    
    /**
     * Calculates SHA-256 checksum of a file
     */
    private fun calculateFileChecksum(filePath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        
        FileInputStream(File(filePath)).use { inputStream ->
            val buffer = ByteArray(CHUNK_SIZE)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}