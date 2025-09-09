package com.sandbox.jwt.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class TokenRefreshRequest(
    @field:NotBlank(message = "Refresh token cannot be blank.")
    @field:Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "The refresh token format is invalid."
    )
    val token: String,
)
