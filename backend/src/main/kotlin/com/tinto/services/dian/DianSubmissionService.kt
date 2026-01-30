package com.tinto.services.dian

import com.tinto.domain.billing.DianSubmission
import com.tinto.domain.billing.DianSubmissionStatus
import com.tinto.domain.billing.Invoice
import com.tinto.repository.DianSubmissionRepository
import com.tinto.repository.EnvironmentRepository
import com.tinto.security.ConfigurationService
import com.tinto.security.EncryptionService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.LocalDate

/**
 * DIAN Submission Service - Orchestrates the full invoice submission flow
 *
 * This service coordinates the complete process of submitting an invoice to DIAN:
 * 1. Generate CUFE hash
 * 2. Build UBL 2.1 XML
 * 3. Sign XML with XAdES-EPES
 * 4. Package into ZIP
 * 5. Send to DIAN via SOAP
 * 6. Track submission status
 * 7. Process DIAN response
 *
 * The process is asynchronous:
 * - SendBillAsync returns a TrackId
 * - GetStatusZip is polled to check status
 * - Final response contains ApplicationResponse XML
 */
@Service
@Transactional
class DianSubmissionService(
    private val cufeCalculator: CufeCalculator,
    private val ublXmlBuilder: UblXmlBuilder,
    private val xmlSigner: XmlSigner,
    private val zipPackager: ZipPackager,
    private val dianSoapClient: DianSoapClient,
    private val encryptionService: EncryptionService,
    private val configService: ConfigurationService,
    private val submissionRepository: DianSubmissionRepository,
    private val environmentRepository: EnvironmentRepository
) {

    private val logger = LoggerFactory.getLogger(DianSubmissionService::class.java)

    @Value("\${dian.certificate.path:#{null}}")
    private val certificatePath: String? = null

    @Value("\${dian.certificate.password:#{null}}")
    private val certificatePassword: String? = null

    @Value("\${dian.certificate.alias:#{null}}")
    private val certificateAlias: String? = null

    @Value("\${dian.technical.key}")
    private lateinit var technicalKey: String

    @Value("\${dian.software.id}")
    private lateinit var softwareId: String

    @Value("\${dian.software.pin}")
    private lateinit var softwarePin: String

    @Value("\${tinto.encryption.password}")
    private lateinit var encryptionPassword: String

    /**
     * Submit invoice to DIAN
     *
     * This is the main entry point for submitting invoices.
     * The process is idempotent - if a submission already exists for this invoice,
     * it will return the existing submission instead of creating a new one.
     *
     * @param invoice Invoice to submit
     * @param privateKey Private key for signing (optional if using keystore)
     * @param certificate X.509 certificate for signing (optional if using keystore)
     * @return DianSubmission entity tracking the submission
     */
    fun submitInvoice(
        invoice: Invoice,
        privateKey: PrivateKey? = null,
        certificate: X509Certificate? = null
    ): DianSubmission {
        logger.info("Starting DIAN submission for invoice ID: ${invoice.id}")

        // Check if there's already a pending or successful submission
        val existingSubmission = submissionRepository.findLatestByInvoice(invoice)
        if (existingSubmission != null && !existingSubmission.canRetry()) {
            logger.info("Found existing submission for invoice ${invoice.id}: ${existingSubmission.status}")
            return existingSubmission
        }

        // Get current environment
        val environment = environmentRepository.findByName(configService.getDianConfig().environment)
            ?: throw DianSubmissionException("Environment not found: ${configService.getDianConfig().environment}")

        // Create new submission record
        val submission = DianSubmission(
            invoice = invoice,
            environment = environment,
            status = DianSubmissionStatus.PENDING
        )
        val savedSubmission = submissionRepository.save(submission)

        try {
            // Step 1: Calculate CUFE
            logger.debug("Calculating CUFE for invoice ${invoice.id}")
            val isProduction = configService.isProduction()
            val cufe = cufeCalculator.calculateCufe(invoice, technicalKey, isProduction)
            logger.info("CUFE calculated: $cufe")

            // Step 2: Build UBL XML
            logger.debug("Building UBL XML for invoice ${invoice.id}")
            val ublXml = ublXmlBuilder.buildInvoiceXml(invoice, cufe, softwareId, softwarePin)
            logger.debug("UBL XML built: ${ublXml.length} characters")

            // Step 3: Sign XML
            logger.debug("Signing XML for invoice ${invoice.id}")
            val signedXml = signXml(ublXml, privateKey, certificate)
            logger.info("XML signed successfully")

            // Step 4: Package to ZIP
            logger.debug("Packaging to ZIP for invoice ${invoice.id}")
            val packagedInvoice = zipPackager.packageToZip(
                signedXml = signedXml,
                nit = invoice.issuer.nit,
                year = LocalDate.now().year,
                sequence = invoice.number.toString(),
                prefix = invoice.prefix ?: "",
                number = invoice.number.toString()
            )
            logger.info("ZIP packaged: ${packagedInvoice.zipFileName}")

            // Encrypt and store ZIP
            val encryptedZip = encryptionService.encryptWithPassword(packagedInvoice.zipBase64, encryptionPassword)
            savedSubmission.zipFileEncrypted = encryptedZip

            // Step 5: Send to DIAN
            logger.info("Sending invoice ${invoice.id} to DIAN")
            val response = dianSoapClient.sendBillAsync(
                zipBase64 = packagedInvoice.zipBase64,
                nit = invoice.issuer.nit
            )

            // Update submission with TrackId
            if (response.success && response.trackId != null) {
                savedSubmission.markSubmitted(response.trackId)
                logger.info("Invoice submitted successfully. TrackId: ${response.trackId}")
            } else {
                savedSubmission.markError(
                    response.errorMessage ?: "Unknown error from DIAN"
                )
                logger.error("DIAN rejected submission: ${response.errorMessage}")
            }

            return submissionRepository.save(savedSubmission)

        } catch (e: Exception) {
            logger.error("Failed to submit invoice ${invoice.id} to DIAN", e)
            savedSubmission.markError("Submission failed: ${e.message}")
            submissionRepository.save(savedSubmission)
            throw DianSubmissionException("Failed to submit invoice: ${e.message}", e)
        }
    }

    /**
     * Check submission status and update database
     *
     * This method polls DIAN for the status of a submitted invoice.
     * It should be called periodically until the submission reaches a final state.
     *
     * @param submission DianSubmission to check
     * @return Updated DianSubmission
     */
    fun checkStatus(submission: DianSubmission): DianSubmission {
        if (submission.trackId == null) {
            logger.warn("Cannot check status: submission ${submission.id} has no TrackId")
            return submission
        }

        if (submission.isFinal()) {
            logger.debug("Submission ${submission.id} is already in final state: ${submission.status}")
            return submission
        }

        logger.info("Checking status for submission ${submission.id}, TrackId: ${submission.trackId}")

        try {
            // Query DIAN for status
            val statusResponse = dianSoapClient.getStatusZip(submission.trackId!!)

            // Update submission based on response
            when (statusResponse.statusCode) {
                "00" -> {
                    // Processing
                    if (submission.status != DianSubmissionStatus.PROCESSING) {
                        submission.markProcessing()
                        logger.info("Submission ${submission.id} is being processed by DIAN")
                    }
                }
                "02" -> {
                    // Accepted
                    val encryptedResponse = encryptionService.encryptWithPassword(statusResponse.zipBase64, encryptionPassword)
                    submission.markAccepted(encryptedResponse)
                    logger.info("Submission ${submission.id} accepted by DIAN")
                }
                "04" -> {
                    // Rejected
                    val encryptedResponse = encryptionService.encryptWithPassword(statusResponse.zipBase64, encryptionPassword)
                    submission.markRejected(
                        errorCode = statusResponse.statusCode,
                        errorMessage = statusResponse.statusMessage ?: "Rejected by DIAN",
                        dianResponse = encryptedResponse
                    )
                    logger.warn("Submission ${submission.id} rejected by DIAN: ${statusResponse.statusMessage}")
                }
                else -> {
                    // Unknown status
                    logger.warn("Unknown status code from DIAN: ${statusResponse.statusCode}")
                    submission.markError("Unknown status: ${statusResponse.statusMessage}")
                }
            }

            return submissionRepository.save(submission)

        } catch (e: Exception) {
            logger.error("Failed to check status for submission ${submission.id}", e)
            throw DianSubmissionException("Failed to check status: ${e.message}", e)
        }
    }

    /**
     * Get decrypted ApplicationResponse XML from accepted submission
     *
     * @param submission DianSubmission that was accepted
     * @return ApplicationResponse XML content
     */
    fun getApplicationResponse(submission: DianSubmission): String? {
        if (submission.dianResponseEncrypted == null) {
            return null
        }

        try {
            // Decrypt response
            val decryptedZipBase64 = encryptionService.decryptWithPassword(submission.dianResponseEncrypted!!, encryptionPassword)

            // Extract XML from ZIP
            return zipPackager.extractXmlFromZip(decryptedZipBase64)

        } catch (e: Exception) {
            logger.error("Failed to extract ApplicationResponse for submission ${submission.id}", e)
            throw DianSubmissionException("Failed to extract ApplicationResponse: ${e.message}", e)
        }
    }

    /**
     * Poll DIAN status until submission reaches final state
     *
     * This is a blocking operation that will poll DIAN every few seconds
     * until the invoice is accepted, rejected, or an error occurs.
     *
     * WARNING: This can take several minutes. Use with caution in production.
     * Consider using a background job queue instead.
     *
     * @param submission DianSubmission to poll
     * @param maxAttempts Maximum polling attempts (default: 20)
     * @param delaySeconds Delay between attempts in seconds (default: 5)
     * @return Final DianSubmission state
     */
    fun pollUntilFinal(
        submission: DianSubmission,
        maxAttempts: Int = 20,
        delaySeconds: Long = 5
    ): DianSubmission {
        logger.info("Starting polling for submission ${submission.id} (max $maxAttempts attempts)")

        var currentSubmission = submission
        var attempts = 0

        while (!currentSubmission.isFinal() && attempts < maxAttempts) {
            attempts++
            logger.debug("Poll attempt $attempts/$maxAttempts for submission ${submission.id}")

            try {
                // Wait before polling (except first attempt)
                if (attempts > 1) {
                    Thread.sleep(delaySeconds * 1000)
                }

                currentSubmission = checkStatus(currentSubmission)

            } catch (e: Exception) {
                logger.error("Error during polling attempt $attempts", e)
                if (attempts >= maxAttempts) {
                    throw e
                }
            }
        }

        if (!currentSubmission.isFinal()) {
            logger.warn("Polling timed out after $maxAttempts attempts for submission ${submission.id}")
        } else {
            logger.info("Polling completed for submission ${submission.id}: ${currentSubmission.status}")
        }

        return currentSubmission
    }

    /**
     * Sign XML with certificate
     *
     * Uses provided private key/certificate or loads from keystore configuration
     */
    private fun signXml(
        xmlContent: String,
        privateKey: PrivateKey?,
        certificate: X509Certificate?
    ): String {
        return if (privateKey != null && certificate != null) {
            // Use provided key and certificate
            xmlSigner.signXml(xmlContent, privateKey, certificate)
        } else if (certificatePath != null && certificatePassword != null && certificateAlias != null) {
            // Load from keystore
            xmlSigner.signXml(
                xmlContent = xmlContent,
                keystorePath = certificatePath,
                keystorePassword = certificatePassword,
                keyAlias = certificateAlias,
                keyPassword = certificatePassword // Assuming same password for key
            )
        } else {
            throw DianSubmissionException(
                "No certificate provided and keystore not configured. " +
                "Set dian.certificate.* properties or provide privateKey and certificate"
            )
        }
    }
}

/**
 * Custom exception for DIAN submission errors
 */
class DianSubmissionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
