package com.sandbox.jwt.auth.service

import com.sandbox.jwt.auth.config.JwtProperties
import com.sandbox.jwt.user.domain.Role
import com.sandbox.jwt.user.domain.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date
import javax.crypto.SecretKey

class JwtServiceTest {

    private lateinit var jwtProperties: JwtProperties

    private lateinit var secretKey: SecretKey

    private lateinit var jwtService: JwtService

    private val testSecret = "dGhlLWhpZGRlbi1tZXNzYWdlLWlzLXRoYXQtdGhlcmUtaXMtbm8taGlkZGVuLW1lc3NhZ2U="
    private val accessTokenExpirationMs = 900000L // 15 minutes
    private val refreshTokenExpirationMs = 604800000L // 15 minutes

    @BeforeEach
    fun setUp() {
        jwtProperties = JwtProperties(
            secretKey = testSecret,
            accessTokenExpirationMs = accessTokenExpirationMs,
            refreshTokenExpirationMs = refreshTokenExpirationMs,
        )
        jwtService = JwtService(jwtProperties)
        secretKey = Keys.hmacShaKeyFor(testSecret.toByteArray())
    }

    @Test
    fun `generateToken should create a valid JWT with correct subject, roles, and expiration`() {
        // Arrange
        val user = User(
            email = "existing_user@example.test",
            passwordHash = "UserPassword123",
            roles = mutableSetOf(Role.USER, Role.ADMIN)
        )
        val now = Date()

        // Act
        val token = jwtService.generateToken(user)

        // Assert
        assertThat(token).isNotNull().isNotEmpty()

        val claims = parseTokenClaims(token)
        val expirationDate = claims.expiration
        val issuedAtDate = claims.issuedAt

        assertThat(claims.subject).isEqualTo(user.email)

        @Suppress("UNCHECKED_CAST")
        val rolesClaim = claims["roles"] as List<String>
        assertThat(rolesClaim).containsExactlyInAnyOrder(Role.USER.name, Role.ADMIN.name)

        assertThat(issuedAtDate).isCloseTo(now, 1000) // Within 1 second
        val expectedExpiration = Date(issuedAtDate.time + accessTokenExpirationMs)
        assertThat(expirationDate).isCloseTo(expectedExpiration, 1000) // Within 1 second
    }

    private fun parseTokenClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
