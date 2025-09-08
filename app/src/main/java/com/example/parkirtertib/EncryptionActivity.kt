package com.example.parkirtertib

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.WorkInfo
import com.example.parkirtertib.databinding.ActivityEncryptionBinding
import com.example.parkirtertib.encryption.EncryptionManager
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*

class EncryptionActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "EncryptionActivity"
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1001
    }
    
    private lateinit var binding: ActivityEncryptionBinding
    private lateinit var encryptionManager: EncryptionManager
    private var selectedFileUri: Uri? = null
    private var selectedFilePath: String? = null
    private var currentOperationId: UUID? = null
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleSelectedFile(uri)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEncryptionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        encryptionManager = EncryptionManager(this)
        
        setupUI()
        updateKeyStatus()
        checkPermissions()
    }
    
    private fun setupUI() {
        binding.btnSelectFile.setOnClickListener {
            openFilePicker()
        }
        
        binding.btnEncrypt.setOnClickListener {
            encryptSelectedFile()
        }
        
        binding.btnDecrypt.setOnClickListener {
            decryptSelectedFile()
        }
        
        binding.btnGenerateKey.setOnClickListener {
            generateNewKey()
        }
        
        binding.btnDeleteKey.setOnClickListener {
            deleteKey()
        }
        
        binding.btnCancelOperation.setOnClickListener {
            cancelCurrentOperation()
        }
        
        // Setup history RecyclerView
        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(this)
        
        // Observe operation history
        encryptionManager.getOperationHistory().observe(this) { history ->
            // You can create an adapter here to display operation history
            Log.d(TAG, "Operation history updated: ${history.size} items")
        }
    }
    
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && 
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this, 
                        "Storage permissions are required for file encryption", 
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(intent)
    }
    
    private fun handleSelectedFile(uri: Uri) {
        selectedFileUri = uri
        
        // Get file name and copy to internal storage
        val fileName = getFileName(uri)
        binding.tvSelectedFile.text = fileName
        
        try {
            // Copy file to internal storage for processing
            val internalFile = File(filesDir, "temp_${System.currentTimeMillis()}_$fileName")
            copyUriToFile(uri, internalFile)
            
            selectedFilePath = internalFile.absolutePath
            
            // Enable operation buttons
            binding.btnEncrypt.isEnabled = true
            binding.btnDecrypt.isEnabled = fileName.endsWith(".enc", ignoreCase = true)
            
            Log.d(TAG, "File selected: $fileName (${internalFile.length()} bytes)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle selected file", e)
            Toast.makeText(this, "Failed to process selected file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        result = it.getString(displayNameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "unknown_file"
    }
    
    private fun copyUriToFile(uri: Uri, destinationFile: File) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
    
    private fun encryptSelectedFile() {
        val inputPath = selectedFilePath ?: return
        val outputPath = "$inputPath.enc"
        
        showProgressCard(true)
        binding.tvProgressStatus.text = "Starting encryption..."
        
        val operationId = encryptionManager.encryptFileAsync(inputPath, outputPath)
        currentOperationId = operationId
        
        observeOperation(operationId, "Encryption")
    }
    
    private fun decryptSelectedFile() {
        val inputPath = selectedFilePath ?: return
        val outputPath = inputPath.removeSuffix(".enc")
        
        showProgressCard(true)
        binding.tvProgressStatus.text = "Starting decryption..."
        
        val operationId = encryptionManager.decryptFileAsync(inputPath, outputPath)
        currentOperationId = operationId
        
        observeOperation(operationId, "Decryption")
    }
    
    private fun observeOperation(operationId: UUID, operationType: String) {
        encryptionManager.getOperationProgress(operationId).observe(this) { progress ->
            progress?.let {
                updateProgressUI(it, operationType)
            }
        }
    }
    
    private fun updateProgressUI(progress: EncryptionManager.OperationProgress, operationType: String) {
        when (progress.state) {
            WorkInfo.State.RUNNING -> {
                binding.progressBar.progress = progress.progress
                binding.tvProgressStatus.text = "$operationType in progress..."
                binding.tvProgressDetails.text = "${progress.progress}% - " +
                    "${formatBytes(progress.bytesProcessed)} / ${formatBytes(progress.totalBytes)}"
            }
            
            WorkInfo.State.SUCCEEDED -> {
                binding.progressBar.progress = 100
                binding.tvProgressStatus.text = "$operationType completed successfully!"
                binding.tvProgressDetails.text = "100% - Operation completed"
                
                progress.integrityVerified?.let { verified ->
                    if (!verified) {
                        binding.tvProgressStatus.text = "$operationType completed with integrity warning!"
                    }
                }
                
                showProgressCard(false)
                currentOperationId = null
                
                Toast.makeText(this, "$operationType completed!", Toast.LENGTH_SHORT).show()
            }
            
            WorkInfo.State.FAILED -> {
                binding.tvProgressStatus.text = "$operationType failed"
                binding.tvProgressDetails.text = progress.error ?: "Unknown error"
                
                showProgressCard(false)
                currentOperationId = null
                
                Toast.makeText(this, "$operationType failed: ${progress.error}", Toast.LENGTH_LONG).show()
            }
            
            WorkInfo.State.CANCELLED -> {
                binding.tvProgressStatus.text = "$operationType cancelled"
                showProgressCard(false)
                currentOperationId = null
                
                Toast.makeText(this, "$operationType cancelled", Toast.LENGTH_SHORT).show()
            }
            
            else -> {
                // ENQUEUED, BLOCKED states
                binding.tvProgressStatus.text = "$operationType queued..."
            }
        }
    }
    
    private fun showProgressCard(show: Boolean) {
        binding.cardProgress.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) {
            binding.progressBar.progress = 0
        }
    }
    
    private fun cancelCurrentOperation() {
        currentOperationId?.let { operationId ->
            encryptionManager.cancelOperation(operationId)
            currentOperationId = null
        }
    }
    
    private fun updateKeyStatus() {
        val hasKey = encryptionManager.hasEncryptionKey()
        binding.tvKeyStatus.text = if (hasKey) {
            "Encryption key is available"
        } else {
            "No encryption key found. Generate one to start encrypting files."
        }
        
        binding.btnDeleteKey.isEnabled = hasKey
    }
    
    private fun generateNewKey() {
        AlertDialog.Builder(this)
            .setTitle("Generate New Key")
            .setMessage("This will generate a new encryption key. Files encrypted with the old key will not be readable. Continue?")
            .setPositiveButton("Generate") { _, _ ->
                if (encryptionManager.generateNewEncryptionKey()) {
                    updateKeyStatus()
                    Toast.makeText(this, "New encryption key generated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to generate new key", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteKey() {
        AlertDialog.Builder(this)
            .setTitle("Delete Encryption Key")
            .setMessage("This will permanently delete the encryption key. Files encrypted with this key will not be readable. Continue?")
            .setPositiveButton("Delete") { _, _ ->
                if (encryptionManager.deleteEncryptionKey()) {
                    updateKeyStatus()
                    Toast.makeText(this, "Encryption key deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to delete key", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return "%.1f %s".format(size, units[unitIndex])
    }
}