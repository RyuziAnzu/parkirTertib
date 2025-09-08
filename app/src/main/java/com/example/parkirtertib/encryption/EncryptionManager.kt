package com.example.parkirtertib.encryption

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.work.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

/**
 * High-level manager for file encryption and decryption operations
 */
class EncryptionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "EncryptionManager"
        private const val WORK_TAG_PREFIX = "encryption_"
    }
    
    private val workManager = WorkManager.getInstance(context)
    private val keyManager = SecureKeyManager()
    
    data class OperationProgress(
        val workId: UUID,
        val operationType: String,
        val state: WorkInfo.State,
        val progress: Int = 0,
        val bytesProcessed: Long = 0,
        val totalBytes: Long = 0,
        val outputPath: String? = null,
        val error: String? = null,
        val integrityVerified: Boolean? = null
    )
    
    /**
     * Starts file encryption in the background
     */
    fun encryptFileAsync(
        inputPath: String,
        outputPath: String
    ): UUID {
        val workRequest = EncryptionWorker.createEncryptionWork(
            inputPath = inputPath,
            outputPath = outputPath,
            tag = "${WORK_TAG_PREFIX}encrypt_${System.currentTimeMillis()}"
        )
        
        workManager.enqueue(workRequest)
        Log.d(TAG, "Encryption work enqueued: ${workRequest.id}")
        
        return workRequest.id
    }
    
    /**
     * Starts file decryption in the background
     */
    fun decryptFileAsync(
        inputPath: String,
        outputPath: String
    ): UUID {
        val workRequest = EncryptionWorker.createDecryptionWork(
            inputPath = inputPath,
            outputPath = outputPath,
            tag = "${WORK_TAG_PREFIX}decrypt_${System.currentTimeMillis()}"
        )
        
        workManager.enqueue(workRequest)
        Log.d(TAG, "Decryption work enqueued: ${workRequest.id}")
        
        return workRequest.id
    }
    
    /**
     * Gets live operation progress for a specific work ID
     */
    fun getOperationProgress(workId: UUID): LiveData<OperationProgress?> {
        return workManager.getWorkInfoByIdLiveData(workId).map { workInfo ->
            workInfo?.let { info ->
                val operationType = info.tags.find { it.startsWith(WORK_TAG_PREFIX) }
                    ?.removePrefix(WORK_TAG_PREFIX)
                    ?.substringBefore("_") ?: "unknown"
                
                val progress = info.progress.getInt(EncryptionWorker.KEY_PROGRESS, 0)
                val bytesProcessed = info.progress.getLong(EncryptionWorker.KEY_BYTES_PROCESSED, 0)
                val totalBytes = info.progress.getLong(EncryptionWorker.KEY_TOTAL_BYTES, 0)
                
                val outputPath = when (info.state) {
                    WorkInfo.State.SUCCEEDED -> info.outputData.getString(EncryptionWorker.KEY_OUTPUT_FILE_PATH)
                    else -> null
                }
                
                val error = when (info.state) {
                    WorkInfo.State.FAILED -> info.outputData.getString(EncryptionWorker.KEY_ERROR_MESSAGE)
                    else -> null
                }
                
                val integrityVerified = when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        if (operationType == "decrypt") {
                            info.outputData.getBoolean(EncryptionWorker.KEY_INTEGRITY_VERIFIED, false)
                        } else null
                    }
                    else -> null
                }
                
                OperationProgress(
                    workId = workId,
                    operationType = operationType,
                    state = info.state,
                    progress = progress,
                    bytesProcessed = bytesProcessed,
                    totalBytes = totalBytes,
                    outputPath = outputPath,
                    error = error,
                    integrityVerified = integrityVerified
                )
            }
        }
    }
    
    /**
     * Gets all active encryption operations
     */
    fun getAllActiveOperations(): LiveData<List<OperationProgress>> {
        return workManager.getWorkInfosByTagLiveData(WORK_TAG_PREFIX.dropLast(1)).map { workInfos ->
            workInfos.mapNotNull { workInfo ->
                if (workInfo.state.isFinished) return@mapNotNull null
                
                val operationType = workInfo.tags.find { it.startsWith(WORK_TAG_PREFIX) }
                    ?.removePrefix(WORK_TAG_PREFIX)
                    ?.substringBefore("_") ?: "unknown"
                
                val progress = workInfo.progress.getInt(EncryptionWorker.KEY_PROGRESS, 0)
                val bytesProcessed = workInfo.progress.getLong(EncryptionWorker.KEY_BYTES_PROCESSED, 0)
                val totalBytes = workInfo.progress.getLong(EncryptionWorker.KEY_TOTAL_BYTES, 0)
                
                OperationProgress(
                    workId = workInfo.id,
                    operationType = operationType,
                    state = workInfo.state,
                    progress = progress,
                    bytesProcessed = bytesProcessed,
                    totalBytes = totalBytes
                )
            }
        }
    }
    
    /**
     * Cancels a specific operation
     */
    fun cancelOperation(workId: UUID) {
        workManager.cancelWorkById(workId)
        Log.d(TAG, "Cancelled operation: $workId")
    }
    
    /**
     * Cancels all encryption operations
     */
    fun cancelAllOperations() {
        workManager.cancelAllWorkByTag(WORK_TAG_PREFIX.dropLast(1))
        Log.d(TAG, "Cancelled all encryption operations")
    }
    
    /**
     * Checks if encryption key exists
     */
    fun hasEncryptionKey(): Boolean {
        return keyManager.hasEncryptionKey()
    }
    
    /**
     * Generates a new encryption key (deletes existing one)
     */
    fun generateNewEncryptionKey(): Boolean {
        return try {
            keyManager.deleteEncryptionKey()
            keyManager.getOrCreateEncryptionKey()
            Log.d(TAG, "New encryption key generated")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate new encryption key", e)
            false
        }
    }
    
    /**
     * Deletes the encryption key (use with caution)
     */
    fun deleteEncryptionKey(): Boolean {
        return keyManager.deleteEncryptionKey()
    }
    
    /**
     * Gets encryption operation history (completed or failed)
     */
    fun getOperationHistory(): LiveData<List<OperationProgress>> {
        return workManager.getWorkInfosByTagLiveData(WORK_TAG_PREFIX.dropLast(1)).map { workInfos ->
            workInfos.mapNotNull { workInfo ->
                if (!workInfo.state.isFinished) return@mapNotNull null
                
                val operationType = workInfo.tags.find { it.startsWith(WORK_TAG_PREFIX) }
                    ?.removePrefix(WORK_TAG_PREFIX)
                    ?.substringBefore("_") ?: "unknown"
                
                val outputPath = when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> workInfo.outputData.getString(EncryptionWorker.KEY_OUTPUT_FILE_PATH)
                    else -> null
                }
                
                val error = when (workInfo.state) {
                    WorkInfo.State.FAILED -> workInfo.outputData.getString(EncryptionWorker.KEY_ERROR_MESSAGE)
                    else -> null
                }
                
                val integrityVerified = when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        if (operationType == "decrypt") {
                            workInfo.outputData.getBoolean(EncryptionWorker.KEY_INTEGRITY_VERIFIED, false)
                        } else null
                    }
                    else -> null
                }
                
                OperationProgress(
                    workId = workInfo.id,
                    operationType = operationType,
                    state = workInfo.state,
                    progress = if (workInfo.state == WorkInfo.State.SUCCEEDED) 100 else 0,
                    outputPath = outputPath,
                    error = error,
                    integrityVerified = integrityVerified
                )
            }.sortedByDescending { it.workId.timestamp() }
        }
    }
    
    /**
     * Helper extension to get timestamp from UUID (for sorting)
     */
    private fun UUID.timestamp(): Long {
        return this.toString().hashCode().toLong()
    }
}