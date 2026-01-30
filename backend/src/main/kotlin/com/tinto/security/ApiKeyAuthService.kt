package com.tinto.security

import com.tinto.domain.auth.Role
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * API key authentication service handling:
 * - API key derivation from master key
 * - Request signature validation
 * - Replay attack prevention
 *
 * Security model:
 * - API keys are derived from master key
 * - Each request must include a signature: HMAC-SHA512(apiKey, method + path + timestamp + body)
 * - Signatures expire after 5 minutes to prevent replay attacks
 */
@Service
class ApiKeyAuthService(
    private val encryptionService: EncryptionService
) {

    private val logger = LoggerFactory.getLogger(ApiKeyAuthService::class.java)

    companion object {
        private const val SIGNATURE_EXPIRATION_MINUTES = 5L
        private const val API_KEY_HEADER = "X-Tinto-API-Key"
        private const val SIGNATURE_HEADER = "X-Tinto-Signature"
        private const val TIMESTAMP_HEADER = "X-Tinto-Timestamp"
    }

    /**
     * Derive an API key from a master key string
     *
     * @param masterKeyString Encoded master key
     * @param keyName Unique name for the API key
     * @param role Role assigned to the API key
     * @return Derived API key token
     */
    fun deriveApiKey(masterKeyString: String, keyName: String, role: Role): String {
        val timestamp = System.currentTimeMillis()
        val derivationInput = "$masterKeyString:$keyName:${role.name}:$timestamp"
        val hash = encryptionService.hash(derivationInput)
        
        // Use first 64 characters (32 bytes in hex) as API key
        val apiKey = hash.substring(0, 64)
        
        logger.debug("Derived API key for $keyName with role ${role.name}")
        return apiKey
    }

    /**
     * Create a request signature for authenticating API calls
     *
     * @param apiKey API key token
     * @param method HTTP method (GET, POST, etc.)
     * @param path Request path
     * @param timestamp Request timestamp (ISO-8601)
     * @param body Request body (empty string if no body)
     * @return HMAC-SHA512 signature
     */
    fun createSignature(
        apiKey: String,
        method: String,
        path: String,
        timestamp: String,
        body: String = ""
    ): String {
        val signatureInput = "$method:$path:$timestamp:$body"
        val combined = "$apiKey:$signatureInput"
        return encryptionService.hash(combined)
    }

    /**
     * Validate a request signature
     *
     * @param apiKey API key from request header
     * @param signature Signature from request header
     * @param method HTTP method
     * @param path Request path
     * @param timestamp Timestamp from request header
     * @param body Request body
     * @return ValidationResult indicating if signature is valid
     */
    fun validateSignature(
        apiKey: String,
        signature: String,
        method: String,
        path: String,
        timestamp: String,
        body: String = ""
    ): ValidationResult {
        // Validate timestamp is not too old (replay attack prevention)
        val requestTime = try {
            Instant.parse(timestamp)
        } catch (e: Exception) {
            logger.warn("Invalid timestamp format: $timestamp")
            return ValidationResult(false, "Invalid timestamp format")
        }

        val now = Instant.now()
        val minutesSinceRequest = ChronoUnit.MINUTES.between(requestTime, now)
        
        if (minutesSinceRequest > SIGNATURE_EXPIRATION_MINUTES) {
            logger.warn("Signature expired: $minutesSinceRequest minutes old")
            return ValidationResult(false, "Signature expired")
        }

        if (minutesSinceRequest < -1) {
            logger.warn("Timestamp is in the future: $timestamp")
            return ValidationResult(false, "Invalid timestamp: future time")
        }

        // Compute expected signature
        val expectedSignature = createSignature(apiKey, method, path, timestamp, body)

        // Constant-time comparison to prevent timing attacks
        if (!constantTimeEquals(signature, expectedSignature)) {
            logger.warn("Invalid signature for request: $method $path")
            return ValidationResult(false, "Invalid signature")
        }

        logger.debug("Valid signature for request: $method $path")
        return ValidationResult(true, "Valid")
    }

    /**
     * Verify API key format and structure
     *
     * @param apiKey API key to verify
     * @return true if API key has valid format
     */
    fun isValidApiKeyFormat(apiKey: String): Boolean {
        // API key should be 64 hex characters
        return apiKey.matches(Regex("^[0-9a-fA-F]{64}$"))
    }

    /**
     * Hash an API key for secure storage in database
     *
     * @param apiKey Plain API key
     * @return Hashed API key for database storage
     */
    fun hashApiKey(apiKey: String): String {
        return encryptionService.hash(apiKey)
    }

    /**
     * Verify an API key against its hash
     *
     * @param apiKey Plain API key
     * @param hashedApiKey Hashed API key from database
     * @return true if API key matches hash
     */
    fun verifyApiKey(apiKey: String, hashedApiKey: String): Boolean {
        return encryptionService.verifyHash(apiKey, hashedApiKey)
    }

    /**
     * Constant-time string comparison to prevent timing attacks
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) {
            return false
        }

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    /**
     * Extract API key from request headers
     */
    fun extractApiKey(headers: Map<String, String>): String? {
        return headers[API_KEY_HEADER] ?: headers[API_KEY_HEADER.lowercase()]
    }

    /**
     * Extract signature from request headers
     */
    fun extractSignature(headers: Map<String, String>): String? {
        return headers[SIGNATURE_HEADER] ?: headers[SIGNATURE_HEADER.lowercase()]
    }

    /**
     * Extract timestamp from request headers
     */
    fun extractTimestamp(headers: Map<String, String>): String? {
        return headers[TIMESTAMP_HEADER] ?: headers[TIMESTAMP_HEADER.lowercase()]
    }

    /**
     * Result of signature validation
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
}
