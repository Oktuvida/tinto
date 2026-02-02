package com.tinto.api.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Request to create a new invoice
 */
data class CreateInvoiceRequest(
    @field:NotBlank(message = "Issuer NIT is required")
    @field:Pattern(
        regexp = "^[0-9]{9,10}$",
        message = "Issuer NIT must be 9-10 digits"
    )
    val issuerNit: String,

    @field:NotBlank(message = "Customer identification type is required")
    @field:Size(min = 1, max = 10, message = "Identification type must be 1-10 characters")
    val customerIdentificationType: String,

    @field:NotBlank(message = "Customer identification number is required")
    @field:Size(min = 1, max = 50, message = "Identification number must be 1-50 characters")
    val customerIdentificationNumber: String,

    @field:Size(max = 10, message = "Prefix must be at most 10 characters")
    val prefix: String? = null,

    @field:PastOrPresent(message = "Issue date cannot be in the future")
    val issueDate: LocalDate? = null,

    @field:Future(message = "Due date must be in the future")
    val dueDate: LocalDate? = null,

    @field:Pattern(
        regexp = "^[A-Z]{3}$",
        message = "Currency code must be 3 uppercase letters (e.g., COP, USD)"
    )
    val currencyCode: String? = "COP",

    @field:NotEmpty(message = "Invoice must have at least one line item")
    @field:Valid
    val lineItems: List<LineItemRequest>,

    @field:Positive(message = "Total amount must be positive if provided")
    val totalAmount: BigDecimal? = null
)

/**
 * Line item in an invoice
 */
data class LineItemRequest(
    @field:NotBlank(message = "Item description is required")
    @field:Size(min = 1, max = 500, message = "Description must be 1-500 characters")
    val description: String,

    @field:NotNull(message = "Quantity is required")
    @field:Positive(message = "Quantity must be positive")
    @field:DecimalMin(value = "0.01", message = "Quantity must be at least 0.01")
    val quantity: BigDecimal,

    @field:NotNull(message = "Unit price is required")
    @field:PositiveOrZero(message = "Unit price cannot be negative")
    val unitPrice: BigDecimal,

    @field:DecimalMin(value = "0.00", message = "Tax rate cannot be negative")
    @field:DecimalMax(value = "100.00", message = "Tax rate cannot exceed 100%")
    val taxRate: BigDecimal? = null,

    @field:Size(max = 50, message = "Item code must be at most 50 characters")
    val itemCode: String? = null
)
