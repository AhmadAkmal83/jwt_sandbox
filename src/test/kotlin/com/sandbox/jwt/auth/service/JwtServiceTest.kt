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
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.Date
import javax.crypto.SecretKey
import org.springframework.security.core.userdetails.User as SpringUser

class JwtServiceTest {

    private lateinit var jwtProperties: JwtProperties

    private lateinit var secretKey: SecretKey

    private lateinit var jwtService: JwtService

    private lateinit var existingUser: User

    private lateinit var existingUserDetails: SpringUser

    private val testSecret = "dGhlLWhpZGRlbi1tZXNzYWdlLWlzLXRoYXQtdGhlcmUtaXMtbm8taGlkZGVuLW1lc3NhZ2U="
    private val accessTokenExpirationMs = 900000L // 15 minutes
    private val refreshTokenExpirationMs = 604800000L // 7 days

    @BeforeEach
    fun setUp() {
        jwtProperties = JwtProperties(
            secretKey = testSecret,
            accessTokenExpirationMs = accessTokenExpirationMs,
            refreshTokenExpirationMs = refreshTokenExpirationMs,
        )
        jwtService = JwtService(jwtProperties)
        secretKey = Keys.hmacShaKeyFor(testSecret.toByteArray())

        existingUser = User(
            email = "existing_user@example.test",
            passwordHash = "EncodedUserPassword123",
            roles = mutableSetOf(Role.USER, Role.ADMIN),
        )

        existingUserDetails = SpringUser(
            existingUser.email,
            existingUser.passwordHash,
            listOf(SimpleGrantedAuthority("ROLE_USER"), SimpleGrantedAuthority("ROLE_ADMIN")),
        )
    }

    @Test
    fun `generateToken should create a valid JWT with correct subject, roles, and expiration`() {
        // Arrange
        val now = Date()

        // Act
        val token = jwtService.generateToken(existingUser)

        // Assert
        assertThat(token).isNotNull().isNotEmpty()

        val claims = parseTokenClaims(token)
        val expirationDate = claims.expiration
        val issuedAtDate = claims.issuedAt

        assertThat(claims.subject).isEqualTo(existingUser.email)

        @Suppress("UNCHECKED_CAST")
        val rolesClaim = claims["roles"] as List<String>
        assertThat(rolesClaim).containsExactlyInAnyOrder(Role.USER.name, Role.ADMIN.name)

        assertThat(issuedAtDate).isCloseTo(now, 1000) // Within 1 second
        val expectedExpiration = Date(issuedAtDate.time + accessTokenExpirationMs)
        assertThat(expirationDate).isCloseTo(expectedExpiration, 1000) // Within 1 second
    }

    @Test
    fun `extractUsername should return correct email from a valid token`() {
        // Arrange
        val token = jwtService.generateToken(existingUser)

        // Act
        val extractedUsername = jwtService.extractUsername(token)

        // Assert
        assertThat(extractedUsername).isEqualTo(existingUser.email)
    }

    @Test
    fun `extractUsername should return null for an expired token`() {
        // Arrange
        val expiredToken = generateExpiredToken(existingUser)

        // Act
        val extractedUsername = jwtService.extractUsername(expiredToken)

        // Assert
        assertThat(extractedUsername).isNull()
    }

    @Test
    fun `isTokenValid should return true for a valid, non-expired token and matching user details`() {
        // Arrange
        val token = jwtService.generateToken(existingUser)

        // Act
        val isValid = jwtService.isTokenValid(token, existingUserDetails)

        // Assert
        assertThat(isValid).isTrue
    }

    @Test
    fun `isTokenValid should return false when username does not match`() {
        // Arrange
        val token = jwtService.generateToken(existingUser)
        val otherUserDetails = SpringUser(
            "different_user@example.test",
            "EncodedDifferentPassword123",
            emptyList()
        )

        // Act
        val isValid = jwtService.isTokenValid(token, otherUserDetails)

        // Assert
        assertThat(isValid).isFalse
    }

    @Test
    fun `isTokenValid should return false for an expired token`() {
        // Arrange
        val expiredToken = generateExpiredToken(existingUser)

        // Act
        val isValid = jwtService.isTokenValid(expiredToken, existingUserDetails)

        // Assert
        assertThat(isValid).isFalse
    }

    @Test
    fun `isTokenExpired should return true for an expired token`() {
        // Arrange
        val expiredToken = generateExpiredToken(existingUser)

        // Act
        val isExpired = jwtService.isTokenExpired(expiredToken)

        // Assert
        assertThat(isExpired).isTrue
    }

    @Test
    fun `isTokenExpired should return false for a valid, non-expired token`() {
        // Arrange
        val validToken = jwtService.generateToken(existingUser)

        // Act
        val isExpired = jwtService.isTokenExpired(validToken)

        // Assert
        assertThat(isExpired).isFalse
    }

    private fun parseTokenClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    private fun generateExpiredToken(user: User): String {
        val now = Date()
        val expiredDate = Date(now.time - 1000) // 1 second in the past

        return Jwts.builder()
            .subject(user.email)
            .issuedAt(Date(now.time - accessTokenExpirationMs - 1000))
            .expiration(expiredDate)
            .signWith(secretKey)
            .compact()
    }
}
