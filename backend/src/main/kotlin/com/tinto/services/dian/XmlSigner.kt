package com.tinto.services.dian

import org.apache.xml.security.Init
import org.apache.xml.security.c14n.Canonicalizer
import org.apache.xml.security.signature.XMLSignature
import org.apache.xml.security.transforms.Transforms
import org.apache.xml.security.utils.Constants
import org.apache.xml.security.utils.XMLUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * XML Digital Signature Service (XAdES-EPES)
 *
 * Signs UBL 2.1 XML documents using X.509 certificates following DIAN requirements:
 * - Signature Algorithm: RSA-SHA256
 * - Canonicalization: C14N (Canonical XML 1.0)
 * - Type: Enveloped Signature (signature inside UBLExtensions)
 *
 * Technical Steps:
 * 1. Parse XML document
 * 2. Create SignedInfo with SHA-256 digest
 * 3. Sign with private key (RSA-SHA256)
 * 4. Embed X.509 certificate in KeyInfo
 * 5. Insert signature into UBLExtensions/UBLExtension/ExtensionContent
 *
 * References:
 * - DIAN Anexo 006: Firma Electrónica
 * - XML Signature Syntax and Processing (XMLDSIG)
 * - XAdES-EPES (Explicit Policy-based Electronic Signatures)
 */
@Service
class XmlSigner {

    private val logger = LoggerFactory.getLogger(XmlSigner::class.java)

    companion object {
        // Signature algorithm: RSA with SHA-256
        private const val SIGNATURE_ALGORITHM = XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256
        
        // Canonicalization algorithm: Canonical XML 1.0 (without comments)
        private const val CANONICALIZATION_ALGORITHM = Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS
        
        // Digest algorithm: SHA-256
        private const val DIGEST_ALGORITHM = "http://www.w3.org/2001/04/xmlenc#sha256"
        
        // UBL namespace
        private const val UBL_NAMESPACE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
        
        // Extension namespace
        private const val EXT_NAMESPACE = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2"
        
        // XML Signature namespace
        private const val DS_NAMESPACE = Constants.SignatureSpecNS

        init {
            // Initialize Apache Santuario XML Security library
            Init.init()
        }
    }

    /**
     * Sign UBL XML document with X.509 certificate
     *
     * @param xmlContent UBL 2.1 XML content as String
     * @param keystorePath Path to PKCS12 keystore file
     * @param keystorePassword Keystore password
     * @param keyAlias Certificate alias in keystore
     * @param keyPassword Private key password
     * @return Signed XML as String
     */
    fun signXml(
        xmlContent: String,
        keystorePath: String,
        keystorePassword: String,
        keyAlias: String,
        keyPassword: String
    ): String {
        logger.info("Signing XML document with certificate alias: $keyAlias")
        
        try {
            // Load certificate and private key from keystore
            val (privateKey, certificate) = loadKeystore(keystorePath, keystorePassword, keyAlias, keyPassword)
            
            // Parse XML document
            val document = parseXml(xmlContent)
            
            // Sign the document
            signDocument(document, privateKey, certificate)
            
            // Serialize signed document back to string
            val signedXml = serializeDocument(document)
            
            logger.info("XML document signed successfully")
            return signedXml
            
        } catch (e: Exception) {
            logger.error("Failed to sign XML document", e)
            throw XmlSigningException("Failed to sign XML document: ${e.message}", e)
        }
    }

    /**
     * Sign XML document using in-memory private key and certificate
     *
     * @param xmlContent UBL 2.1 XML content as String
     * @param privateKey Private key for signing
     * @param certificate X.509 certificate
     * @return Signed XML as String
     */
    fun signXml(
        xmlContent: String,
        privateKey: PrivateKey,
        certificate: X509Certificate
    ): String {
        logger.info("Signing XML document with certificate: ${certificate.subjectX500Principal.name}")
        
        try {
            // Parse XML document
            val document = parseXml(xmlContent)
            
            // Sign the document
            signDocument(document, privateKey, certificate)
            
            // Serialize signed document back to string
            val signedXml = serializeDocument(document)
            
            logger.info("XML document signed successfully")
            return signedXml
            
        } catch (e: Exception) {
            logger.error("Failed to sign XML document", e)
            throw XmlSigningException("Failed to sign XML document: ${e.message}", e)
        }
    }

    /**
     * Load private key and certificate from PKCS12 keystore
     */
    private fun loadKeystore(
        keystorePath: String,
        keystorePassword: String,
        keyAlias: String,
        keyPassword: String
    ): Pair<PrivateKey, X509Certificate> {
        logger.debug("Loading keystore from: $keystorePath")
        
        val keystore = KeyStore.getInstance("PKCS12")
        FileInputStream(keystorePath).use { fis ->
            keystore.load(fis, keystorePassword.toCharArray())
        }
        
        // Load private key
        val privateKey = keystore.getKey(keyAlias, keyPassword.toCharArray()) as? PrivateKey
            ?: throw XmlSigningException("Private key not found for alias: $keyAlias")
        
        // Load certificate
        val certificate = keystore.getCertificate(keyAlias) as? X509Certificate
            ?: throw XmlSigningException("Certificate not found for alias: $keyAlias")
        
        logger.debug("Loaded certificate: ${certificate.subjectX500Principal.name}")
        
        return Pair(privateKey, certificate)
    }

