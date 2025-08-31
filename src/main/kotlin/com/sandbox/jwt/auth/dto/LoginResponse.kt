package com.sandbox.jwt.auth.dto

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
)
