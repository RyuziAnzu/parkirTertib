package com.example.parkirtertib.encryption

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker for file encryption/decryption operations
 */
class EncryptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "EncryptionWorker"
        
        // Input parameters
        const val KEY_OPERATION_TYPE = "operation_type"
        const val KEY_INPUT_PATH = "input_path"
        const val KEY_OUTPUT_PATH = "output_path"
        
        // Operation types
        const val OPERATION_ENCRYPT = "encrypt"
        const val OPERATION_DECRYPT = "decrypt"
        
        // Progress and result keys
        const val KEY_PROGRESS = "progress"
        const val KEY_BYTES_PROCESSED = "bytes_processed"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_SUCCESS = "success"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_OUTPUT_FILE_PATH = "output_file_path"
        const val KEY_ORIGINAL_CHECKSUM = "original_checksum"
        const val KEY_PROCESSED_CHECKSUM = "processed_checksum"
        const val KEY_INTEGRITY_VERIFIED = "integrity_verified"
        
        /**
         * Creates a work request for file encryption
         */
        fun createEncryptionWork(
            inputPath: String,
            outputPath: String,
            tag: String = "file_encryption"
        ): OneTimeWorkRequest {
            val inputData = Data.Builder()
                .putString(KEY_OPERATION_TYPE, OPERATION_ENCRYPT)
                .putString(KEY_INPUT_PATH, inputPath)
                .putString(KEY_OUTPUT_PATH, outputPath)
                .build()
            
            return OneTimeWorkRequestBuilder<EncryptionWorker>()
                .setInputData(inputData)
                .addTag(tag)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresCharging(false)
                        .setRequiresDeviceIdle(false)
                        .build()
                )
                .build()
        }
        
        /**
         * Creates a work request for file decryption
         */
        fun createDecryptionWork(
            inputPath: String,
            outputPath: String,
            tag: String = "file_decryption"
        ): OneTimeWorkRequest {
            val inputData = Data.Builder()
                .putString(KEY_OPERATION_TYPE, OPERATION_DECRYPT)
                .putString(KEY_INPUT_PATH, inputPath)
                .putString(KEY_OUTPUT_PATH, outputPath)
                .build()
            
            return OneTimeWorkRequestBuilder<EncryptionWorker>()
                .setInputData(inputData)
                .addTag(tag)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresCharging(false)
                        .setRequiresDeviceIdle(false)
                        .build()
                )
                .build()
        }
    }
    
    private val encryptionService = FileEncryptionService(SecureKeyManager())
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val operationType = inputData.getString(KEY_OPERATION_TYPE)
            val inputPath = inputData.getString(KEY_INPUT_PATH)
            val outputPath = inputData.getString(KEY_OUTPUT_PATH)
            
            if (operationType == null || inputPath == null || outputPath == null) {
                Log.e(TAG, "Missing required input parameters")
                return@withContext Result.failure(
                    Data.Builder()
                        .putBoolean(KEY_SUCCESS, false)
                        .putString(KEY_ERROR_MESSAGE, "Missing required input parameters")
                        .build()
                )
            }
            
            Log.d(TAG, "Starting $operationType operation: $inputPath -> $outputPath")
            
            val progressCallback = object : FileEncryptionService.ProgressCallback {
                override fun onProgress(progress: Int, bytesProcessed: Long, totalBytes: Long) {
                    val progressData = Data.Builder()
                        .putInt(KEY_PROGRESS, progress)
                        .putLong(KEY_BYTES_PROCESSED, bytesProcessed)
                        .putLong(KEY_TOTAL_BYTES, totalBytes)
                        .build()
                    
                    setProgressAsync(progressData)
                }
                
                override fun onComplete() {
                    Log.d(TAG, "$operationType operation completed")
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "$operationType operation failed: $error")
                }
            }
            
            when (operationType) {
                OPERATION_ENCRYPT -> {
                    val result = encryptionService.encryptFile(inputPath, outputPath, progressCallback)
                    
                    if (result.success) {
                        Result.success(
                            Data.Builder()
                                .putBoolean(KEY_SUCCESS, true)
                                .putString(KEY_OUTPUT_FILE_PATH, result.outputPath)
                                .putString(KEY_ORIGINAL_CHECKSUM, result.originalChecksum)
                                .putString(KEY_PROCESSED_CHECKSUM, result.encryptedChecksum)
                                .build()
                        )
                    } else {
                        Result.failure(
                            Data.Builder()
                                .putBoolean(KEY_SUCCESS, false)
                                .putString(KEY_ERROR_MESSAGE, result.error)
                                .build()
                        )
                    }
                }
                
                OPERATION_DECRYPT -> {
                    val result = encryptionService.decryptFile(inputPath, outputPath, progressCallback)
                    
                    if (result.success) {
                        Result.success(
                            Data.Builder()
                                .putBoolean(KEY_SUCCESS, true)
                                .putString(KEY_OUTPUT_FILE_PATH, result.outputPath)
                                .putString(KEY_ORIGINAL_CHECKSUM, result.originalChecksum)
                                .putString(KEY_PROCESSED_CHECKSUM, result.decryptedChecksum)
                                .putBoolean(KEY_INTEGRITY_VERIFIED, result.integrityVerified)
                                .build()
                        )
                    } else {
                        Result.failure(
                            Data.Builder()
                                .putBoolean(KEY_SUCCESS, false)
                                .putString(KEY_ERROR_MESSAGE, result.error)
                                .build()
                        )
                    }
                }
                
                else -> {
                    Log.e(TAG, "Unknown operation type: $operationType")
                    Result.failure(
                        Data.Builder()
                            .putBoolean(KEY_SUCCESS, false)
                            .putString(KEY_ERROR_MESSAGE, "Unknown operation type: $operationType")
                            .build()
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Worker execution failed", e)
            Result.failure(
                Data.Builder()
                    .putBoolean(KEY_SUCCESS, false)
                    .putString(KEY_ERROR_MESSAGE, e.message ?: "Unknown error")
                    .build()
            )
        }
    }
}