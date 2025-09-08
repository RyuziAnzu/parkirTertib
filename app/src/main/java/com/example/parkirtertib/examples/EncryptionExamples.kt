package com.example.parkirtertib.examples

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkInfo
import com.example.parkirtertib.encryption.EncryptionManager
import com.example.parkirtertib.encryption.FileEncryptionService
import com.example.parkirtertib.encryption.SecureKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

/**
 * Example usage of the file encryption system
 */
class EncryptionExamples(private val context: Context) {
    
    private val encryptionManager = EncryptionManager(context)
    
    /**
     * Example 1: Simple file encryption with progress monitoring
     */
    fun encryptFileWithProgress(
        inputFilePath: String,
        lifecycleOwner: LifecycleOwner,
        onComplete: (Boolean, String?) -> Unit
    ) {
        // Start encryption in background
        val operationId = encryptionManager.encryptFileAsync(
            inputPath = inputFilePath,
            outputPath = "$inputFilePath.enc"
        )
        
        // Monitor progress
        encryptionManager.getOperationProgress(operationId).observe(lifecycleOwner) { progress ->
            progress?.let {
                when (it.state) {
                    WorkInfo.State.RUNNING -> {
                        println("Encryption progress: ${it.progress}% (${it.bytesProcessed}/${it.totalBytes} bytes)")
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        println("Encryption completed successfully!")
                        onComplete(true, it.outputPath)
                    }
                    WorkInfo.State.FAILED -> {
                        println("Encryption failed: ${it.error}")
                        onComplete(false, it.error)
                    }
                    else -> {
                        println("Encryption state: ${it.state}")
                    }
                }
            }
        }
    }
    
    /**
     * Example 2: Direct encryption/decryption with coroutines
     */
    fun directEncryptionExample() {
        val keyManager = SecureKeyManager()
        val encryptionService = FileEncryptionService(keyManager)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create a test file
                val testFile = File(context.filesDir, "test_document.txt")
                testFile.writeText("This is a confidential document that needs encryption.")
                
                // Encrypt the file
                val encryptResult = encryptionService.encryptFile(
                    inputPath = testFile.absolutePath,
                    outputPath = "${testFile.absolutePath}.enc",
                    progressCallback = object : FileEncryptionService.ProgressCallback {
                        override fun onProgress(progress: Int, bytesProcessed: Long, totalBytes: Long) {
                            println("Progress: $progress% ($bytesProcessed/$totalBytes bytes)")
                        }
                        
                        override fun onComplete() {
                            println("Encryption completed!")
                        }
                        
                        override fun onError(error: String) {
                            println("Encryption error: $error")
                        }
                    }
                )
                
                if (encryptResult.success) {
                    println("File encrypted successfully!")
                    println("Original checksum: ${encryptResult.originalChecksum}")
                    println("Encrypted file: ${encryptResult.outputPath}")
                    
                    // Decrypt the file
                    val decryptResult = encryptionService.decryptFile(
                        inputPath = encryptResult.outputPath!!,
                        outputPath = "${testFile.absolutePath}.decrypted"
                    )
                    
                    if (decryptResult.success && decryptResult.integrityVerified) {
                        println("File decrypted successfully with verified integrity!")
                        
                        // Verify content matches
                        val originalContent = testFile.readText()
                        val decryptedContent = File(decryptResult.outputPath!!).readText()
                        
                        if (originalContent == decryptedContent) {
                            println("Content verification successful!")
                        } else {
                            println("Content verification failed!")
                        }
                    }
                }
                
            } catch (e: Exception) {
                println("Error during encryption/decryption: ${e.message}")
            }
        }
    }
    
    /**
     * Example 3: Key management operations
     */
    fun keyManagementExample() {
        val keyManager = SecureKeyManager()
        
        // Check if key exists
        if (!keyManager.hasEncryptionKey()) {
            println("No encryption key found. Generating new key...")
            keyManager.getOrCreateEncryptionKey()
            println("New encryption key generated.")
        } else {
            println("Encryption key already exists.")
        }
        
        // Get existing key
        val encryptionKey = keyManager.getOrCreateEncryptionKey()
        println("Encryption key algorithm: ${encryptionKey.algorithm}")
        
        // Example: Regenerate key (be careful - this makes old encrypted files unreadable)
        // keyManager.deleteEncryptionKey()
        // keyManager.getOrCreateEncryptionKey()
        // println("New key generated - old encrypted files are now unreadable!")
    }
    
    /**
     * Example 4: Batch file encryption
     */
    fun batchEncryptionExample(
        filePaths: List<String>,
        lifecycleOwner: LifecycleOwner,
        onBatchComplete: (List<Pair<String, Boolean>>) -> Unit
    ) {
        val results = mutableListOf<Pair<String, Boolean>>()
        var completedCount = 0
        
        filePaths.forEach { filePath ->
            val operationId = encryptionManager.encryptFileAsync(
                inputPath = filePath,
                outputPath = "$filePath.enc"
            )
            
            encryptionManager.getOperationProgress(operationId).observe(lifecycleOwner) { progress ->
                progress?.let {
                    if (it.state.isFinished) {
                        results.add(filePath to (it.state == WorkInfo.State.SUCCEEDED))
                        completedCount++
                        
                        if (completedCount == filePaths.size) {
                            onBatchComplete(results)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Example 5: Cancel long-running operation
     */
    fun cancellationExample(inputFilePath: String): UUID {
        // Start a long encryption operation
        val operationId = encryptionManager.encryptFileAsync(
            inputPath = inputFilePath,
            outputPath = "$inputFilePath.enc"
        )
        
        // Cancel after 10 seconds (example)
        CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(10000)
            encryptionManager.cancelOperation(operationId)
            println("Operation cancelled after 10 seconds")
        }
        
        return operationId
    }
    
    /**
     * Example 6: Monitor all active operations
     */
    fun monitorAllOperations(lifecycleOwner: LifecycleOwner) {
        encryptionManager.getAllActiveOperations().observe(lifecycleOwner) { activeOps ->
            println("Active operations: ${activeOps.size}")
            activeOps.forEach { op ->
                println("${op.operationType} - ${op.progress}% (${op.state})")
            }
        }
    }
    
    /**
     * Example 7: View operation history
     */
    fun viewOperationHistory(lifecycleOwner: LifecycleOwner) {
        encryptionManager.getOperationHistory().observe(lifecycleOwner) { history ->
            println("Operation History (${history.size} items):")
            history.forEach { op ->
                val status = when (op.state) {
                    WorkInfo.State.SUCCEEDED -> "✓ Success"
                    WorkInfo.State.FAILED -> "✗ Failed: ${op.error}"
                    else -> op.state.toString()
                }
                
                println("${op.operationType} - $status")
                if (op.outputPath != null) {
                    println("  Output: ${op.outputPath}")
                }
                if (op.integrityVerified != null) {
                    println("  Integrity: ${if (op.integrityVerified) "Verified" else "Failed"}")
                }
            }
        }
    }
}