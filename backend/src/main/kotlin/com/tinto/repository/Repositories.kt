package com.tinto.repository

import com.tinto.domain.auth.ApiKey
import com.tinto.domain.auth.MasterAccessKey
import com.tinto.domain.auth.RequestSignature
import com.tinto.domain.billing.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * Repository for Invoice entities
 */
@Repository
interface InvoiceRepository : JpaRepository<Invoice, UUID> {
    
    /**
     * Find invoice by issuer, prefix, and number
     */
    fun findByIssuerAndPrefixAndNumber(
        issuer: Issuer,
        prefix: String?,
        number: Long
    ): Optional<Invoice>

    /**
     * Find invoices by issuer
     */
    fun findByIssuer(issuer: Issuer): List<Invoice>

    /**
     * Find invoices by customer
     */
    fun findByCustomer(customer: Customer): List<Invoice>

    /**
     * Find invoices by status
     */
    fun findByStatus(status: InvoiceStatus): List<Invoice>

    /**
     * Find invoices by issuer and status
     */
    fun findByIssuerAndStatus(issuer: Issuer, status: InvoiceStatus): List<Invoice>

    /**
     * Find invoices by issue date range
     */
    fun findByIssueDateBetween(startDate: LocalDate, endDate: LocalDate): List<Invoice>

    /**
     * Find invoices by CUFE
     */
    fun findByCufe(cufe: String): Optional<Invoice>

    /**
     * Count invoices by issuer and prefix
     * Useful for generating next invoice number
     */
    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.issuer = :issuer AND i.prefix = :prefix")
    fun countByIssuerAndPrefix(
        @Param("issuer") issuer: Issuer,
        @Param("prefix") prefix: String?
    ): Long

    /**
     * Get the highest invoice number for issuer and prefix
     */
    @Query("SELECT MAX(i.number) FROM Invoice i WHERE i.issuer = :issuer AND i.prefix = :prefix")
    fun getMaxNumberByIssuerAndPrefix(
        @Param("issuer") issuer: Issuer,
        @Param("prefix") prefix: String?
    ): Long?

    /**
     * Find invoices that need DIAN submission
     * (status = SIGNED and no successful submission)
     */
    @Query("""
        SELECT i FROM Invoice i 
        WHERE i.status = 'SIGNED' 
        AND i.cufe IS NOT NULL
        AND NOT EXISTS (
            SELECT d FROM DianSubmission d 
            WHERE d.invoice = i 
            AND d.status = 'ACCEPTED'
        )
    """)
    fun findInvoicesNeedingDianSubmission(): List<Invoice>
}

/**
 * Repository for Issuer entities
 */
@Repository
interface IssuerRepository : JpaRepository<Issuer, UUID> {
    
    /**
     * Find issuer by NIT
     */
    fun findByNit(nit: String): Optional<Issuer>

    /**
     * Check if issuer with NIT exists
     */
    fun existsByNit(nit: String): Boolean

    /**
     * Find issuers with valid certificates
     */
    @Query("""
        SELECT i FROM Issuer i 
        WHERE i.certificateData IS NOT NULL 
        AND i.certificateExpiresAt > CURRENT_TIMESTAMP
    """)
    fun findIssuersWithValidCertificates(): List<Issuer>
}

/**
 * Repository for Customer entities
 */
@Repository
interface CustomerRepository : JpaRepository<Customer, UUID> {
    
    /**
     * Find customer by identification type and number
     */
    fun findByIdentificationTypeAndIdentificationNumber(
        identificationType: String,
        identificationNumber: String
    ): Optional<Customer>

    /**
     * Check if customer exists
     */
    fun existsByIdentificationTypeAndIdentificationNumber(
        identificationType: String,
        identificationNumber: String
    ): Boolean

    /**
     * Find customers by name (case-insensitive search)
     */
    @Query("SELECT c FROM Customer c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    fun searchByName(@Param("name") name: String): List<Customer>
}

/**
 * Repository for LineItem entities
 */
@Repository
interface LineItemRepository : JpaRepository<LineItem, UUID> {
    
    /**
     * Find line items by invoice
     */
    fun findByInvoice(invoice: Invoice): List<LineItem>

    /**
     * Find line items by invoice, ordered by line number
     */
    fun findByInvoiceOrderByLineNumberAsc(invoice: Invoice): List<LineItem>

    /**
     * Delete all line items for an invoice
     */
    fun deleteByInvoice(invoice: Invoice)
}

/**
 * Repository for DianSubmission entities
 */
@Repository
interface DianSubmissionRepository : JpaRepository<DianSubmission, UUID> {
    
    /**
     * Find submissions by invoice
     */
    fun findByInvoice(invoice: Invoice): List<DianSubmission>

    /**
     * Find submission by TrackId
     */
    fun findByTrackId(trackId: String): Optional<DianSubmission>

    /**
     * Find submissions by status
     */
    fun findByStatus(status: DianSubmissionStatus): List<DianSubmission>

    /**
     * Find submissions that are still processing
     * (SUBMITTED or PROCESSING status)
     */
    @Query("""
        SELECT d FROM DianSubmission d 
        WHERE d.status IN ('SUBMITTED', 'PROCESSING')
        ORDER BY d.submittedAt ASC
    """)
    fun findProcessingSubmissions(): List<DianSubmission>

    /**
     * Find the latest submission for an invoice
     */
    @Query("""
        SELECT d FROM DianSubmission d 
        WHERE d.invoice = :invoice 
        ORDER BY d.createdAt DESC
        LIMIT 1
    """)
    fun findLatestByInvoice(@Param("invoice") invoice: Invoice): DianSubmission?

    /**
     * Check if invoice has a successful submission
     */
    @Query("""
        SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END
        FROM DianSubmission d 
        WHERE d.invoice = :invoice 
        AND d.status = 'ACCEPTED'
    """)
    fun hasSuccessfulSubmission(@Param("invoice") invoice: Invoice): Boolean
}

/**
 * Repository for Environment entities
 */
@Repository
interface EnvironmentRepository : JpaRepository<Environment, UUID> {
    
    /**
     * Find environment by name
     */
    fun findByName(name: String): Environment?

    /**
     * Get production environment
     */
    @Query("SELECT e FROM Environment e WHERE e.isProduction = true")
    fun findProductionEnvironment(): Optional<Environment>

    /**
     * Get non-production environments
     */
    @Query("SELECT e FROM Environment e WHERE e.isProduction = false")
    fun findNonProductionEnvironments(): List<Environment>
}

/**
 * Repository for MasterAccessKey entities
 */
@Repository
interface MasterAccessKeyRepository : JpaRepository<MasterAccessKey, UUID> {

    fun findByKeyHash(keyHash: String): MasterAccessKey?

    fun findByIsActiveTrue(): List<MasterAccessKey>
}

/**
 * Repository for ApiKey entities
 */
@Repository
interface ApiKeyRepository : JpaRepository<ApiKey, UUID> {

    fun findByKeyHash(keyHash: String): ApiKey?

    fun findByIsActiveTrue(): List<ApiKey>

    fun findByName(name: String): ApiKey?
}

/**
 * Repository for RequestSignature entities
 */
@Repository
interface RequestSignatureRepository : JpaRepository<RequestSignature, UUID> {

    /**
     * Check for replay attacks — does this exact signature + timestamp already exist?
     */
    fun existsBySignatureHashAndRequestTimestamp(
        signatureHash: String,
        requestTimestamp: Instant
    ): Boolean

    /**
     * Find signatures by API key (for auditing)
     */
    fun findByApiKey(apiKey: ApiKey): List<RequestSignature>
}
