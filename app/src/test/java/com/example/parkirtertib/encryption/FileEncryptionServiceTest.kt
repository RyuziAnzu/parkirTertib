package com.example.parkirtertib.encryption

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileEncryptionServiceTest {

    private lateinit var context: Context
    private lateinit var keyManager: SecureKeyManager
    private lateinit var encryptionService: FileEncryptionService
    private lateinit var testFile: File
    private lateinit var encryptedFile: File
    private lateinit var decryptedFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        keyManager = SecureKeyManager()
        encryptionService = FileEncryptionService(keyManager)
        
        // Create test files
        testFile = File(context.filesDir, "test_file.txt")
        encryptedFile = File(context.filesDir, "test_file.txt.enc")
        decryptedFile = File(context.filesDir, "test_file_decrypted.txt")
        
        // Create test content
        testFile.writeText("This is a test file for encryption testing. " +
                "It contains some sample text to verify that encryption and decryption work correctly.")
    }

    @After
    fun cleanup() {
        // Clean up test files
        testFile.delete()
        encryptedFile.delete()
        decryptedFile.delete()
        
        // Clean up encryption key
        keyManager.deleteEncryptionKey()
    }

    @Test
    fun testEncryptionAndDecryption() = runBlocking {
        // Test encryption
        val encryptResult = encryptionService.encryptFile(
            testFile.absolutePath,
            encryptedFile.absolutePath
        )
        
        assertTrue("Encryption should succeed", encryptResult.success)
        assertNotNull("Encrypted file should exist", encryptResult.outputPath)
        assertTrue("Encrypted file should exist on disk", encryptedFile.exists())
        assertNotNull("Original checksum should be calculated", encryptResult.originalChecksum)
        assertNotNull("Encrypted checksum should be calculated", encryptResult.encryptedChecksum)
        
        // Test decryption
        val decryptResult = encryptionService.decryptFile(
            encryptedFile.absolutePath,
            decryptedFile.absolutePath
        )
        
        assertTrue("Decryption should succeed", decryptResult.success)
        assertNotNull("Decrypted file should exist", decryptResult.outputPath)
        assertTrue("Decrypted file should exist on disk", decryptedFile.exists())
        assertTrue("Integrity should be verified", decryptResult.integrityVerified)
        
        // Verify content integrity
        val originalContent = testFile.readText()
        val decryptedContent = decryptedFile.readText()
        assertEquals("Decrypted content should match original", originalContent, decryptedContent)
        
        // Verify checksums match
        assertEquals("Original and decrypted checksums should match", 
            encryptResult.originalChecksum, decryptResult.decryptedChecksum)
    }

    @Test
    fun testKeyGeneration() {
        // Test key generation
        val key1 = keyManager.getOrCreateEncryptionKey()
        assertNotNull("Key should be generated", key1)
        
        assertTrue("Key should exist", keyManager.hasEncryptionKey())
        
        // Test key retrieval
        val key2 = keyManager.getOrCreateEncryptionKey()
        assertEquals("Retrieved key should be the same", key1.encoded.contentToString(), key2.encoded.contentToString())
    }

    @Test
    fun testKeyDeletion() {
        // Generate a key
        keyManager.getOrCreateEncryptionKey()
        assertTrue("Key should exist", keyManager.hasEncryptionKey())
        
        // Delete the key
        assertTrue("Key deletion should succeed", keyManager.deleteEncryptionKey())
        assertFalse("Key should not exist after deletion", keyManager.hasEncryptionKey())
    }

    @Test
    fun testEncryptionWithProgress() = runBlocking {
        var progressUpdates = 0
        var lastProgress = 0
        
        val progressCallback = object : FileEncryptionService.ProgressCallback {
            override fun onProgress(progress: Int, bytesProcessed: Long, totalBytes: Long) {
                progressUpdates++
                lastProgress = progress
                assertTrue("Progress should be between 0 and 100", progress in 0..100)
                assertTrue("Bytes processed should not exceed total", bytesProcessed <= totalBytes)
            }
            
            override fun onComplete() {
                // Operation completed
            }
            
            override fun onError(error: String) {
                fail("Encryption should not fail: $error")
            }
        }
        
        val result = encryptionService.encryptFile(
            testFile.absolutePath,
            encryptedFile.absolutePath,
            progressCallback
        )
        
        assertTrue("Encryption should succeed", result.success)
        assertEquals("Final progress should be 100", 100, lastProgress)
    }

    @Test
    fun testEncryptionWithInvalidFile() = runBlocking {
        val nonExistentFile = File(context.filesDir, "non_existent_file.txt")
        
        val result = encryptionService.encryptFile(
            nonExistentFile.absolutePath,
            encryptedFile.absolutePath
        )
        
        assertFalse("Encryption should fail for non-existent file", result.success)
        assertNotNull("Error message should be provided", result.error)
    }
}