package com.tinto.api

import com.tinto.api.dto.CreateInvoiceRequest
import com.tinto.api.dto.LineItemRequest
import com.tinto.domain.billing.Invoice
import com.tinto.domain.billing.InvoiceStatus
import com.tinto.services.invoice.InvoiceService
import com.tinto.services.invoice.InvoiceServiceException
import com.tinto.services.invoice.InvoiceStatusService
import com.tinto.services.invoice.InvoiceStatusDetail
import com.tinto.services.invoice.InvoiceWithLineItems
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Invoice REST API Controller
 *
 * Endpoints for creating and managing invoices.
 * All requests require API key authentication.
 */
@RestController
@RequestMapping("/v1/invoices")
class InvoiceController(
    private val invoiceService: InvoiceService,
    private val invoiceStatusService: InvoiceStatusService
) {

    private val logger = LoggerFactory.getLogger(InvoiceController::class.java)

    companion object {
        private val VALID_IDENTIFICATION_TYPES = setOf(
            "NIT", "CC", "CE", "TI", "PP", "DIE", "NUIP", "RC"
        )
        private val VALID_CURRENCIES = setOf("COP", "USD", "EUR")
        private const val MAX_LINE_ITEMS = 500
    }

    /**
     * Create a new invoice
     *
     * POST /v1/invoices
     *
     * This creates a draft invoice. Use POST /v1/invoices/{id}/issue
     * to submit it to DIAN.
     */
    @PostMapping
    fun createInvoice(
        @Valid @RequestBody request: CreateInvoiceRequest
    ): ResponseEntity<InvoiceResponse> {
        logger.info("Creating invoice for issuer: ${request.issuerNit}")

        // Business validations beyond Jakarta annotations
        validateCreateRequest(request)

        val invoice = invoiceService.createInvoice(request)

        logger.info("Invoice created: ${invoice.id}")

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(invoice.toResponse())
    }

    /**
     * Issue invoice to DIAN
     *
     * POST /v1/invoices/{id}/issue
     *
     * This submits the invoice to DIAN for validation and acceptance.
     * The process is asynchronous - use GET /v1/invoices/{id} to check status.
     */
    @PostMapping("/{id}/issue")
    fun issueInvoice(
        @PathVariable id: UUID
    ): ResponseEntity<InvoiceResponse> {
        logger.info("Issuing invoice to DIAN: $id")

        val invoice = invoiceService.issueInvoice(id)

        logger.info("Invoice issued to DIAN: ${invoice.id}, Status: ${invoice.status}")

        return ResponseEntity.ok(invoice.toResponse())
    }

    /**
     * Get invoice by ID
     *
     * GET /v1/invoices/{id}
     */
    @GetMapping("/{id}")
    fun getInvoice(
        @PathVariable id: UUID,
        @RequestParam(required = false, defaultValue = "false") includeLineItems: Boolean
    ): ResponseEntity<Any> {
        logger.debug("Getting invoice: $id")

        return if (includeLineItems) {
            val invoiceWithLineItems = invoiceService.getInvoiceWithLineItems(id)
            ResponseEntity.ok(invoiceWithLineItems.toDetailedResponse())
        } else {
            val invoice = invoiceService.getInvoice(id)
            ResponseEntity.ok(invoice.toResponse())
        }
    }

    /**
     * List invoices by issuer
     *
     * GET /v1/invoices?issuerNit={nit}
     */
    @GetMapping
    fun listInvoices(
        @RequestParam(required = true) issuerNit: String
    ): ResponseEntity<List<InvoiceResponse>> {
        logger.debug("Listing invoices for issuer: $issuerNit")

        if (!issuerNit.matches(Regex("^[0-9]{9,10}$"))) {
            throw IllegalArgumentException("issuerNit must be 9-10 digits")
        }

        val invoices = invoiceService.listInvoicesByIssuer(issuerNit)

        return ResponseEntity.ok(invoices.map { it.toResponse() })
    }

    /**
     * Get invoice status with DIAN submission details and error guidance
     *
     * GET /v1/invoices/{id}/status
     *
     * Returns the full status including:
     * - Current invoice status
     * - All DIAN submissions
     * - Error guidance (if rejected/errored)
     * - Whether the invoice can be retried or re-issued
     */
    @GetMapping("/{id}/status")
    fun getInvoiceStatus(
        @PathVariable id: UUID
    ): ResponseEntity<InvoiceStatusDetail> {
        logger.debug("Getting status for invoice: $id")

        val statusDetail = invoiceStatusService.getInvoiceStatus(id)

        return ResponseEntity.ok(statusDetail)
    }

    /**
     * Refresh invoice status by polling DIAN
     *
     * POST /v1/invoices/{id}/status/refresh
     *
     * Polls DIAN for the latest submission status.
     * Only has effect when the latest submission is in a non-final state
     * (SUBMITTED or PROCESSING).
     */
    @PostMapping("/{id}/status/refresh")
    fun refreshInvoiceStatus(
        @PathVariable id: UUID
    ): ResponseEntity<InvoiceStatusDetail> {
        logger.info("Refreshing status for invoice: $id")

        val statusDetail = invoiceStatusService.refreshStatus(id)

        return ResponseEntity.ok(statusDetail)
    }

    /**
     * Business-level validations for invoice creation
     */
    private fun validateCreateRequest(request: CreateInvoiceRequest) {
        // Validate identification type is a known DIAN code
        if (request.customerIdentificationType !in VALID_IDENTIFICATION_TYPES) {
            throw IllegalArgumentException(
                "Invalid identification type '${request.customerIdentificationType}'. " +
                "Must be one of: ${VALID_IDENTIFICATION_TYPES.joinToString()}"
            )
        }

        // Validate currency if provided
        val currency = request.currencyCode ?: "COP"
        if (currency !in VALID_CURRENCIES) {
            throw IllegalArgumentException(
                "Unsupported currency '$currency'. Must be one of: ${VALID_CURRENCIES.joinToString()}"
            )
        }

        // Validate line item count limit
        if (request.lineItems.size > MAX_LINE_ITEMS) {
            throw IllegalArgumentException(
                "Too many line items (${request.lineItems.size}). Maximum is $MAX_LINE_ITEMS"
            )
        }

        // Validate due date is after issue date when both are provided
        if (request.issueDate != null && request.dueDate != null) {
            if (!request.dueDate.isAfter(request.issueDate)) {
                throw IllegalArgumentException("Due date must be after issue date")
            }
        }

        // Validate line item totals are reasonable (no overflow)
        request.lineItems.forEach { item ->
            val lineTotal = item.quantity.multiply(item.unitPrice)
            if (lineTotal.compareTo(BigDecimal("999999999999.99")) > 0) {
                throw IllegalArgumentException(
                    "Line item '${item.description}' total exceeds maximum allowed amount"
                )
            }
        }
    }
}

