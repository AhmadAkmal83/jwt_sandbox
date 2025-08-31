package com.sandbox.jwt.common.exception

import com.sandbox.jwt.auth.exception.AccountNotVerifiedException
import com.sandbox.jwt.auth.exception.EmailAlreadyExistsException
import com.sandbox.jwt.auth.exception.InvalidVerificationTokenException
import com.sandbox.jwt.auth.exception.VerificationTokenExpiredException
import com.sandbox.jwt.common.exception.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: AuthenticationException): ResponseEntity<ErrorResponse> {
        val body = buildSimpleErrorResponse(ex.message, "Authentication failed.")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        val body = ErrorResponse(message = "Request is missing required fields.")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val body = buildValidationErrorResponse(ex)
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body)
    }

    @ExceptionHandler(AccountNotVerifiedException::class)
    fun handleAccountNotVerifiedException(ex: AccountNotVerifiedException): ResponseEntity<ErrorResponse> {
        val body = buildSimpleErrorResponse(ex.message, "Account is not verified.")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body)
    }

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun handleEmailAlreadyExistsException(ex: EmailAlreadyExistsException): ResponseEntity<ErrorResponse> {
        val body = buildSimpleErrorResponse(ex.message, "The provided email already exists.")
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body)
    }

    @ExceptionHandler(InvalidVerificationTokenException::class)
    fun handleInvalidVerificationTokenException(ex: InvalidVerificationTokenException): ResponseEntity<ErrorResponse> {
        val body = buildSimpleErrorResponse(ex.message, "The provided token is invalid.")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(VerificationTokenExpiredException::class)
    fun handleVerificationTokenExpiredException(ex: VerificationTokenExpiredException): ResponseEntity<ErrorResponse> {
        val body = buildSimpleErrorResponse(ex.message, "The provided token has expired.")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    private fun buildSimpleErrorResponse(message: String?, fallBackMessage: String): ErrorResponse {
        return ErrorResponse(message = message ?: fallBackMessage)
    }

    private fun buildValidationErrorResponse(ex: MethodArgumentNotValidException): ErrorResponse {
        val errors = ex.bindingResult.fieldErrors
            .groupBy { it.field }
            .mapValues { entry -> entry.value.mapNotNull { it.defaultMessage } }

        return ErrorResponse(
            message = "The given data was invalid.",
            errors = errors,
        )
    }
}
