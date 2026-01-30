package com.tinto.services.dian

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * ZIP Packaging Service for DIAN Invoice Submission
 *
 * DIAN requires XML invoices to be:
 * 1. Compressed in ZIP format
 * 2. Encoded as Base64 for SOAP transport
 *
 * ZIP Naming Convention:
 * z{NIT_SIN_DV}{CODIGO_DOC}{AÑO}{CONSECUTIVO}.zip
 *
 * Example: z9001234560120260001.zip
 * - 900123456: NIT without check digit
 * - 01: Document code (01 = Invoice)
 * - 2026: Year
 * - 0001: Invoice sequence number
 *
 * XML File Naming Convention (inside ZIP):
 * face_f{PREFIJO}{CONSECUTIVO}.xml
 *
 * Example: face_fSPOS0001.xml
 * - SPOS: Invoice prefix
 * - 0001: Invoice number
 */
@Service
class ZipPackager {

    private val logger = LoggerFactory.getLogger(ZipPackager::class.java)

    companion object {
        // Document type codes
        const val DOCUMENT_CODE_INVOICE = "01"
        const val DOCUMENT_CODE_CREDIT_NOTE = "91"
        const val DOCUMENT_CODE_DEBIT_NOTE = "92"
        
        // File naming patterns
        private const val ZIP_NAME_PATTERN = "z%s%s%s%s.zip" // NIT, DocCode, Year, Sequence
        private const val XML_NAME_PATTERN = "face_f%s%s.xml" // Prefix, Number
    }

    /**
     * Package signed XML into ZIP file and encode as Base64
     *
     * @param signedXml Signed XML invoice content
     * @param nit Issuer's NIT (without check digit)
     * @param documentCode Document type code (01 = Invoice)
     * @param year Invoice year (e.g., 2026)
     * @param sequence Invoice sequence number
     * @param prefix Invoice prefix (e.g., "SPOS")
     * @param number Invoice number (e.g., "0001")
     * @return Base64-encoded ZIP file
     */
    fun packageToZip(
        signedXml: String,
        nit: String,
        documentCode: String = DOCUMENT_CODE_INVOICE,
        year: Int,
        sequence: String,
        prefix: String,
        number: String
    ): PackagedInvoice {
        logger.info("Packaging invoice to ZIP: NIT=$nit, Year=$year, Seq=$sequence")
        
        try {
            // Generate file names
            val zipFileName = generateZipFileName(nit, documentCode, year, sequence)
            val xmlFileName = generateXmlFileName(prefix, number)
            
            logger.debug("ZIP file name: $zipFileName")
            logger.debug("XML file name: $xmlFileName")
            
            // Create ZIP in memory
            val zipBytes = createZip(xmlFileName, signedXml)
            
            // Encode to Base64
            val base64Zip = Base64.getEncoder().encodeToString(zipBytes)
            
            logger.info("ZIP package created successfully: ${zipBytes.size} bytes")
            
            return PackagedInvoice(
                zipFileName = zipFileName,
                xmlFileName = xmlFileName,
                zipBase64 = base64Zip,
                zipSizeBytes = zipBytes.size
            )
            
        } catch (e: Exception) {
            logger.error("Failed to package invoice to ZIP", e)
            throw ZipPackagingException("Failed to package invoice: ${e.message}", e)
        }
    }

    /**
     * Create ZIP file with XML content
     *
     * @param fileName Name of the XML file inside ZIP
     * @param xmlContent XML content
     * @return ZIP file as byte array
     */
    private fun createZip(fileName: String, xmlContent: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        
        ZipOutputStream(outputStream).use { zipStream ->
            // Create ZIP entry for XML file
            val zipEntry = ZipEntry(fileName)
            zipEntry.time = System.currentTimeMillis()
            
            zipStream.putNextEntry(zipEntry)
            zipStream.write(xmlContent.toByteArray(Charsets.UTF_8))
            zipStream.closeEntry()
        }
        
        return outputStream.toByteArray()
    }

    /**
     * Generate ZIP file name according to DIAN convention
     *
     * Format: z{NIT}{DocCode}{Year}{Sequence}.zip
     *
     * @param nit Issuer's NIT without check digit (9 digits)
     * @param documentCode Document type code (2 digits)
     * @param year Invoice year (4 digits)
     * @param sequence Invoice sequence (padded to 10 digits)
     * @return ZIP file name
     */
    private fun generateZipFileName(
        nit: String,
        documentCode: String,
        year: Int,
        sequence: String
    ): String {
        // Remove any non-numeric characters from NIT
        val cleanNit = nit.replace(Regex("[^0-9]"), "")
        
        // Pad sequence to 10 digits
        val paddedSequence = sequence.padStart(10, '0')
        
        return ZIP_NAME_PATTERN.format(cleanNit, documentCode, year, paddedSequence)
    }