/**
 * Invoice API response
 */
data class InvoiceResponse(
    val id: UUID,
    val invoiceNumber: String,
    val prefix: String?,
    val number: Long,
    val issuerNit: String,
    val issuerName: String,
    val customerIdentification: String,
    val customerName: String,
    val issueDate: LocalDate,
    val dueDate: LocalDate?,
    val currency: String,
    val subtotal: BigDecimal,
    val taxAmount: BigDecimal,
    val totalAmount: BigDecimal,
    val status: InvoiceStatus,
    val cufe: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Detailed invoice response with line items
 */
data class DetailedInvoiceResponse(
    val invoice: InvoiceResponse,
    val lineItems: List<LineItemResponse>
)

/**
 * Line item API response
 */
data class LineItemResponse(
    val lineNumber: Int,
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val lineTotal: BigDecimal,
    val taxRate: BigDecimal?,
    val taxAmount: BigDecimal?
)

/**
 * Extension functions to convert entities to API responses
 */
private fun Invoice.toResponse(): InvoiceResponse {
    return InvoiceResponse(
        id = this.id!!,
        invoiceNumber = this.getInvoiceNumber(),
        prefix = this.prefix,
        number = this.number,
        issuerNit = this.issuer.nit,
        issuerName = this.issuer.name,
        customerIdentification = "${this.customer.identificationType} ${this.customer.identificationNumber}",
        customerName = this.customer.name,
        issueDate = this.issueDate,
        dueDate = this.dueDate,
        currency = this.currency,
        subtotal = convertFromCents(this.subtotal),
        taxAmount = convertFromCents(this.taxAmount),
        totalAmount = convertFromCents(this.totalAmount),
        status = this.status,
        cufe = this.cufe,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}

private fun InvoiceWithLineItems.toDetailedResponse(): DetailedInvoiceResponse {
    return DetailedInvoiceResponse(
        invoice = this.invoice.toResponse(),
        lineItems = this.lineItems.map { lineItem ->
            LineItemResponse(
                lineNumber = lineItem.lineNumber,
                description = lineItem.description,
                quantity = lineItem.quantity,
                unitPrice = convertFromCents(lineItem.unitPrice),
                lineTotal = convertFromCents(lineItem.lineTotal),
                taxRate = lineItem.taxRate,
                taxAmount = lineItem.taxAmount?.let { convertFromCents(it) }
            )
        }
    )
}

/**
 * Convert cents to decimal amount
 */
private fun convertFromCents(cents: Long): BigDecimal {
    return BigDecimal(cents).divide(BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)
}
