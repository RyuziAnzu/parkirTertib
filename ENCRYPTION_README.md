# File Encryption and Decryption Implementation

This implementation adds comprehensive file encryption and decryption functionality to the ParkirTertib Android application using industry-standard security practices.

## Overview

The encryption system provides:
- AES-256-GCM encryption for maximum security
- Hardware-backed key storage using Android Keystore
- Background processing for large files
- Real-time progress tracking
- File integrity verification
- Chunk-based processing for memory efficiency

## Architecture

### Core Components

1. **SecureKeyManager** (`encryption/SecureKeyManager.kt`)
   - Manages encryption keys using Android Keystore
   - Provides hardware-backed security
   - Supports key generation, retrieval, and deletion

2. **FileEncryptionService** (`encryption/FileEncryptionService.kt`)
   - Core encryption/decryption engine
   - Uses AES-256-GCM for authenticated encryption
   - Processes files in 64KB chunks for memory efficiency
   - Calculates SHA-256 checksums for integrity verification

3. **EncryptionWorker** (`encryption/EncryptionWorker.kt`)
   - Background processing using Android WorkManager
   - Handles long-running encryption/decryption tasks
   - Provides progress updates and error handling
   - Survives app lifecycle changes

4. **EncryptionManager** (`encryption/EncryptionManager.kt`)
   - High-level coordinator for encryption operations
   - Manages WorkManager integration
   - Provides LiveData for UI updates
   - Handles operation history and cancellation

5. **EncryptionActivity** (`EncryptionActivity.kt`)
   - Complete UI for file encryption management
   - File picker integration
   - Real-time progress tracking
   - Key management interface

## Security Features

### Encryption Algorithm
- **Algorithm**: AES-256-GCM (Galois/Counter Mode)
- **Key Size**: 256 bits
- **IV**: 12 bytes, randomly generated per operation
- **Authentication**: Built-in with GCM mode

### Key Management
- **Storage**: Android Keystore (hardware-backed when available)
- **Generation**: Cryptographically secure random generation
- **Protection**: Keys never leave secure hardware
- **Lifecycle**: Automatic key creation and secure deletion

### File Integrity
- **Checksum**: SHA-256 of original file
- **Verification**: Automatic integrity check after decryption
- **Metadata**: Encrypted file contains original file information

## Performance Optimizations

### Memory Efficiency
- **Chunk Size**: 64KB chunks prevent memory exhaustion
- **Streaming**: Files processed without loading entirely into memory
- **Garbage Collection**: Minimal object allocation during processing

### Background Processing
- **WorkManager**: Reliable background execution
- **Progress Updates**: Real-time progress with byte-level precision
- **Cancellation**: User can cancel long-running operations
- **Persistence**: Operations survive app restarts

## Usage

### Basic Encryption/Decryption

```kotlin
val encryptionManager = EncryptionManager(context)

// Encrypt a file
val operationId = encryptionManager.encryptFileAsync(inputPath, outputPath)

// Monitor progress
encryptionManager.getOperationProgress(operationId).observe(this) { progress ->
    // Update UI with progress
}

// Decrypt a file
val decryptionId = encryptionManager.decryptFileAsync(encryptedPath, outputPath)
```

### Key Management

```kotlin
val keyManager = SecureKeyManager()

// Check if key exists
if (keyManager.hasEncryptionKey()) {
    // Key is available
}

// Generate new key
keyManager.getOrCreateEncryptionKey()

// Delete key (caution: makes encrypted files unreadable)
keyManager.deleteEncryptionKey()
```

## File Format

Encrypted files use the following format:

```
[64-byte Header][Encrypted Data]
```

Header contains:
- Bytes 0-11: IV (Initialization Vector)
- Bytes 12-19: Original file size
- Bytes 20-63: SHA-256 checksum of original file

## Testing

Comprehensive unit tests are provided in `FileEncryptionServiceTest.kt`:

- End-to-end encryption/decryption testing
- Key management functionality
- Progress callback verification
- Error handling validation
- File integrity verification

## Integration

The encryption functionality integrates seamlessly with the existing ParkirTertib app:

1. **Menu Integration**: Accessible via toolbar menu in MainActivity
2. **Navigation**: Dedicated EncryptionActivity for file operations
3. **Permissions**: Automatic storage permission handling
4. **UI Consistency**: Matches existing app design patterns

## Dependencies Added

```kotlin
// Encryption and Background Processing
implementation(libs.androidx.work.runtime)
implementation(libs.kotlinx.coroutines.core)
implementation(libs.kotlinx.coroutines.android)
implementation(libs.androidx.security.crypto)
```

## Permissions Required

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

## Error Handling

The system provides comprehensive error handling:

- **File Access Errors**: Proper permission checking and user feedback
- **Encryption Failures**: Detailed error messages and recovery suggestions
- **Key Management Issues**: Automatic key regeneration when possible
- **Background Task Failures**: Robust retry mechanisms and user notification

## Performance Characteristics

- **Small Files** (< 1MB): Near-instantaneous processing
- **Medium Files** (1-100MB): Progress updates every few seconds
- **Large Files** (> 100MB): Chunk-based processing with detailed progress
- **Memory Usage**: Constant ~64KB regardless of file size
- **Battery Impact**: Minimal due to efficient WorkManager scheduling

## Future Enhancements

Potential improvements for future versions:

1. **Multiple Key Support**: Support for different encryption keys per operation
2. **Cloud Integration**: Direct encryption/decryption for cloud storage
3. **Batch Operations**: Encrypt/decrypt multiple files simultaneously
4. **Password Protection**: Additional layer with user-defined passwords
5. **Compression**: Optional compression before encryption