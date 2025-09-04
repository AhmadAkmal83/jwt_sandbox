package com.sandbox.jwt.mail.dto

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.mail")
data class MailProperties(
    val from: From,
    val verification: Verification,
    val passwordReset: PasswordReset,
) {
    data class From(
        val address: String,
        val name: String,
    )

    data class Verification(
        val url: String,
    )

    data class PasswordReset(
        val url: String,
    )
}
