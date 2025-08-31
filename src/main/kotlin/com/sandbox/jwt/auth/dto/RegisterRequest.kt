package com.sandbox.jwt.auth.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

class RegisterRequest @JsonCreator constructor(
    @JsonProperty("email")
    _email: String,

    @JsonProperty("password")
    _password: String
) {
    @get:NotBlank(message = "Email cannot be blank.")
    @get:Email(message = "Email format is invalid.")
    val email: String = _email.trim()

    @get:NotBlank(message = "Password cannot be blank.")
    @get:Size(min = 8, max = 30, message = "Password must be between 8 and 30 characters.")
    val password: String = _password
}
