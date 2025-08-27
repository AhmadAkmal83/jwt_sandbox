package com.sandbox.jwt.exception

import com.sandbox.jwt.auth.exception.EmailAlreadyExistsException
import com.sandbox.jwt.auth.exception.InvalidVerificationTokenException
import com.sandbox.jwt.auth.exception.VerificationTokenExpiredException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun handleEmailExistsException(ex: EmailAlreadyExistsException): ResponseEntity<Map<String, String?>> {
        val body = mapOf("error" to ex.message)

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(body)
    }

    @ExceptionHandler(InvalidVerificationTokenException::class)
    fun handleInvalidTokenException(ex: InvalidVerificationTokenException): ResponseEntity<Map<String, String?>> {
        val body = mapOf("error" to ex.message)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(body)
    }

    @ExceptionHandler(VerificationTokenExpiredException::class)
    fun handleTokenExpiredException(ex: VerificationTokenExpiredException): ResponseEntity<Map<String, String?>> {
        val body = mapOf("error" to ex.message)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = ex.bindingResult.fieldErrors.map { it.defaultMessage }
        val body = mapOf(
            "error" to "Validation Failed",
            "messages" to errors,
        )

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(body)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMissingRequestBodyException(ex: HttpMessageNotReadableException): ResponseEntity<Map<String, String?>> {
        val body = mapOf("error" to "Request is missing required fields")

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(body)
    }
}
