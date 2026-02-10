package com.tinto.domain.auth

import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * Request signature entity — prevents replay attacks.
 *
 * Each authenticated API request records its HMAC signature here.
 * The (signature_hash, request_timestamp) unique constraint ensures
 * the same signature cannot be reused.
 */
@Entity
@Table(
    name = "request_signatures",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["signature_hash", "request_timestamp"])
    ],
    indexes = [
        Index(name = "idx_request_signatures_api_key", columnList = "api_key_id"),
        Index(name = "idx_request_signatures_timestamp", columnList = "request_timestamp")
    ]
)
data class RequestSignature(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    /**
     * API key that created this signature
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_key_id", nullable = false)
    val apiKey: ApiKey,

    /**
     * HMAC-SHA512 hash of the request signature
     */
    @Column(name = "signature_hash", nullable = false, length = 128)
    val signatureHash: String,

    /**
     * HTTP method of the request
     */
    @Column(name = "request_method", nullable = false, length = 10)
    val requestMethod: String,

    /**
     * Path of the request
     */
    @Column(name = "request_path", nullable = false, length = 500)
    val requestPath: String,

    /**
     * Timestamp from the request header
     */
    @Column(name = "request_timestamp", nullable = false)
    val requestTimestamp: Instant,

    /**
     * Whether this signature is still valid
     */
    @Column(name = "is_valid", nullable = false)
    var isValid: Boolean = true,

    /**
     * Creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
