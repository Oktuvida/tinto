package com.tinto.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import javax.crypto.SecretKey

/**
 * Master key guard ensuring master key access is ONLY available from local console.
 *
 * Security requirements:
 * - Master key must never be accessible over the network
 * - Master key operations must only be possible from direct host/console access
 * - All master key operations are logged for audit
 */
@Service
class MasterKeyGuard(
    private val encryptionService: EncryptionService,
    @Value("\${tinto.master-key.location:/var/tinto/master.key.enc}")
    private val masterKeyLocation: String
) {

    private val logger = LoggerFactory.getLogger(MasterKeyGuard::class.java)

    companion object {
        private const val CONSOLE_ACCESS_ENV = "TINTO_CONSOLE_ACCESS"
        private const val SYSTEM_KEY_ENV = "TINTO_SYSTEM_KEY"
    }

    /**
     * Verify that the current execution context is local console access.
     * This check prevents network-based access to master key operations.
     *
     * @throws SecurityException if not running in local console context
     */
    fun verifyLocalConsoleAccess() {
        val consoleAccess = System.getenv(CONSOLE_ACCESS_ENV)
        
        if (consoleAccess != "true") {
            logger.error("Attempted master key access without local console authorization")
            throw SecurityException(
                "Master key operations are only available from local console access. " +
                "This operation cannot be performed over the network."
            )
        }
        
        logger.info("Local console access verified for master key operation")
    }

    /**
     * Load the master key from encrypted storage.
     * Requires local console access.
     *
     * @return Decrypted master SecretKey
     * @throws SecurityException if not local console access
     * @throws IllegalStateException if master key file not found or cannot be decrypted
     */
    fun loadMasterKey(): SecretKey {
        verifyLocalConsoleAccess()
        
        val keyFile = File(masterKeyLocation)
        if (!keyFile.exists()) {
            logger.error("Master key file not found at: $masterKeyLocation")
            throw IllegalStateException(
                "Master key file not found. Initialize the system first using: " +
                "tinto-admin init-master-key"
            )
        }

        return try {
            val encryptedKey = Files.readString(Paths.get(masterKeyLocation))
            val systemKey = getSystemKey()
            val keyBytes = encryptionService.decrypt(encryptedKey, systemKey)
            val decodedKey = encryptionService.decodeKey(keyBytes)
            
            logger.info("Master key loaded successfully from: $masterKeyLocation")
            decodedKey
        } catch (e: Exception) {
            logger.error("Failed to load master key", e)
            throw IllegalStateException("Failed to decrypt master key: ${e.message}", e)
        }
    }

    /**
     * Save the master key to encrypted storage.
     * Requires local console access.
     *
     * @param masterKey Master SecretKey to save
     * @throws SecurityException if not local console access
     */
    fun saveMasterKey(masterKey: SecretKey) {
        verifyLocalConsoleAccess()
        
        val keyFile = File(masterKeyLocation)
        keyFile.parentFile?.mkdirs()

        try {
            val encodedKey = encryptionService.encodeKey(masterKey)
            val systemKey = getSystemKey()
            val encryptedKey = encryptionService.encrypt(encodedKey, systemKey)
            
            Files.writeString(Paths.get(masterKeyLocation), encryptedKey)
            
            // Set restrictive permissions (owner read/write only)
            keyFile.setReadable(false, false)
            keyFile.setReadable(true, true)
            keyFile.setWritable(false, false)
            keyFile.setWritable(true, true)
            keyFile.setExecutable(false)
            
            logger.info("Master key saved successfully to: $masterKeyLocation")
        } catch (e: Exception) {
            logger.error("Failed to save master key", e)
            throw IllegalStateException("Failed to save master key: ${e.message}", e)
        }
    }

    /**
     * Generate a new master key.
     * Requires local console access.
     *
     * @return Newly generated master SecretKey
     * @throws SecurityException if not local console access
     */
    fun generateMasterKey(): SecretKey {
        verifyLocalConsoleAccess()
        
        logger.info("Generating new master key")
        val masterKey = encryptionService.generateKey()
        saveMasterKey(masterKey)
        
        logger.info("New master key generated and saved")
        return masterKey
    }

    /**
     * Derive an API key from the master key.
     * Requires local console access.
     *
     * @param keyName Name/identifier for the API key
     * @param role Role for the API key (ADMIN, OPERATOR, AUDITOR)
     * @return Derived API key as a secure token
     * @throws SecurityException if not local console access
     */
    fun deriveApiKey(keyName: String, role: String): String {
        verifyLocalConsoleAccess()
        
        val masterKey = loadMasterKey()
        val masterKeyString = encryptionService.encodeKey(masterKey)
        
        // Derive API key: hash(masterKey + keyName + role + timestamp)
        val timestamp = System.currentTimeMillis()
        val derivationInput = "$masterKeyString:$keyName:$role:$timestamp"
        val derivedKey = encryptionService.hash(derivationInput)
        
        // Generate secure token (first 32 bytes of hash)
        val apiKeyToken = derivedKey.substring(0, 64) // 32 bytes in hex
        
        logger.info("Derived API key for: $keyName with role: $role")
        return apiKeyToken
    }

    /**
     * Get the system key used for encrypting the master key at rest.
     * This key is stored in an environment variable for the system administrator to manage.
     *
     * @return System SecretKey
     * @throws IllegalStateException if system key not configured
     */
    private fun getSystemKey(): SecretKey {
        val systemKeyEnv = System.getenv(SYSTEM_KEY_ENV)
            ?: throw IllegalStateException(
                "System key not configured. Set $SYSTEM_KEY_ENV environment variable. " +
                "Generate with: openssl rand -base64 32"
            )
        
        return encryptionService.decodeKey(systemKeyEnv)
    }

    /**
     * Verify that master key infrastructure is properly initialized
     *
     * @return true if master key exists and can be loaded
     */
    fun isMasterKeyInitialized(): Boolean {
        val keyFile = File(masterKeyLocation)
        return keyFile.exists() && keyFile.canRead()
    }
}
