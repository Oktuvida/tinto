package com.tinto.services.dian

import com.tinto.domain.billing.Invoice
import com.tinto.domain.billing.LineItem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * UBL 2.1 XML builder for DIAN electronic invoices.
 *
 * This service builds DIAN-compliant UBL 2.1 XML documents
 * following the technical specifications from TECH_SPECS.md.
 *
 * Key requirements:
 * - Strict namespace compliance
 * - CUFE must be calculated before XML generation
 * - All amounts in 2 decimal precision
 * - Colombian timezone (-05:00)
 */
@Service
class UblXmlBuilder {

    private val logger = LoggerFactory.getLogger(UblXmlBuilder::class.java)

    companion object {
        // UBL 2.1 Namespaces
        private const val NS_INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
        private const val NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
        private const val NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
        private const val NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2"
        private const val NS_STS = "dian:gov:co:facturaelectronica:Structures-2-1"
        private const val NS_XSI = "http://www.w3.org/2001/XMLSchema-instance"
        
        // DIAN-specific constants
        private const val UBL_VERSION = "UBL 2.1"
        private const val CUSTOMIZATION_ID = "10" // Standard sales invoice
        private const val PROFILE_ID = "DIAN 2.1"
        private const val PROFILE_EXECUTION_ID = "1" // 1=Production, 2=Test
        
        // Colombian timezone
        private const val COLOMBIA_TIMEZONE = "-05:00"
        
        // Date/time formatters
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
    }

    /**
     * Build UBL 2.1 XML for an invoice
     *
     * @param invoice Invoice entity with all data
     * @param cufe Pre-calculated CUFE
     * @param softwareId Software ID from DIAN registration
     * @param softwarePin Software PIN from DIAN registration
     * @return XML string
     */
    fun buildInvoiceXml(
        invoice: Invoice,
        cufe: String,
        softwareId: String,
        softwarePin: String
    ): String {
        logger.debug("Building UBL XML for invoice ${invoice.getInvoiceNumber()}")

        val docBuilder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder()

        val document = docBuilder.newDocument()

        // Create root Invoice element with namespaces
        val root = document.createElementNS(NS_INVOICE, "Invoice").apply {
            setAttribute("xmlns", NS_INVOICE)
            setAttribute("xmlns:cac", NS_CAC)
            setAttribute("xmlns:cbc", NS_CBC)
            setAttribute("xmlns:ext", NS_EXT)
            setAttribute("xmlns:sts", NS_STS)
            setAttribute("xmlns:xsi", NS_XSI)
        }
        document.appendChild(root)

        // 1. UBL Extensions (for signature - empty for now)
        addUblExtensions(document, root, softwareId, softwarePin)

        // 2. Header information
        addElement(document, root, "cbc:UBLVersionID", UBL_VERSION)
        addElement(document, root, "cbc:CustomizationID", CUSTOMIZATION_ID)
        addElement(document, root, "cbc:ProfileID", PROFILE_ID)
        addElement(document, root, "cbc:ProfileExecutionID", PROFILE_EXECUTION_ID)
        addElement(document, root, "cbc:ID", invoice.getInvoiceNumber())
        
        // UUID with CUFE
        val uuidElement = addElement(document, root, "cbc:UUID", cufe)
        uuidElement.setAttribute("schemeName", "CUFE-SHA384")

        // Issue date and time
        addElement(document, root, "cbc:IssueDate", invoice.issueDate.format(DATE_FORMATTER))
        addElement(document, root, "cbc:IssueTime", "12:00:00$COLOMBIA_TIMEZONE") // TODO: Use actual time

        // Due date (if specified)
        invoice.dueDate?.let {
            addElement(document, root, "cbc:DueDate", it.format(DATE_FORMATTER))
        }

        // Invoice type code (01 = Factura de Venta)
        addElement(document, root, "cbc:InvoiceTypeCode", "01")

        // Document currency code
        addElement(document, root, "cbc:DocumentCurrencyCode", invoice.currency)

        // Line count extension
        addElement(document, root, "cbc:LineCountNumeric", invoice.lineItems.size.toString())

        // 3. Supplier (Issuer)
        addAccountingSupplierParty(document, root, invoice)

        // 4. Customer
        addAccountingCustomerParty(document, root, invoice)

        // 5. Payment means (default: cash)
        addPaymentMeans(document, root)

        // 6. Tax totals
        addTaxTotal(document, root, invoice)

        // 7. Legal monetary total
        addLegalMonetaryTotal(document, root, invoice)

        // 8. Invoice lines
        invoice.lineItems.forEachIndexed { index, lineItem ->
            addInvoiceLine(document, root, lineItem, index + 1)
        }

        // Convert to string
        return documentToString(document)
    }

