package com.sandbox.jwt.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "Email cannot be blank.")
    @field:Email(message = "Invalid email format.")
    val email: String,

    @field:NotBlank(message = "Password cannot be blank.")
    val password: String,
)
