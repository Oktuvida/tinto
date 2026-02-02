package com.tinto.services.invoice

import com.tinto.api.dto.CreateInvoiceRequest
import com.tinto.api.dto.LineItemRequest
import com.tinto.domain.billing.*
import com.tinto.repository.*
import com.tinto.services.dian.DianSubmissionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

/**
 * Invoice Issuance Service
 *
 * Business logic for creating and issuing invoices.
 * Handles:
 * - Invoice creation and validation
 * - Line item management
 * - Tax calculations
 * - Sequential numbering
 * - DIAN submission orchestration
 */
@Service
@Transactional
class InvoiceService(
    private val invoiceRepository: InvoiceRepository,
    private val issuerRepository: IssuerRepository,
    private val customerRepository: CustomerRepository,
    private val lineItemRepository: LineItemRepository,
    private val environmentRepository: EnvironmentRepository,
    private val dianSubmissionService: DianSubmissionService
) {

    private val logger = LoggerFactory.getLogger(InvoiceService::class.java)

    /**
     * Create and issue a new invoice
     *
     * @param request Invoice creation request
     * @return Created invoice with DIAN submission started
     */
    fun createInvoice(request: CreateInvoiceRequest): Invoice {
        logger.info("Creating invoice for issuer NIT: ${request.issuerNit}")

        // Step 1: Validate issuer
        val issuer = issuerRepository.findByNit(request.issuerNit)
            .orElseThrow { InvoiceServiceException("Issuer not found with NIT: ${request.issuerNit}") }

        // Step 2: Validate customer
        val customer = customerRepository.findByIdentificationTypeAndIdentificationNumber(
            request.customerIdentificationType,
            request.customerIdentificationNumber
        ).orElseThrow {
            InvoiceServiceException(
                "Customer not found: ${request.customerIdentificationType} ${request.customerIdentificationNumber}"
            )
        }

        // Step 3: Generate next invoice number
        val nextNumber = generateNextInvoiceNumber(issuer, request.prefix)

        // Step 4: Validate line items
        validateLineItems(request.lineItems)

        // Step 5: Calculate totals
        val totals = calculateTotals(request.lineItems)

        // Step 6: Validate total matches (if provided)
        if (request.totalAmount != null) {
            val providedTotalCents = convertToCents(request.totalAmount)
            if (providedTotalCents != totals.totalCents) {
                throw InvoiceServiceException(
                    "Total amount mismatch. Calculated: ${convertFromCents(totals.totalCents)}, " +
                    "Provided: ${request.totalAmount}"
                )
            }
        }

        // Step 7: Get current environment
        val environment = environmentRepository.findByName("habilitacion")
            ?: throw InvoiceServiceException("Default environment not found")

        // Step 8: Create invoice entity
        val invoice = Invoice(
            issuer = issuer,
            customer = customer,
            environment = environment,
            prefix = request.prefix,
            number = nextNumber,
            issueDate = request.issueDate ?: LocalDate.now(),
            dueDate = request.dueDate,
            currency = request.currencyCode ?: "COP",
            subtotal = totals.subtotalCents,
            taxAmount = totals.taxAmountCents,
            totalAmount = totals.totalCents,
            status = InvoiceStatus.DRAFT
        )

        val savedInvoice = invoiceRepository.save(invoice)
        logger.info("Invoice created: ${savedInvoice.getInvoiceNumber()}, ID: ${savedInvoice.id}")

        // Step 9: Create line items
        request.lineItems.forEachIndexed { index, lineItemRequest ->
            val lineTotal = convertToCents(lineItemRequest.quantity * lineItemRequest.unitPrice)
            val taxAmount = if (lineItemRequest.taxRate != null) {
                calculateLineTaxCents(lineTotal, lineItemRequest.taxRate)
            } else {
                0L
            }
            
            val lineItem = LineItem(
                invoice = savedInvoice,
                lineNumber = index + 1,
                description = lineItemRequest.description,
                quantity = lineItemRequest.quantity,
                unitPrice = convertToCents(lineItemRequest.unitPrice),
                lineTotal = lineTotal,
                taxRate = lineItemRequest.taxRate,
                taxAmount = taxAmount
            )
            lineItemRepository.save(lineItem)
        }

        logger.info("Created ${request.lineItems.size} line items for invoice ${savedInvoice.id}")

        return savedInvoice
    }

    /**
     * Issue invoice to DIAN
     *
     * This marks the invoice as ready for DIAN submission and starts the async process.
     *
     * @param invoiceId Invoice ID
     * @return Updated invoice with submission started
     */
    fun issueInvoice(invoiceId: UUID): Invoice {
        logger.info("Issuing invoice to DIAN: $invoiceId")

        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { InvoiceServiceException("Invoice not found: $invoiceId") }

        // Validate invoice can be issued
        if (invoice.status != InvoiceStatus.DRAFT && invoice.status != InvoiceStatus.SIGNED) {
            throw InvoiceServiceException(
                "Invoice cannot be issued. Current status: ${invoice.status}"
            )
        }

        // Mark invoice as signed (ready for DIAN)
        invoice.status = InvoiceStatus.SIGNED
        val updatedInvoice = invoiceRepository.save(invoice)

        // Start DIAN submission
        try {
            dianSubmissionService.submitInvoice(updatedInvoice)
            logger.info("DIAN submission started for invoice ${invoice.id}")
        } catch (e: Exception) {
            logger.error("Failed to submit invoice to DIAN", e)
            invoice.status = InvoiceStatus.REJECTED_BY_DIAN
            invoiceRepository.save(invoice)
            throw InvoiceServiceException("Failed to submit to DIAN: ${e.message}", e)
        }

        return updatedInvoice
    }

    /**
     * Get invoice by ID
     */
    fun getInvoice(invoiceId: UUID): Invoice {
        return invoiceRepository.findById(invoiceId)
            .orElseThrow { InvoiceServiceException("Invoice not found: $invoiceId") }
    }

    /**
     * Get invoice with line items
     */
    fun getInvoiceWithLineItems(invoiceId: UUID): InvoiceWithLineItems {
        val invoice = getInvoice(invoiceId)
        val lineItems = lineItemRepository.findByInvoiceOrderByLineNumberAsc(invoice)
        return InvoiceWithLineItems(invoice, lineItems)
    }

    /**
     * List invoices by issuer
     */
    fun listInvoicesByIssuer(issuerNit: String): List<Invoice> {
        val issuer = issuerRepository.findByNit(issuerNit)
            .orElseThrow { InvoiceServiceException("Issuer not found: $issuerNit") }
        return invoiceRepository.findByIssuer(issuer)
    }

    /**
     * Generate next invoice number for issuer and prefix
     */
    private fun generateNextInvoiceNumber(issuer: Issuer, prefix: String?): Long {
        val maxNumber = invoiceRepository.getMaxNumberByIssuerAndPrefix(issuer, prefix)
        val nextNumber = (maxNumber ?: 0L) + 1L
        logger.debug("Next invoice number for issuer ${issuer.nit}, prefix '$prefix': $nextNumber")
        return nextNumber
    }

    /**
     * Validate line items
     */
    private fun validateLineItems(lineItems: List<LineItemRequest>) {
        if (lineItems.isEmpty()) {
            throw InvoiceServiceException("Invoice must have at least one line item")
        }

        lineItems.forEachIndexed { index, item ->
            if (item.quantity <= BigDecimal.ZERO) {
                throw InvoiceServiceException("Line item ${index + 1}: Quantity must be positive")
            }
            if (item.unitPrice < BigDecimal.ZERO) {
                throw InvoiceServiceException("Line item ${index + 1}: Unit price cannot be negative")
            }
            if (item.description.isBlank()) {
                throw InvoiceServiceException("Line item ${index + 1}: Description is required")
            }
        }
    }

    /**
     * Calculate invoice totals from line items
     */
    private fun calculateTotals(lineItems: List<LineItemRequest>): InvoiceTotals {
        var subtotalCents = 0L
        var taxAmountCents = 0L

        lineItems.forEach { item ->
            val lineTotal = convertToCents(item.quantity * item.unitPrice)
            subtotalCents += lineTotal

            if (item.taxRate != null && item.taxRate > BigDecimal.ZERO) {
                val lineTax = calculateLineTaxCents(lineTotal, item.taxRate)
                taxAmountCents += lineTax
            }
        }

        val totalCents = subtotalCents + taxAmountCents

        return InvoiceTotals(
            subtotalCents = subtotalCents,
            taxAmountCents = taxAmountCents,
            totalCents = totalCents
        )
    }

    /**
     * Calculate tax for a line item (returns cents)
     */
    private fun calculateLineTaxCents(lineTotalCents: Long, taxRate: BigDecimal): Long {
        val taxAmount = BigDecimal(lineTotalCents)
            .multiply(taxRate)
            .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
        return taxAmount.toLong()
    }

    /**
     * Convert BigDecimal amount to cents/centavos
     */
    private fun convertToCents(amount: BigDecimal): Long {
        return amount.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /**
     * Convert cents/centavos to BigDecimal amount
     */
    private fun convertFromCents(cents: Long): BigDecimal {
        return BigDecimal(cents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    }
}

/**
 * Invoice with line items
 */
data class InvoiceWithLineItems(
    val invoice: Invoice,
    val lineItems: List<LineItem>
)

/**
 * Calculated invoice totals (in cents)
 */
private data class InvoiceTotals(
    val subtotalCents: Long,
    val taxAmountCents: Long,
    val totalCents: Long
)

/**
 * Custom exception for invoice service errors
 */
class InvoiceServiceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
