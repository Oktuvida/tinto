package com.tinto.domain.billing

import jakarta.persistence.*
import java.time.Instant
import java.util.*

/**
 * DIAN submission status enum
 */
enum class DianSubmissionStatus {
    PENDING,
    SUBMITTED,
    PROCESSING,
    ACCEPTED,
    REJECTED,
    ERROR
}

/**
 * DianSubmission entity tracking invoice submission to DIAN.
 *
 * This entity tracks the async submission process to DIAN,
 * including the TrackId for polling and the response data.
 */
@Entity
@Table(
    name = "dian_submissions",
    indexes = [
        Index(name = "idx_dian_submissions_invoice", columnList = "invoice_id"),
        Index(name = "idx_dian_submissions_status", columnList = "status")
    ]
)
data class DianSubmission(
    @Id
    @GeneratedValue
    val id: UUID? = null,

    /**
     * Invoice being submitted
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    val invoice: Invoice,

    /**
     * Environment used for submission
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false)
    val environment: Environment,

    /**
     * TrackId returned by DIAN SendBillAsync
     * Used to poll for status
     */
    @Column(name = "track_id", length = 100)
    var trackId: String? = null,

    /**
     * Current submission status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: DianSubmissionStatus = DianSubmissionStatus.PENDING,

    /**
     * Encrypted ZIP file sent to DIAN
     * Contains the signed XML invoice
     */
    @Column(name = "zip_file_encrypted", columnDefinition = "TEXT")
    var zipFileEncrypted: String? = null,

    /**
     * Encrypted DIAN ApplicationResponse XML
     * Contains the final response from DIAN
     */
    @Column(name = "dian_response_encrypted", columnDefinition = "TEXT")
    var dianResponseEncrypted: String? = null,

    /**
     * Error code from DIAN (if rejected)
     */
    @Column(name = "error_code", length = 50)
    var errorCode: String? = null,

    /**
     * Error message from DIAN (if rejected)
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    /**
     * Timestamp when submitted to DIAN
     */
    @Column(name = "submitted_at")
    var submittedAt: Instant? = null,

    /**
     * Timestamp when DIAN finished processing
     */
    @Column(name = "processed_at")
    var processedAt: Instant? = null,

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
     * Mark as submitted with TrackId
     */
    fun markSubmitted(trackId: String) {
        this.trackId = trackId
        this.status = DianSubmissionStatus.SUBMITTED
        this.submittedAt = Instant.now()
    }

    /**
     * Mark as processing
     */
    fun markProcessing() {
        this.status = DianSubmissionStatus.PROCESSING
    }

    /**
     * Mark as accepted by DIAN
     */
    fun markAccepted(dianResponse: String) {
        this.status = DianSubmissionStatus.ACCEPTED
        this.dianResponseEncrypted = dianResponse
        this.processedAt = Instant.now()
    }

    /**
     * Mark as rejected by DIAN
     */
    fun markRejected(errorCode: String, errorMessage: String, dianResponse: String? = null) {
        this.status = DianSubmissionStatus.REJECTED
        this.errorCode = errorCode
        this.errorMessage = errorMessage
        this.dianResponseEncrypted = dianResponse
        this.processedAt = Instant.now()
    }

    /**
     * Mark as error (technical failure)
     */
    fun markError(errorMessage: String) {
        this.status = DianSubmissionStatus.ERROR
        this.errorMessage = errorMessage
        this.processedAt = Instant.now()
    }

    /**
     * Check if submission is in a final state
     */
    fun isFinal(): Boolean {
        return status in listOf(
            DianSubmissionStatus.ACCEPTED,
            DianSubmissionStatus.REJECTED,
            DianSubmissionStatus.ERROR
        )
    }

    /**
     * Check if submission is successful
     */
    fun isSuccessful(): Boolean {
        return status == DianSubmissionStatus.ACCEPTED
    }

    /**
     * Check if submission can be retried
     */
    fun canRetry(): Boolean {
        return status in listOf(
            DianSubmissionStatus.ERROR,
            DianSubmissionStatus.REJECTED
        )
    }

    /**
     * Get human-readable status message
     */
    fun getStatusMessage(): String {
        return when (status) {
            DianSubmissionStatus.PENDING -> "Pending submission to DIAN"
            DianSubmissionStatus.SUBMITTED -> "Submitted to DIAN, waiting for processing (TrackId: $trackId)"
            DianSubmissionStatus.PROCESSING -> "Being processed by DIAN"
            DianSubmissionStatus.ACCEPTED -> "Accepted by DIAN"
            DianSubmissionStatus.REJECTED -> "Rejected by DIAN: $errorMessage"
            DianSubmissionStatus.ERROR -> "Error during submission: $errorMessage"
        }
    }
}
