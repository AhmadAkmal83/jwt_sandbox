package com.sandbox.jwt.auth.service

import com.sandbox.jwt.auth.config.JwtProperties
import com.sandbox.jwt.auth.domain.RefreshToken
import com.sandbox.jwt.auth.exception.InvalidVerificationTokenException
import com.sandbox.jwt.auth.exception.VerificationTokenExpiredException
import com.sandbox.jwt.auth.repository.RefreshTokenRepository
import com.sandbox.jwt.user.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RefreshTokenServiceTest {

    @Mock
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Mock
    private lateinit var jwtService: JwtService

    @Captor
    private lateinit var refreshTokenCaptor: ArgumentCaptor<RefreshToken>

    private lateinit var refreshTokenService: RefreshTokenService

    private val jwtProperties = JwtProperties(
        secretKey = "dGhlLWhpZGRlbi1tZXNzYWdlLWlzLXRoYXQtdGhlcmUtaXMtbm8taGlkZGVuLW1lc3NhZ2U=",
        accessTokenExpirationMs = 900000L, // 15 minutes
        refreshTokenExpirationMs = 604800000L, // 7 days
    )

    @BeforeEach
    fun setUp() {
        refreshTokenService = RefreshTokenService(refreshTokenRepository, jwtProperties, jwtService)
    }

    @Test
    fun `createRefreshToken should create and save a new token when none exists`() {
        // Arrange
        val user = User(id = 1L, email = "existing_user@example.test", passwordHash = "UserPassword123")
        whenever(refreshTokenRepository.findByUser(user)).thenReturn(Optional.empty())
        whenever(refreshTokenRepository.save(any<RefreshToken>())).thenAnswer { it.arguments[0] }

        // Act & Verify
        val result = refreshTokenService.createRefreshToken(user)

        verify(refreshTokenRepository, never()).delete(any())
        verify(refreshTokenRepository, never()).flush()
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture())

        // Assert
        val savedToken = refreshTokenCaptor.value
        assertThat(result).isEqualTo(savedToken)
        assertThat(savedToken.user).isEqualTo(user)
        assertThat(savedToken.token).isNotNull()
        assertThat(savedToken.expiryDate).isAfter(result.expiryDate.minusMillis(jwtProperties.refreshTokenExpirationMs + 1000))
    }

    @Test
    fun `createRefreshToken should delete, flush, and then save when an existing token is found`() {
        // Arrange
        val user = User(id = 1L, email = "existing_user@example.test", passwordHash = "UserPassword123")
        val existingToken = RefreshToken(
            id = 99L,
            user = user,
            token = UUID.randomUUID().toString(),
            expiryDate = Instant.now()
        )
        whenever(refreshTokenRepository.findByUser(user)).thenReturn(Optional.of(existingToken))
        whenever(refreshTokenRepository.save(any<RefreshToken>())).thenAnswer { it.arguments[0] }

        // Act
        val result = refreshTokenService.createRefreshToken(user)

        // Verify order of deleting, flushing, then saving
        val inOrder = inOrder(refreshTokenRepository)
        inOrder.verify(refreshTokenRepository).delete(existingToken)
        inOrder.verify(refreshTokenRepository).flush()
        inOrder.verify(refreshTokenRepository).save(any<RefreshToken>())

        // Assert the result is a new token, not the old one
        assertThat(result.id).isNotEqualTo(existingToken.id)
        assertThat(result.token).isNotEqualTo(existingToken.token)
    }

    @Test
    fun `refreshAccessToken should return new access token for valid token`() {
        // Arrange
        val user = User(id = 1L, email = "existing_user@example.test", passwordHash = "UserPassword123")
        val refreshTokenString = UUID.randomUUID().toString()
        val refreshToken = RefreshToken(
            user = user,
            token = refreshTokenString,
            expiryDate = Instant.now().plus(1, ChronoUnit.DAYS)
        )
        val newAccessToken = "new-jwt-access-token"

        whenever(refreshTokenRepository.findByToken(refreshTokenString)).thenReturn(Optional.of(refreshToken))
        whenever(jwtService.generateToken(user)).thenReturn(newAccessToken)

        // Act
        val result = refreshTokenService.refreshAccessToken(refreshTokenString)

        // Assert
        assertThat(result).isEqualTo(newAccessToken)
        verify(refreshTokenRepository, never()).delete(any())
    }

    @Test
    fun `refreshAccessToken should throw InvalidVerificationTokenException for non-existent token`() {
        // Arrange
        val nonExistentToken = UUID.randomUUID().toString()
        whenever(refreshTokenRepository.findByToken(nonExistentToken)).thenReturn(Optional.empty())

        // Act & Assert
        assertThatThrownBy { refreshTokenService.refreshAccessToken(nonExistentToken) }
            .isInstanceOf(InvalidVerificationTokenException::class.java)
            .hasMessage("The refresh token is invalid.")

        verify(jwtService, never()).generateToken(any())
    }

    @Test
    fun `refreshAccessToken should throw VerificationTokenExpiredException and delete token when expired`() {
        // Arrange
        val user = User(id = 1L, email = "existing_user@example.test", passwordHash = "UserPassword123")
        val expiredTokenString = UUID.randomUUID().toString()
        val expiredToken = RefreshToken(
            user = user,
            token = expiredTokenString,
            expiryDate = Instant.now().minus(1, ChronoUnit.DAYS)
        )
        whenever(refreshTokenRepository.findByToken(expiredTokenString)).thenReturn(Optional.of(expiredToken))

        // Act & Assert
        assertThatThrownBy { refreshTokenService.refreshAccessToken(expiredTokenString) }
            .isInstanceOf(VerificationTokenExpiredException::class.java)
            .hasMessage("Refresh token has expired. Please log in again.")

        // Verify
        verify(refreshTokenRepository).delete(expiredToken)
        verify(jwtService, never()).generateToken(any())
    }

    @Test
    fun `logout should call deleteByUser on the repository`() {
        // Arrange
        val user = User(id = 1L, email = "existing_user@example.test", passwordHash = "UserPassword123")

        // Act
        refreshTokenService.logout(user)

        // Assert
        verify(refreshTokenRepository).deleteByUser(user)
    }
}
