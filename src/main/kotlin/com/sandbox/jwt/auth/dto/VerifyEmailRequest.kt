package com.sandbox.jwt.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class VerifyEmailRequest(
    @field:NotBlank(message = "The verification token cannot be blank.")
    @field:Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "The verification token format is invalid."
    )
    val token: String? = null
)
