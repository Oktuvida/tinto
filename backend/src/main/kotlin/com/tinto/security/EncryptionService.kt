package com.tinto.security

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encryption service providing AES-256-GCM encryption for data at rest
 * and SHA-512 hashing for key derivation and verification.
 *
 * Security requirements:
 * - All stored data encrypted at rest
 * - All secrets encrypted in configuration
 * - No plaintext sensitive data in memory longer than necessary
 */
@Service
class EncryptionService {

    companion object {
        private const val AES_KEY_SIZE = 256
        private const val GCM_IV_SIZE = 12
        private const val GCM_TAG_SIZE = 128
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val HASH_ALGORITHM = "SHA-512"

        init {
            // Register BouncyCastle provider for advanced crypto
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val secureRandom = SecureRandom()

    /**
     * Generate a new AES-256 key for encryption
     */
    fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(AES_KEY_SIZE, secureRandom)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt data using AES-256-GCM
     *
     * @param plaintext Data to encrypt
     * @param key Secret key for encryption
     * @return Base64-encoded encrypted data with IV prepended (format: IV + ciphertext)
     */
    fun encrypt(plaintext: String, key: SecretKey): String {
        val iv = ByteArray(GCM_IV_SIZE)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Prepend IV to ciphertext (IV is not secret, just needs to be unique)
        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Encrypt data using AES-256-GCM with a password-derived key
     *
     * @param plaintext Data to encrypt
     * @param password Password to derive encryption key from
     * @return Base64-encoded encrypted data
     */
    fun encryptWithPassword(plaintext: String, password: String): String {
        val key = deriveKeyFromPassword(password)
        return encrypt(plaintext, key)
    }

    /**
     * Decrypt data using AES-256-GCM
     *
     * @param encryptedData Base64-encoded encrypted data (IV + ciphertext)
     * @param key Secret key for decryption
     * @return Decrypted plaintext
     */
    fun decrypt(encryptedData: String, key: SecretKey): String {
        val combined = Base64.getDecoder().decode(encryptedData)

        // Extract IV and ciphertext
        val iv = combined.sliceArray(0 until GCM_IV_SIZE)
        val ciphertext = combined.sliceArray(GCM_IV_SIZE until combined.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }

    /**
     * Decrypt data using AES-256-GCM with a password-derived key
     *
     * @param encryptedData Base64-encoded encrypted data
     * @param password Password to derive decryption key from
     * @return Decrypted plaintext
     */
    fun decryptWithPassword(encryptedData: String, password: String): String {
        val key = deriveKeyFromPassword(password)
        return decrypt(encryptedData, key)
    }

    /**
     * Hash data using SHA-512
     *
     * @param data Data to hash
     * @return Hex-encoded hash
     */
    fun hash(data: String): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val hashBytes = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify that data matches a hash
     *
     * @param data Data to verify
     * @param expectedHash Expected hash value
     * @return true if data matches hash
     */
    fun verifyHash(data: String, expectedHash: String): Boolean {
        val actualHash = hash(data)
        return actualHash.equals(expectedHash, ignoreCase = true)
    }

    /**
     * Derive a SecretKey from a password using SHA-512
     * Note: For production, consider using PBKDF2 with salt for stronger key derivation
     *
     * @param password Password to derive key from
     * @return Derived SecretKey
     */
    private fun deriveKeyFromPassword(password: String): SecretKey {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val keyBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        // Use first 256 bits (32 bytes) for AES-256
        val aesKeyBytes = keyBytes.sliceArray(0 until 32)
        return SecretKeySpec(aesKeyBytes, "AES")
    }

    /**
     * Securely encode a SecretKey to Base64 for storage
     *
     * @param key SecretKey to encode
     * @return Base64-encoded key
     */
    fun encodeKey(key: SecretKey): String {
        return Base64.getEncoder().encodeToString(key.encoded)
    }

    /**
     * Decode a Base64-encoded SecretKey
     *
     * @param encodedKey Base64-encoded key
     * @return Decoded SecretKey
     */
    fun decodeKey(encodedKey: String): SecretKey {
        val keyBytes = Base64.getDecoder().decode(encodedKey)
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Generate a cryptographically secure random token
     *
     * @param length Length of token in bytes (default 32)
     * @return Base64-encoded random token
     */
    fun generateSecureToken(length: Int = 32): String {
        val tokenBytes = ByteArray(length)
        secureRandom.nextBytes(tokenBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
    }
}
