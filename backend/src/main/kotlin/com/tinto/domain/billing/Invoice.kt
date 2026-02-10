package com.tinto.domain.billing

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Invoice status enum
 */
enum class InvoiceStatus {
    DRAFT,
    PENDING_SIGNATURE,
    SIGNED,
    SUBMITTED_TO_DIAN,
    ACCEPTED_BY_DIAN,
    REJECTED_BY_DIAN,
    CANCELLED
}

/**
 * Invoice entity representing an electronic invoice.
 *
 * This entity stores all invoice data and tracks its lifecycle
 * from creation through DIAN submission and acceptance.
 */
@Entity
@Table(
    name = "invoices",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["issuer_id", "prefix", "number"])
    ],
    indexes = [
        Index(name = "idx_invoices_issuer", columnList = "issuer_id"),
        Index(name = "idx_invoices_customer", columnList = "customer_id"),
        Index(name = "idx_invoices_status", columnList = "status"),
        Index(name = "idx_invoices_issue_date", columnList = "issue_date"),
        Index(name = "idx_invoices_cufe", columnList = "cufe")
    ]
)
data class Invoice(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    /**
     * Invoice number prefix (optional)
     */
    @Column(length = 10)
    val prefix: String? = null,

    /**
     * Invoice number
     */
    @Column(nullable = false)
    val number: Long,

    /**
     * Full invoice number (generated column: prefix + number)
     * Format: "PREFIX-12345" or "12345" if no prefix
     */
    @Column(name = "full_number", length = 20, insertable = false, updatable = false)
    val fullNumber: String? = null,

    /**
     * Issuer (seller) reference
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issuer_id", nullable = false)
    val issuer: Issuer,

    /**
     * Customer (buyer) reference
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    val customer: Customer,

    /**
     * Environment (dev/staging/production)
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false)
    val environment: Environment,

    /**
     * Issue date
     */
    @Column(name = "issue_date", nullable = false)
    val issueDate: LocalDate,

    /**
     * Due date (optional)
     */
    @Column(name = "due_date")
    val dueDate: LocalDate? = null,

    /**
     * Currency code (ISO 4217)
     */
    @Column(length = 3)
    val currency: String = "COP",

    /**
     * Subtotal amount in cents/centavos (before taxes)
     */
    @Column(nullable = false)
    val subtotal: Long,

    /**
     * Tax amount in cents/centavos
     */
    @Column(name = "tax_amount", nullable = false)
    val taxAmount: Long,

    /**
     * Total amount in cents/centavos (subtotal + taxes)
     */
    @Column(name = "total_amount", nullable = false)
    val totalAmount: Long,

    /**
     * CUFE (Código Único de Factura Electrónica)
     * SHA-384 hash uniquely identifying this invoice
     */
    @Column(length = 96)
    val cufe: String? = null,

    /**
     * Current status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: InvoiceStatus = InvoiceStatus.DRAFT,

    /**
     * Encrypted UBL XML (before signing)
     */
    @Column(name = "ubl_xml_encrypted", columnDefinition = "TEXT")
    var ublXmlEncrypted: String? = null,

    /**
     * Encrypted signed XML (after XAdES-EPES signing)
     */
    @Column(name = "signed_xml_encrypted", columnDefinition = "TEXT")
    var signedXmlEncrypted: String? = null,

    /**
     * Creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    /**
     * Last update timestamp
     */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    /**
     * API key that created this invoice
     */
    @Column(name = "created_by")
    val createdBy: UUID? = null,

    /**
     * Line items (one-to-many)
     */
    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val lineItems: MutableList<LineItem> = mutableListOf(),

    /**
     * DIAN submissions (one-to-many)
     */
    @OneToMany(mappedBy = "invoice", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val dianSubmissions: MutableList<DianSubmission> = mutableListOf()
) {
    /**
     * Update the updatedAt timestamp
     */
    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    /**
     * Add a line item to this invoice
     */
    fun addLineItem(lineItem: LineItem) {
        lineItems.add(lineItem)
    }

    /**
     * Get the total from line items (for validation)
     */
    fun calculateLineItemsTotal(): Long {
        return lineItems.sumOf { it.lineTotal }
    }

    /**
     * Get the total tax from line items (for validation)
     */
    fun calculateLineItemsTax(): Long {
        return lineItems.sumOf { it.taxAmount ?: 0L }
    }

    /**
     * Validate that totals match line items
     */
    fun validateTotals(): Boolean {
        val calculatedSubtotal = calculateLineItemsTotal()
        val calculatedTax = calculateLineItemsTax()
        val calculatedTotal = calculatedSubtotal + calculatedTax
        
        return subtotal == calculatedSubtotal && 
               taxAmount == calculatedTax && 
               totalAmount == calculatedTotal
    }

    /**
     * Check if invoice is editable
     */
    fun isEditable(): Boolean {
        return status == InvoiceStatus.DRAFT
    }

    /**
     * Check if invoice can be submitted to DIAN
     */
    fun canSubmitToDian(): Boolean {
        return status == InvoiceStatus.SIGNED && cufe != null
    }

    /**
     * Get the invoice number with prefix
     */
    fun getInvoiceNumber(): String {
        return if (prefix != null) "$prefix-$number" else number.toString()
    }
}

/**
 * Environment entity (dev, staging, production)
 */
@Entity
@Table(name = "environments")
data class Environment(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    @Column(nullable = false, unique = true, length = 50)
    val name: String,

    @Column(name = "dian_endpoint", nullable = false, length = 255)
    val dianEndpoint: String,

    @Column(name = "is_production", nullable = false)
    val isProduction: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
