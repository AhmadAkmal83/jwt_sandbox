package com.sandbox.jwt.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class PasswordResetConsumptionRequest(
    @field:NotBlank(message = "The reset token cannot be blank.")
    @field:Pattern(
        regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        message = "The reset token format is invalid."
    )
    val token: String,

    @field:NotBlank(message = "New password cannot be blank.")
    @field:Size(min = 8, max = 30, message = "New password must be between 8 and 30 characters.")
    val newPassword: String,
)
