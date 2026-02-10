package com.tinto.services.invoice

import com.tinto.domain.billing.DianSubmission
import com.tinto.domain.billing.DianSubmissionStatus
import com.tinto.domain.billing.Invoice
import com.tinto.domain.billing.InvoiceStatus
import com.tinto.repository.DianSubmissionRepository
import com.tinto.repository.InvoiceRepository
import com.tinto.services.dian.DianErrorMapper
import com.tinto.services.dian.DianSubmissionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Invoice Status Service
 *
 * Provides invoice status retrieval including:
 * - Current invoice status
 * - DIAN submission history
 * - Error details with user guidance
 * - Status polling and refresh
 */
@Service
@Transactional(readOnly = true)
class InvoiceStatusService(
    private val invoiceRepository: InvoiceRepository,
    private val submissionRepository: DianSubmissionRepository,
    private val dianSubmissionService: DianSubmissionService,
    private val dianErrorMapper: DianErrorMapper
) {

    private val logger = LoggerFactory.getLogger(InvoiceStatusService::class.java)

    /**
     * Get full invoice status including DIAN submission details and error guidance
     *
     * @param invoiceId Invoice UUID
     * @return InvoiceStatusDetail with status, submissions, and error guidance
     */
    fun getInvoiceStatus(invoiceId: UUID): InvoiceStatusDetail {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { InvoiceServiceException("Invoice not found: $invoiceId") }

        val submissions = submissionRepository.findByInvoice(invoice)
        val latestSubmission = submissions.maxByOrNull { it.createdAt }

        val errorGuidance = latestSubmission?.let { sub ->
            if (sub.status == DianSubmissionStatus.REJECTED || sub.status == DianSubmissionStatus.ERROR) {
                dianErrorMapper.mapToGuidance(sub.errorCode, sub.errorMessage)
            } else {
                null
            }
        }

        return InvoiceStatusDetail(
            invoiceId = invoice.id!!,
            invoiceNumber = invoice.getInvoiceNumber(),
            status = invoice.status,
            cufe = invoice.cufe,
            submissions = submissions.map { it.toSummary() },
            latestSubmission = latestSubmission?.toSummary(),
            errorGuidance = errorGuidance,
            canRetry = latestSubmission?.canRetry() ?: false,
            canIssue = invoice.status == InvoiceStatus.DRAFT || invoice.status == InvoiceStatus.SIGNED
        )
    }

    /**
     * Refresh status by polling DIAN for the latest submission
     *
     * @param invoiceId Invoice UUID
     * @return Updated InvoiceStatusDetail
     */
    @Transactional
    fun refreshStatus(invoiceId: UUID): InvoiceStatusDetail {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { InvoiceServiceException("Invoice not found: $invoiceId") }

        val latestSubmission = submissionRepository.findLatestByInvoice(invoice)

        if (latestSubmission != null && !latestSubmission.isFinal()) {
            logger.info("Polling DIAN for invoice $invoiceId, trackId: ${latestSubmission.trackId}")
            dianSubmissionService.checkStatus(latestSubmission)
        }

        return getInvoiceStatus(invoiceId)
    }

    /**
     * Get submission history for an invoice
     */
    fun getSubmissionHistory(invoiceId: UUID): List<SubmissionSummary> {
        val invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow { InvoiceServiceException("Invoice not found: $invoiceId") }

        return submissionRepository.findByInvoice(invoice).map { it.toSummary() }
    }

    private fun DianSubmission.toSummary(): SubmissionSummary {
        return SubmissionSummary(
            id = this.id!!,
            trackId = this.trackId,
            status = this.status,
            statusMessage = this.getStatusMessage(),
            errorCode = this.errorCode,
            errorMessage = this.errorMessage,
            submittedAt = this.submittedAt?.toString(),
            processedAt = this.processedAt?.toString(),
            createdAt = this.createdAt.toString()
        )
    }
}

/**
 * Full invoice status detail
 */
data class InvoiceStatusDetail(
    val invoiceId: UUID,
    val invoiceNumber: String,
    val status: InvoiceStatus,
    val cufe: String?,
    val submissions: List<SubmissionSummary>,
    val latestSubmission: SubmissionSummary?,
    val errorGuidance: DianErrorMapper.ErrorGuidance?,
    val canRetry: Boolean,
    val canIssue: Boolean
)

/**
 * Summary of a DIAN submission
 */
data class SubmissionSummary(
    val id: UUID,
    val trackId: String?,
    val status: DianSubmissionStatus,
    val statusMessage: String,
    val errorCode: String?,
    val errorMessage: String?,
    val submittedAt: String?,
    val processedAt: String?,
    val createdAt: String
)
