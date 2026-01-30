package com.tinto.domain.auth

/**
 * API key roles defining access levels
 *
 * - ADMIN: Full access to all operations including master key derivation
 * - OPERATOR: Can issue invoices and manage billing operations
 * - AUDITOR: Read-only access for auditing and reporting
 */
enum class Role {
    /**
     * Administrator role
     * - Can derive new API keys (with local console access)
     * - Can manage issuers and certificates
     * - Full read/write access to all resources
     */
    ADMIN,

    /**
     * Operator role
     * - Can issue invoices
     * - Can manage customers
     * - Can view invoice status and DIAN responses
     * - Cannot manage API keys or system configuration
     */
    OPERATOR,

    /**
     * Auditor role
     * - Read-only access to all invoices and submissions
     * - Can view DIAN responses and status
     * - Cannot create or modify any resources
     */
    AUDITOR;

    /**
     * Check if this role has permission to perform an operation
     */
    fun canCreate(): Boolean = this in listOf(ADMIN, OPERATOR)
    fun canUpdate(): Boolean = this in listOf(ADMIN, OPERATOR)
    fun canDelete(): Boolean = this == ADMIN
    fun canRead(): Boolean = true // All roles can read
    fun canManageKeys(): Boolean = this == ADMIN
    fun canManageIssuers(): Boolean = this == ADMIN
    fun canIssueInvoices(): Boolean = this in listOf(ADMIN, OPERATOR)
}
