package com.sandbox.jwt.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.jwt")
data class JwtProperties(
    val secretKey: String,
    val accessTokenExpirationMs: Long,
    val refreshTokenExpirationMs: Long,
)
