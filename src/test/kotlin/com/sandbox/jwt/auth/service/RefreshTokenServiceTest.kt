package com.sandbox.jwt.auth.service

import com.sandbox.jwt.auth.config.JwtProperties
import com.sandbox.jwt.auth.domain.RefreshToken
import com.sandbox.jwt.auth.repository.RefreshTokenRepository
import com.sandbox.jwt.user.domain.User
import org.assertj.core.api.Assertions.assertThat
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
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class RefreshTokenServiceTest {

    @Mock
    private lateinit var refreshTokenRepository: RefreshTokenRepository

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
        refreshTokenService = RefreshTokenService(refreshTokenRepository, jwtProperties)
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
        assertThat(savedToken.token).isNotNull
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
}
