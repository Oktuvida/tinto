package com.tinto.domain.billing

import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * Customer entity representing an invoice recipient.
 *
 * This entity stores information about customers (buyers/recipients)
 * who receive invoices.
 */
@Entity
@Table(
    name = "customers",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["identification_type", "identification_number"])
    ]
)
data class Customer(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    /**
     * Type of identification document
     * Examples: NIT, CC (Cédula de Ciudadanía), CE (Cédula de Extranjería), etc.
     */
    @Column(name = "identification_type", nullable = false, length = 20)
    val identificationType: String,

    /**
     * Identification number
     */
    @Column(name = "identification_number", nullable = false, length = 50)
    val identificationNumber: String,

    /**
     * Legal name or full name of the customer
     */
    @Column(nullable = false, length = 255)
    val name: String,

    /**
     * Email address
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
     * Get the full identification string (type + number)
     */
    fun getFullIdentification(): String {
        return "$identificationType-$identificationNumber"
    }
}
