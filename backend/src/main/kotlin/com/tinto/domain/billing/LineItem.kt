package com.tinto.domain.billing

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/**
 * LineItem entity representing a single line in an invoice.
 *
 * Each line item represents a product or service being billed.
 */
@Entity
@Table(
    name = "line_items",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["invoice_id", "line_number"])
    ],
    indexes = [
        Index(name = "idx_line_items_invoice", columnList = "invoice_id")
    ]
)
data class LineItem(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    /**
     * Invoice this line belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    val invoice: Invoice,

    /**
     * Line number within the invoice (1, 2, 3, ...)
     */
    @Column(name = "line_number", nullable = false)
    val lineNumber: Int,

    /**
     * Description of the product/service
     */
    @Column(nullable = false, length = 500)
    val description: String,

    /**
     * Quantity
     * Stored with 4 decimal places precision
     */
    @Column(nullable = false, precision = 12, scale = 4)
    val quantity: BigDecimal,

    /**
     * Unit price in cents/centavos
     */
    @Column(name = "unit_price", nullable = false)
    val unitPrice: Long,

    /**
     * Line total in cents/centavos (quantity * unit_price)
     */
    @Column(name = "line_total", nullable = false)
    val lineTotal: Long,

    /**
     * Tax rate as percentage (e.g., 19.00 for 19% VAT)
     */
    @Column(name = "tax_rate", precision = 5, scale = 2)
    val taxRate: BigDecimal? = null,

    /**
     * Tax amount in cents/centavos
     */
    @Column(name = "tax_amount")
    val taxAmount: Long? = null,

    /**
     * Creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    /**
     * Calculate the line total from quantity and unit price
     */
    companion object {
        fun calculateLineTotal(quantity: BigDecimal, unitPrice: Long): Long {
            return (quantity.multiply(BigDecimal(unitPrice))).toLong()
        }

        /**
         * Calculate tax amount from line total and tax rate
         */
        fun calculateTaxAmount(lineTotal: Long, taxRate: BigDecimal?): Long? {
            if (taxRate == null) return null
            return (BigDecimal(lineTotal).multiply(taxRate).divide(BigDecimal(100))).toLong()
        }
    }

    /**
     * Get the total amount including tax
     */
    fun getTotalWithTax(): Long {
        return lineTotal + (taxAmount ?: 0L)
    }

    /**
     * Validate that calculated values match stored values
     */
    fun validate(): Boolean {
        val expectedLineTotal = calculateLineTotal(quantity, unitPrice)
        val expectedTaxAmount = calculateTaxAmount(lineTotal, taxRate)
        
        return lineTotal == expectedLineTotal && taxAmount == expectedTaxAmount
    }
}
