package com.tinto.api

import com.tinto.services.dian.DianException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
     * Handle validation errors
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleValidationError(ex: IllegalArgumentException, request: WebRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Validation error: ${ex.message}")
        
        val error = ErrorResponse(
            timestamp = Instant.now(),
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
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
            error = "Forbidden",
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
            error = "Not Found",
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
            error = "Bad Gateway",
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
            error = "Conflict",
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
            error = "Internal Server Error",
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