    /**
     * Add UBL extensions (for signature and DIAN-specific data)
     */
    private fun addUblExtensions(document: Document, root: Element, softwareId: String, softwarePin: String) {
        val extensions = document.createElementNS(NS_EXT, "ext:UBLExtensions")
        
        // Extension for signature (empty, will be filled by signer)
        val signatureExt = document.createElementNS(NS_EXT, "ext:UBLExtension")
        val signatureContent = document.createElementNS(NS_EXT, "ext:ExtensionContent")
        signatureExt.appendChild(signatureContent)
        extensions.appendChild(signatureExt)

        // Extension for DIAN data
        val dianExt = document.createElementNS(NS_EXT, "ext:UBLExtension")
        val dianContent = document.createElementNS(NS_EXT, "ext:ExtensionContent")
        
        // Software provider info
        val dianExtensions = document.createElementNS(NS_STS, "sts:DianExtensions")
        val softwareProvider = document.createElementNS(NS_STS, "sts:SoftwareProvider")
        addElement(document, softwareProvider, "sts:ProviderID", softwareId)
        addElement(document, softwareProvider, "sts:SoftwareID", softwareId)
        dianExtensions.appendChild(softwareProvider)
        
        dianContent.appendChild(dianExtensions)
        dianExt.appendChild(dianContent)
        extensions.appendChild(dianExt)

        root.appendChild(extensions)
    }

    /**
     * Add supplier (issuer) party information
     */
    private fun addAccountingSupplierParty(document: Document, root: Element, invoice: Invoice) {
        val supplier = document.createElementNS(NS_CAC, "cac:AccountingSupplierParty")
        val party = document.createElementNS(NS_CAC, "cac:Party")
        
        // Party identification (NIT)
        val partyIdentification = document.createElementNS(NS_CAC, "cac:PartyIdentification")
        val idElement = addElement(document, partyIdentification, "cbc:ID", invoice.issuer.nit)
        idElement.setAttribute("schemeID", "31") // 31 = NIT
        idElement.setAttribute("schemeName", "31")
        party.appendChild(partyIdentification)

        // Party name
        val partyName = document.createElementNS(NS_CAC, "cac:PartyName")
        addElement(document, partyName, "cbc:Name", invoice.issuer.name)
        party.appendChild(partyName)

        // Physical location
        invoice.issuer.address?.let { address ->
            val location = document.createElementNS(NS_CAC, "cac:PhysicalLocation")
            val locationAddress = document.createElementNS(NS_CAC, "cac:Address")
            addElement(document, locationAddress, "cbc:CityName", invoice.issuer.city ?: "")
            addElement(document, locationAddress, "cbc:CountrySubentity", invoice.issuer.department ?: "")
            
            val country = document.createElementNS(NS_CAC, "cac:Country")
            addElement(document, country, "cbc:IdentificationCode", invoice.issuer.country)
            locationAddress.appendChild(country)
            
            location.appendChild(locationAddress)
            party.appendChild(location)
        }

        // Tax scheme
        val partyTaxScheme = document.createElementNS(NS_CAC, "cac:PartyTaxScheme")
        addElement(document, partyTaxScheme, "cbc:RegistrationName", invoice.issuer.name)
        val taxScheme = document.createElementNS(NS_CAC, "cac:TaxScheme")
        addElement(document, taxScheme, "cbc:ID", "01") // 01 = IVA
        addElement(document, taxScheme, "cbc:Name", "IVA")
        partyTaxScheme.appendChild(taxScheme)
        party.appendChild(partyTaxScheme)

        // Legal entity
        val partyLegal = document.createElementNS(NS_CAC, "cac:PartyLegalEntity")
        addElement(document, partyLegal, "cbc:RegistrationName", invoice.issuer.name)
        addElement(document, partyLegal, "cbc:CompanyID", invoice.issuer.nit)
        party.appendChild(partyLegal)

        supplier.appendChild(party)
        root.appendChild(supplier)
    }