    /**
     * Parse XML string into DOM Document
     */
    private fun parseXml(xmlContent: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isIgnoringComments = true
        
        val builder = factory.newDocumentBuilder()
        return builder.parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
    }

    /**
     * Sign the XML document and embed signature in UBLExtensions
     *
     * DIAN requires the signature to be placed in:
     * /Invoice/ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent/ds:Signature
     */
    private fun signDocument(document: Document, privateKey: PrivateKey, certificate: X509Certificate) {
        logger.debug("Creating XML signature")
        
        // Get document element (root)
        val rootElement = document.documentElement
        
        // Find or create UBLExtensions element for signature
        val extensionContent = getOrCreateSignatureExtension(document, rootElement)
        
        // Create XML Signature object
        val signature = XMLSignature(document, "", SIGNATURE_ALGORITHM, CANONICALIZATION_ALGORITHM)
        extensionContent.appendChild(signature.element)
        
        // Create transforms for enveloped signature
        val transforms = Transforms(document)
        transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE)
        
        // Add document reference with SHA-256 digest
        // Reference to the entire document (empty URI = root element)
        signature.addDocument("", transforms, DIGEST_ALGORITHM)
        
        // Add X.509 certificate to KeyInfo
        signature.addKeyInfo(certificate)
        
        // Sign the document
        signature.sign(privateKey)
        
        logger.debug("Signature created and embedded successfully")
    }

    /**
     * Find or create UBLExtensions/UBLExtension/ExtensionContent for signature
     *
     * DIAN expects the signature in the first UBLExtension with specific structure
     */
    private fun getOrCreateSignatureExtension(document: Document, rootElement: Element): Element {
        var ublExtensions = rootElement.getElementsByTagNameNS(EXT_NAMESPACE, "UBLExtensions").item(0) as? Element
        
        // Create UBLExtensions if it doesn't exist
        if (ublExtensions == null) {
            ublExtensions = document.createElementNS(EXT_NAMESPACE, "ext:UBLExtensions")
            rootElement.insertBefore(ublExtensions, rootElement.firstChild)
        }
        
        // Find or create UBLExtension for signature
        var ublExtension = ublExtensions.getElementsByTagNameNS(EXT_NAMESPACE, "UBLExtension").item(0) as? Element
        
        if (ublExtension == null) {
            ublExtension = document.createElementNS(EXT_NAMESPACE, "ext:UBLExtension")
            
            // Add ExtensionContent
            val extensionContent = document.createElementNS(EXT_NAMESPACE, "ext:ExtensionContent")
            ublExtension.appendChild(extensionContent)
            
            ublExtensions.appendChild(ublExtension)
            
            return extensionContent
        }
        
        // Get or create ExtensionContent
        var extensionContent = ublExtension.getElementsByTagNameNS(EXT_NAMESPACE, "ExtensionContent").item(0) as? Element
        
        if (extensionContent == null) {
            extensionContent = document.createElementNS(EXT_NAMESPACE, "ext:ExtensionContent")
            ublExtension.appendChild(extensionContent)
        }
        
        return extensionContent
    }

    /**
     * Serialize DOM Document to XML string
     */
    private fun serializeDocument(document: Document): String {
        val transformerFactory = TransformerFactory.newInstance()
        val transformer = transformerFactory.newTransformer()
        
        // Don't add XML declaration (UBL already has it)
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "no")
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "no")
        
        val outputStream = ByteArrayOutputStream()
        transformer.transform(DOMSource(document), StreamResult(outputStream))
        
        return outputStream.toString(Charsets.UTF_8)
    }

    /**
     * Verify XML signature (for testing and validation)
     *
     * @param signedXml Signed XML content
     * @return true if signature is valid, false otherwise
     */
    fun verifySignature(signedXml: String): Boolean {
        logger.debug("Verifying XML signature")
        
        try {
            val document = parseXml(signedXml)
            
            // Find signature element
            val signatureElements = document.getElementsByTagNameNS(DS_NAMESPACE, "Signature")
            
            if (signatureElements.length == 0) {
                logger.warn("No signature found in XML document")
                return false
            }
            
            val signatureElement = signatureElements.item(0) as Element
            val signature = XMLSignature(signatureElement, "")
            
            // Get certificate from KeyInfo
            val keyInfo = signature.keyInfo
            val certificate = keyInfo.getX509Certificate() ?: run {
                logger.warn("No X.509 certificate found in signature")
                return false
            }
            
            // Verify signature with certificate's public key
            val isValid = signature.checkSignatureValue(certificate.publicKey)
            
            logger.debug("Signature verification result: $isValid")
            return isValid
            
        } catch (e: Exception) {
            logger.error("Failed to verify signature", e)
            return false
        }
    }
}

/**
 * Custom exception for XML signing errors
 */
class XmlSigningException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
