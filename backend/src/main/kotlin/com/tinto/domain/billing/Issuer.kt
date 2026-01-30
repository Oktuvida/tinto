package com.tinto.domain.billing

import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * Issuer entity representing a company that issues invoices.
 *
 * This entity stores information about the invoice issuer (seller/provider)
 * and their DIAN certificate for signing invoices.
 */
@Entity
@Table(name = "issuers")
data class Issuer(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    /**
     * NIT (Número de Identificación Tributaria) - Colombian tax ID
     * Must be unique
     */
    @Column(nullable = false, unique = true, length = 20)
    val nit: String,

    /**
     * Legal name of the company
     */
    @Column(nullable = false)
    val name: String,

    /**
     * Trade name (optional)
     */
    @Column(name = "trade_name")
    val tradeName: String? = null,

    /**
     * Contact email
     */
    @Column(length = 255)
    val email: String? = null,

    /**
     * Phone number
     */
    @Column(length = 50)
    val phone: String? = null,

    /**
     * Physical address
     */
    @Column(columnDefinition = "TEXT")
    val address: String? = null,

    /**
     * City
     */
    @Column(length = 100)
    val city: String? = null,

    /**
     * Department (state/province)
     */
    @Column(length = 100)
    val department: String? = null,

    /**
     * Country code (ISO 3166-1 alpha-2)
     */
    @Column(length = 2)
    val country: String = "CO",

    /**
     * Tax regime (e.g., "Común", "Simplificado")
     */
    @Column(name = "tax_regime", length = 50)
    val taxRegime: String? = null,

    /**
     * Encrypted X.509 certificate data for signing invoices
     * This is encrypted at rest for security
     */
    @Column(name = "certificate_data", columnDefinition = "TEXT")
    val certificateData: String? = null,

    /**
     * Encrypted password for the certificate
     */
    @Column(name = "certificate_password_encrypted", columnDefinition = "TEXT")
    val certificatePasswordEncrypted: String? = null,

    /**
     * Certificate expiration date
     */
    @Column(name = "certificate_expires_at")
    val certificateExpiresAt: Instant? = null,

    /**
     * Creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /**
     * Last update timestamp
     */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    /**
     * Update the updatedAt timestamp
     */
    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    /**
     * Check if the certificate is still valid
     */
    fun isCertificateValid(): Boolean {
        return certificateExpiresAt?.isAfter(Instant.now()) ?: false
    }

    /**
     * Check if the issuer has a certificate configured
     */
    fun hasCertificate(): Boolean {
        return certificateData != null && certificatePasswordEncrypted != null
    }
}
