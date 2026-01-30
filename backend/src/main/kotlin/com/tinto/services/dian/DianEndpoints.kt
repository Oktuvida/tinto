package com.tinto.services.dian

/**
 * DIAN environment endpoints configuration
 *
 * DIAN provides two environments:
 * - Habilitación: Test/staging environment for development and testing
 * - Producción: Production environment for real invoices
 */
enum class DianEnvironment(
    val environmentName: String,
    val wsdlUrl: String,
    val isProduction: Boolean
) {
    /**
     * Habilitación (Test) environment
     * Use this for development, testing, and staging
     */
    HABILITACION(
        environmentName = "habilitacion",
        wsdlUrl = "https://vpfe-hab.dian.gov.co/WcfDianCustomerServices.svc?wsdl",
        isProduction = false
    ),

    /**
     * Producción (Production) environment
     * Use this ONLY for real invoices in production
     */
    PRODUCCION(
        environmentName = "produccion",
        wsdlUrl = "https://vpfe.dian.gov.co/WcfDianCustomerServices.svc?wsdl",
        isProduction = true
    );

    companion object {
        /**
         * Get environment by name
         */
        fun fromName(name: String): DianEnvironment {
            return when (name.lowercase()) {
                "produccion", "production", "prod" -> PRODUCCION
                else -> HABILITACION
            }
        }
    }

    /**
     * Get service endpoint URL (without WSDL suffix)
     */
    fun getServiceUrl(): String {
        return wsdlUrl.removeSuffix("?wsdl")
    }
}

/**
 * DIAN SOAP operations
 */
enum class DianOperation(val operationName: String) {
    /**
     * Send invoice asynchronously to DIAN
     * Returns a TrackId for status polling
     */
    SEND_BILL_ASYNC("SendBillAsync"),

    /**
     * Send invoice synchronously to DIAN
     * Returns immediate response (slower, not recommended for production)
     */
    SEND_BILL_SYNC("SendBillSync"),

    /**
     * Get status of a previously submitted invoice
     * Requires TrackId from SendBillAsync
     */
    GET_STATUS("GetStatus"),

    /**
     * Get status with full ApplicationResponse ZIP
     */
    GET_STATUS_ZIP("GetStatusZip"),

    /**
     * Send test set to DIAN (for habilitación certification)
     */
    SEND_TEST_SET_ASYNC("SendTestSetAsync"),

    /**
     * Get test set results
     */
    GET_TEST_SET_RESULT("GetNumberingRange");

    fun isAsync(): Boolean = operationName.contains("Async")
}
