package com.tinto.domain.auth

import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * API key entity — derived from a master access key.
 *
 * Each API key has a role (ADMIN, OPERATOR, AUDITOR) that
 * determines what operations are allowed.
 */
@Entity
@Table(
    name = "api_keys",
    indexes = [
        Index(name = "idx_api_keys_active", columnList = "is_active")
    ]
)
data class ApiKey(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    /**
     * Human-readable name for this key
     */
    @Column(nullable = false, length = 100)
    val name: String,

    /**
     * Role assigned to this key
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: Role,

    /**
     * SHA-512 hash of the derived key (for lookup / verification)
     */
    @Column(name = "key_hash", nullable = false, unique = true, length = 128)
    val keyHash: String,

    /**
     * Encrypted key data
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
     * Last time this key was used for authentication
     */
    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null,

    /**
     * Whether this key is active
     */
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    /**
     * Master key that created this API key
     */
    @Column(name = "created_by")
    val createdBy: UUID? = null
) {
    fun isExpired(): Boolean =
        expiresAt != null && Instant.now().isAfter(expiresAt)

    fun isUsable(): Boolean = isActive && !isExpired()

    fun recordUsage() {
        lastUsedAt = Instant.now()
    }
}
