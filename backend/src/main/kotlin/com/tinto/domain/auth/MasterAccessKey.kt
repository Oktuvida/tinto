package com.tinto.domain.auth

import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * Master access key entity.
 *
 * Master keys are the root credentials used locally to derive API keys.
 * The actual key material is stored encrypted; only the SHA-512 hash is
 * used for lookup/verification.
 */
@Entity
@Table(name = "master_access_keys")
data class MasterAccessKey(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    /**
     * SHA-512 hash of the master key (for lookup)
     */
    @Column(name = "key_hash", nullable = false, unique = true, length = 128)
    val keyHash: String,

    /**
     * Encrypted key data (AES-256-GCM encrypted with system key)
     */
    @Column(name = "encrypted_key_data", nullable = false, columnDefinition = "TEXT")
    val encryptedKeyData: String,

    /**
     * Creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /**
     * Optional expiration
     */
    @Column(name = "expires_at")
    val expiresAt: Instant? = null,

    /**
     * Whether this key is active
     */
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
) {
    fun isExpired(): Boolean =
        expiresAt != null && Instant.now().isAfter(expiresAt)

    fun isUsable(): Boolean = isActive && !isExpired()
}
