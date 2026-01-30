package com.tinto.services.dian

import com.tinto.domain.billing.Invoice
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.format.DateTimeFormatter

/**
 * CUFE (Código Único de Factura Electrónica) calculator.
 *
 * CUFE is a SHA-384 hash that uniquely identifies an invoice.
 * It must be calculated BEFORE generating the XML, as it's included in the XML.
 *
 * Algorithm: SHA-384
 * Input format (concatenated without spaces):
 * NumFac + FecFac + HorFac + ValFac + CodImp1 + ValImp1 + CodImp2 + ValImp2 + CodImp3 + ValImp3 + 
 * ValTot + NitOfe + DocAdq + TipAdq + ClaveTec + TipoAmbiente
 *
 * Notes:
 * - All numeric values WITHOUT thousand separators
 * - Decimal point with EXACTLY 2 decimal places
 * - If a tax doesn't apply, it's not concatenated
 * 
 * Reference: TECH_SPECS.md Section 2.1
 */
@Service
class CufeCalculator {

    private val logger = LoggerFactory.getLogger(CufeCalculator::class.java)

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss")
        
        // Environment codes
        private const val ENV_PRODUCTION = "1"
        private const val ENV_TEST = "2"
        
        // Tax codes for DIAN
        private const val TAX_CODE_IVA = "01"
        private const val TAX_CODE_CONSUMO = "04"
        private const val TAX_CODE_ICA = "03"
    }

    /**
     * Calculate CUFE for an invoice
     *
     * @param invoice Invoice entity
     * @param technicalKey Technical key from DIAN (specific to environment and numbering range)
     * @param isProduction true if production environment, false for test/habilitacion
     * @return CUFE as SHA-384 hash (96 characters hex)
     */
    fun calculateCufe(
        invoice: Invoice,
        technicalKey: String,
        isProduction: Boolean = false
    ): String {
        logger.debug("Calculating CUFE for invoice ${invoice.getInvoiceNumber()}")

        // Build concatenation string according to DIAN spec
        val cufeInput = buildCufeInput(invoice, technicalKey, isProduction)
        
        logger.debug("CUFE input string: $cufeInput")

        // Calculate SHA-384 hash
        val cufe = calculateSha384(cufeInput)
        
        logger.info("CUFE calculated for invoice ${invoice.getInvoiceNumber()}: $cufe")
        
        return cufe
    }

    /**
     * Build CUFE input string according to DIAN specification
     */
    private fun buildCufeInput(invoice: Invoice, technicalKey: String, isProduction: Boolean): String {
        val parts = mutableListOf<String>()

        // 1. NumFac - Invoice number (prefix + number)
        parts.add(invoice.getInvoiceNumber())

        // 2. FecFac - Issue date (yyyyMMdd)
        parts.add(invoice.issueDate.format(DATE_FORMATTER))

        // 3. HorFac - Issue time (HHmmss) - TODO: Store actual time in invoice
        parts.add("120000") // Default: 12:00:00

        // 4. ValFac - Invoice total before tax (2 decimal places, no thousand separators)
        parts.add(formatAmount(invoice.subtotal))

        // 5-7. Tax 1 (IVA) - Code, Value, Base
        if (invoice.taxAmount > 0) {
            parts.add(TAX_CODE_IVA) // CodImp1
            parts.add(formatAmount(invoice.taxAmount)) // ValImp1
            parts.add(formatAmount(invoice.subtotal)) // ValImp1 base gravable
        }

        // 8-10. Tax 2 (Consumo) - Not implemented, skip if not present
        // 11-13. Tax 3 (ICA) - Not implemented, skip if not present

        // Total (including all taxes)
        parts.add(formatAmount(invoice.totalAmount))

        // NitOfe - Issuer NIT (without verification digit, just the number)
        parts.add(invoice.issuer.nit.replace("-", ""))

        // TipAdq - Customer document type
        parts.add(getCustomerTypeCode(invoice.customer.identificationType))

        // DocAdq - Customer identification number
        parts.add(invoice.customer.identificationNumber)

        // ClaveTec - Technical key from DIAN
        parts.add(technicalKey)

        // TipoAmbiente - Environment (1=Production, 2=Test)
        parts.add(if (isProduction) ENV_PRODUCTION else ENV_TEST)

        return parts.joinToString("")
    }

    /**
     * Calculate SHA-384 hash of input string
     *
     * @param input Input string
     * @return Hex-encoded SHA-384 hash (96 characters)
     */
    private fun calculateSha384(input: String): String {
        val digest = MessageDigest.getInstance("SHA-384")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Format amount from cents to string with 2 decimal places
     * No thousand separators, always 2 decimals
     *
     * Example: 123456 cents -> "1234.56"
     */
    private fun formatAmount(amountCents: Long): String {
        val wholePart = amountCents / 100
        val decimalPart = amountCents % 100
        return String.format("%d.%02d", wholePart, decimalPart)
    }

    /**
     * Get DIAN customer type code
     *
     * Type codes:
     * - 31: NIT
     * - 13: Cédula de Ciudadanía
     * - 22: Cédula de Extranjería
     * - 41: Pasaporte
     * - 42: Documento de identificación extranjero
     * - 50: NIT de otro país
     */
    private fun getCustomerTypeCode(identificationType: String): String {
        return when (identificationType.uppercase()) {
            "NIT" -> "31"
            "CC" -> "13"
            "CE" -> "22"
            "PA", "PASSPORT" -> "41"
            "DIE" -> "42"
            "NIT_EXTRANJERO" -> "50"
            else -> "31" // Default to NIT
        }
    }

    /**
     * Validate CUFE format
     *
     * @param cufe CUFE string to validate
     * @return true if valid SHA-384 hash format
     */
    fun isValidCufe(cufe: String): Boolean {
        // SHA-384 produces 96 hex characters
        return cufe.matches(Regex("^[0-9a-f]{96}$"))
    }

    /**
     * Verify that a CUFE matches an invoice
     *
     * @param invoice Invoice to check
     * @param cufe CUFE to verify
     * @param technicalKey Technical key used
     * @param isProduction Environment used
     * @return true if CUFE matches
     */
    fun verifyCufe(
        invoice: Invoice,
        cufe: String,
        technicalKey: String,
        isProduction: Boolean
    ): Boolean {
        val calculatedCufe = calculateCufe(invoice, technicalKey, isProduction)
        return calculatedCufe.equals(cufe, ignoreCase = true)
    }
}
