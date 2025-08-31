package com.sandbox.jwt.auth.service.dto

data class LoginResult(
    val accessToken: String,
    val refreshToken: String,
)