    /**
     * Add customer party information
     */
    private fun addAccountingCustomerParty(document: Document, root: Element, invoice: Invoice) {
        val customer = document.createElementNS(NS_CAC, "cac:AccountingCustomerParty")
        val party = document.createElementNS(NS_CAC, "cac:Party")
        
        // Party identification
        val partyIdentification = document.createElementNS(NS_CAC, "cac:PartyIdentification")
        val idElement = addElement(document, partyIdentification, "cbc:ID", invoice.customer.identificationNumber)
        idElement.setAttribute("schemeID", getSchemeIdForIdentificationType(invoice.customer.identificationType))
        party.appendChild(partyIdentification)

        // Party name
        val partyName = document.createElementNS(NS_CAC, "cac:PartyName")
        addElement(document, partyName, "cbc:Name", invoice.customer.name)
        party.appendChild(partyName)

        // Tax scheme
        val partyTaxScheme = document.createElementNS(NS_CAC, "cac:PartyTaxScheme")
        addElement(document, partyTaxScheme, "cbc:RegistrationName", invoice.customer.name)
        val taxScheme = document.createElementNS(NS_CAC, "cac:TaxScheme")
        addElement(document, taxScheme, "cbc:ID", "01") // 01 = IVA
        partyTaxScheme.appendChild(taxScheme)
        party.appendChild(partyTaxScheme)

        customer.appendChild(party)
        root.appendChild(customer)
    }

    /**
     * Add payment means (default: cash)
     */
    private fun addPaymentMeans(document: Document, root: Element) {
        val paymentMeans = document.createElementNS(NS_CAC, "cac:PaymentMeans")
        addElement(document, paymentMeans, "cbc:ID", "1") // 1 = Cash
        addElement(document, paymentMeans, "cbc:PaymentMeansCode", "10") // 10 = Cash
        root.appendChild(paymentMeans)
    }

    /**
     * Add tax totals
     */
    private fun addTaxTotal(document: Document, root: Element, invoice: Invoice) {
        val taxTotal = document.createElementNS(NS_CAC, "cac:TaxTotal")
        
        val taxAmount = addElement(document, taxTotal, "cbc:TaxAmount", formatAmount(invoice.taxAmount))
        taxAmount.setAttribute("currencyID", invoice.currency)

        // Tax subtotal (IVA)
        val taxSubtotal = document.createElementNS(NS_CAC, "cac:TaxSubtotal")
        val taxableAmount = addElement(document, taxSubtotal, "cbc:TaxableAmount", formatAmount(invoice.subtotal))
        taxableAmount.setAttribute("currencyID", invoice.currency)
        
        val subtaxAmount = addElement(document, taxSubtotal, "cbc:TaxAmount", formatAmount(invoice.taxAmount))
        subtaxAmount.setAttribute("currencyID", invoice.currency)

        val taxCategory = document.createElementNS(NS_CAC, "cac:TaxCategory")
        addElement(document, taxCategory, "cbc:Percent", "19.00") // TODO: Calculate from line items
        val taxScheme = document.createElementNS(NS_CAC, "cac:TaxScheme")
        addElement(document, taxScheme, "cbc:ID", "01") // 01 = IVA
        addElement(document, taxScheme, "cbc:Name", "IVA")
        taxCategory.appendChild(taxScheme)
        taxSubtotal.appendChild(taxCategory)
        
        taxTotal.appendChild(taxSubtotal)
        root.appendChild(taxTotal)
    }

