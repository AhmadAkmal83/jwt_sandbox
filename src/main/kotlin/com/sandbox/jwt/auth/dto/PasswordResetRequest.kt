package com.sandbox.jwt.auth.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

class PasswordResetRequest @JsonCreator constructor(
    @JsonProperty("email")
    _email: String,
) {
    @get:NotBlank(message = "Email cannot be blank.")
    @get:Email(message = "Email format is invalid.")
    val email: String = _email.trim()
}
