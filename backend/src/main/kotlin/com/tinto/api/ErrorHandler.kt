package com.tinto.api

import com.tinto.services.dian.DianException
import com.tinto.services.invoice.InvoiceServiceException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import java.time.Instant

/**
 * Global error handler for REST API
 *
 * Provides consistent error responses across all endpoints
 */
@RestControllerAdvice
class ErrorHandler {

    private val logger = LoggerFactory.getLogger(ErrorHandler::class.java)

    /**
     * Handle Jakarta Bean Validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationError(
        ex: MethodArgumentNotValidException,
        request: WebRequest
    ): ResponseEntity<ValidationErrorResponse> {
        logger.warn("Validation error: ${ex.bindingResult.errorCount} field(s) invalid")

        val fieldErrors = ex.bindingResult.allErrors.mapNotNull { error ->
            when (error) {
                is FieldError -> FieldValidationError(
                    field = error.field,
                    rejectedValue = error.rejectedValue?.toString(),
                    message = error.defaultMessage ?: "Invalid value"
                )
                else -> null
            }
        }

        val error = ValidationErrorResponse(
            timestamp = Instant.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = "VALIDATION_ERROR",
            message = "Invalid request parameters",
            path = getRequestPath(request),
            fieldErrors = fieldErrors
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
    }

    /**
     * Handle invoice service errors (business logic validation)
     */
    @ExceptionHandler(InvoiceServiceException::class)
    fun handleInvoiceServiceError(ex: InvoiceServiceException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Invoice service error: ${ex.message}")

        // Determine status code based on message content
        val status = when {
            ex.message?.contains("not found", ignoreCase = true) == true -> HttpStatus.NOT_FOUND
            ex.message?.contains("duplicate", ignoreCase = true) == true -> HttpStatus.CONFLICT
            ex.message?.contains("cannot be issued", ignoreCase = true) == true -> HttpStatus.CONFLICT
            else -> HttpStatus.BAD_REQUEST
        }

        val error = ErrorResponse(
            timestamp = Instant.now(),
            status = status.value(),
            error = when (status) {
                HttpStatus.NOT_FOUND -> "RESOURCE_NOT_FOUND"
                HttpStatus.CONFLICT -> "BUSINESS_RULE_VIOLATION"
                else -> "INVALID_REQUEST"
            },
            message = ex.message ?: "Invoice operation failed",
            path = getRequestPath(request)
        )

        return ResponseEntity.status(status).body(error)
    }

    /**
     * Handle IllegalArgumentException (general validation errors)
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentError(ex: IllegalArgumentException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Illegal argument: ${ex.message}")

        val error = ErrorResponse(
            timestamp = Instant.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = "INVALID_ARGUMENT",
            message = ex.message ?: "Invalid request parameters",
            path = getRequestPath(request)
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
    }

    /**
     * Handle security/authentication errors
     */
    @ExceptionHandler(SecurityException::class)
    fun handleSecurityError(ex: SecurityException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.error("Security error: ${ex.message}")

        val error = ErrorResponse(
            timestamp = Instant.now(),
            status = HttpStatus.FORBIDDEN.value(),
            error = "FORBIDDEN",
            message = ex.message ?: "Access denied",
            path = getRequestPath(request)
        )

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error)
    }

    /**
     * Handle resource not found errors
     */
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFoundError(ex: NoSuchElementException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Resource not found: ${ex.message}")

        val error = ErrorResponse(
            timestamp = Instant.now(),
            status = HttpStatus.NOT_FOUND.value(),
            error = "RESOURCE_NOT_FOUND",
            message = ex.message ?: "Resource not found",
            path = getRequestPath(request)
        )

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error)
    }

    /**
     * Handle DIAN service errors
     */
    @ExceptionHandler(DianException::class)
    fun handleDianError(ex: DianException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.error("DIAN service error: ${ex.message}", ex)

        val error = ErrorResponse(
            timestamp = Instant.now(),
            status = HttpStatus.BAD_GATEWAY.value(),
            error = "DIAN_SERVICE_ERROR",
            message = "Error communicating with DIAN service: ${ex.message}",
            path = getRequestPath(request)
        )

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error)
    }

    /**
     * Handle illegal state errors
     */
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateError(ex: IllegalStateException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.error("Illegal state error: ${ex.message}", ex)

        val error = ErrorResponse(
            timestamp = Instant.now(),
            status = HttpStatus.CONFLICT.value(),
            error = "INVALID_STATE",
            message = ex.message ?: "Operation cannot be performed in current state",
            path = getRequestPath(request)
        )

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error)
    }

    /**
     * Handle all other unexpected errors
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericError(ex: Exception, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error", ex)

        val error = ErrorResponse(
            timestamp = Instant.now(),
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "INTERNAL_SERVER_ERROR",
            message = "An unexpected error occurred. Please contact support.",
            path = getRequestPath(request)
        )

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error)
    }

    /**
     * Extract request path from WebRequest
     */
    private fun getRequestPath(request: WebRequest): String {
        return request.getDescription(false).removePrefix("uri=")
    }
}

/**
 * Standard error response format
 */
data class ErrorResponse(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val message: String,
    val path: String
)

/**
 * Validation error response with field-level details
 */
data class ValidationErrorResponse(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val fieldErrors: List<FieldValidationError>
)

/**
 * Field-level validation error
 */
data class FieldValidationError(
    val field: String,
    val rejectedValue: String?,
    val message: String
)