    /**
     * Add legal monetary total
     */
    private fun addLegalMonetaryTotal(document: Document, root: Element, invoice: Invoice) {
        val legalTotal = document.createElementNS(NS_CAC, "cac:LegalMonetaryTotal")
        
        addAmountElement(document, legalTotal, "cbc:LineExtensionAmount", invoice.subtotal, invoice.currency)
        addAmountElement(document, legalTotal, "cbc:TaxExclusiveAmount", invoice.subtotal, invoice.currency)
        addAmountElement(document, legalTotal, "cbc:TaxInclusiveAmount", invoice.totalAmount, invoice.currency)
        addAmountElement(document, legalTotal, "cbc:PayableAmount", invoice.totalAmount, invoice.currency)

        root.appendChild(legalTotal)
    }

    /**
     * Add invoice line
     */
    private fun addInvoiceLine(document: Document, root: Element, lineItem: LineItem, lineNumber: Int) {
        val invoiceLine = document.createElementNS(NS_CAC, "cac:InvoiceLine")
        
        addElement(document, invoiceLine, "cbc:ID", lineNumber.toString())
        
        val quantity = addElement(document, invoiceLine, "cbc:InvoicedQuantity", lineItem.quantity.toString())
        quantity.setAttribute("unitCode", "EA") // EA = Each
        
        addAmountElement(document, invoiceLine, "cbc:LineExtensionAmount", lineItem.lineTotal, "COP")

        // Item
        val item = document.createElementNS(NS_CAC, "cac:Item")
        addElement(document, item, "cbc:Description", lineItem.description)
        
        // Standard item identification
        val stdIdentification = document.createElementNS(NS_CAC, "cac:StandardItemIdentification")
        addElement(document, stdIdentification, "cbc:ID", "999") // Generic code
        item.appendChild(stdIdentification)
        
        invoiceLine.appendChild(item)

        // Price
        val price = document.createElementNS(NS_CAC, "cac:Price")
        addAmountElement(document, price, "cbc:PriceAmount", lineItem.unitPrice, "COP")
        invoiceLine.appendChild(price)

        root.appendChild(invoiceLine)
    }

    /**
     * Helper: Add element with text content
     */
    private fun addElement(document: Document, parent: Element, tagName: String, textContent: String): Element {
        val element = document.createElement(tagName)
        element.textContent = textContent
        parent.appendChild(element)
        return element
    }

    /**
     * Helper: Add amount element with currency
     */
    private fun addAmountElement(document: Document, parent: Element, tagName: String, amount: Long, currency: String) {
        val element = addElement(document, parent, tagName, formatAmount(amount))
        element.setAttribute("currencyID", currency)
    }

    /**
     * Format amount from cents to decimal string (2 decimal places)
     */
    private fun formatAmount(amountCents: Long): String {
        val amount = amountCents.toDouble() / 100.0
        return String.format("%.2f", amount)
    }

    /**
     * Get scheme ID for identification type
     */
    private fun getSchemeIdForIdentificationType(type: String): String {
        return when (type.uppercase()) {
            "NIT" -> "31"
            "CC" -> "13" // Cédula de Ciudadanía
            "CE" -> "22" // Cédula de Extranjería
            "PA" -> "41" // Pasaporte
            else -> "31" // Default to NIT
        }
    }

    /**
     * Convert XML document to string
     */
    private fun documentToString(document: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        
        val writer = StringWriter()
        transformer.transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }
}