    /**
     * Generate XML file name according to DIAN convention
     *
     * Format: face_f{Prefix}{Number}.xml
     *
     * @param prefix Invoice prefix (e.g., "SPOS")
     * @param number Invoice number (e.g., "0001")
     * @return XML file name
     */
    private fun generateXmlFileName(prefix: String, number: String): String {
        return XML_NAME_PATTERN.format(prefix, number)
    }

    /**
     * Extract XML content from Base64-encoded ZIP
     *
     * Useful for processing DIAN's ApplicationResponse ZIP
     *
     * @param zipBase64 Base64-encoded ZIP file
     * @return XML content as String
     */
    fun extractXmlFromZip(zipBase64: String): String {
        logger.debug("Extracting XML from Base64 ZIP")
        
        try {
            // Decode Base64
            val zipBytes = Base64.getDecoder().decode(zipBase64)
            
            // Extract first XML file from ZIP
            ByteArrayInputStream(zipBytes).use { byteStream ->
                ZipInputStream(byteStream).use { zipStream ->
                    var zipEntry = zipStream.nextEntry
                    
                    while (zipEntry != null) {
                        if (zipEntry.name.endsWith(".xml", ignoreCase = true)) {
                            val xmlBytes = zipStream.readBytes()
                            return String(xmlBytes, Charsets.UTF_8)
                        }
                        zipEntry = zipStream.nextEntry
                    }
                    
                    throw ZipPackagingException("No XML file found in ZIP")
                }
            }
            
        } catch (e: Exception) {
            logger.error("Failed to extract XML from ZIP", e)
            throw ZipPackagingException("Failed to extract XML: ${e.message}", e)
        }
    }

    /**
     * Extract all files from Base64-encoded ZIP
     *
     * @param zipBase64 Base64-encoded ZIP file
     * @return Map of file names to content
     */
    fun extractAllFromZip(zipBase64: String): Map<String, String> {
        logger.debug("Extracting all files from Base64 ZIP")
        
        try {
            // Decode Base64
            val zipBytes = Base64.getDecoder().decode(zipBase64)
            val files = mutableMapOf<String, String>()
            
            // Extract all files from ZIP
            ByteArrayInputStream(zipBytes).use { byteStream ->
                ZipInputStream(byteStream).use { zipStream ->
                    var zipEntry = zipStream.nextEntry
                    
                    while (zipEntry != null) {
                        if (!zipEntry.isDirectory) {
                            val fileBytes = zipStream.readBytes()
                            val content = String(fileBytes, Charsets.UTF_8)
                            files[zipEntry.name] = content
                        }
                        zipEntry = zipStream.nextEntry
                    }
                }
            }
            
            logger.debug("Extracted ${files.size} files from ZIP")
            return files
            
        } catch (e: Exception) {
            logger.error("Failed to extract files from ZIP", e)
            throw ZipPackagingException("Failed to extract files: ${e.message}", e)
        }
    }

    /**
     * Validate ZIP file integrity
     *
     * @param zipBase64 Base64-encoded ZIP file
     * @return true if ZIP is valid
     */
    fun validateZip(zipBase64: String): Boolean {
        return try {
            val zipBytes = Base64.getDecoder().decode(zipBase64)
            
            ByteArrayInputStream(zipBytes).use { byteStream ->
                ZipInputStream(byteStream).use { zipStream ->
                    var hasEntries = false
                    var zipEntry = zipStream.nextEntry
                    
                    while (zipEntry != null) {
                        hasEntries = true
                        // Try to read the entry to validate it
                        zipStream.readBytes()
                        zipEntry = zipStream.nextEntry
                    }
                    
                    hasEntries
                }
            }
        } catch (e: Exception) {
            logger.warn("ZIP validation failed: ${e.message}")
            false
        }
    }
}

/**
 * Packaged invoice result
 */
data class PackagedInvoice(
    val zipFileName: String,
    val xmlFileName: String,
    val zipBase64: String,
    val zipSizeBytes: Int
)

/**
 * Custom exception for ZIP packaging errors
 */
class ZipPackagingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
