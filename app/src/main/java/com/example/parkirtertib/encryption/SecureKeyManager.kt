package com.example.parkirtertib.encryption

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Manages secure keys using Android Keystore for file encryption
 */
class SecureKeyManager {
    
    companion object {
        private const val TAG = "SecureKeyManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "FileEncryptionKey"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
    
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    
    init {
        keyStore.load(null)
    }
    
    /**
     * Generates or retrieves the encryption key from Android Keystore
     */
    fun getOrCreateEncryptionKey(): SecretKey {
        return try {
            // Try to retrieve existing key
            val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existingKey != null) {
                Log.d(TAG, "Retrieved existing encryption key")
                existingKey
            } else {
                generateNewKey()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve existing key, generating new one", e)
            generateNewKey()
        }
    }
    
    /**
     * Generates a new encryption key in the Android Keystore
     */
    private fun generateNewKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        val secretKey = keyGenerator.generateKey()
        
        Log.d(TAG, "Generated new encryption key")
        return secretKey
    }
    
    /**
     * Checks if encryption key exists
     */
    fun hasEncryptionKey(): Boolean {
        return try {
            keyStore.containsAlias(KEY_ALIAS)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for encryption key", e)
            false
        }
    }
    
    /**
     * Deletes the encryption key (use with caution)
     */
    fun deleteEncryptionKey(): Boolean {
        return try {
            keyStore.deleteEntry(KEY_ALIAS)
            Log.d(TAG, "Encryption key deleted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete encryption key", e)
            false
        }
    }
}