package com.sandbox.jwt.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sandbox.jwt.auth.domain.RefreshToken
import com.sandbox.jwt.auth.dto.TokenRefreshRequest
import com.sandbox.jwt.auth.repository.RefreshTokenRepository
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerRefreshIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    private val refreshEndpointPath = "/api/v1/auth/refresh"
    private lateinit var verifiedUser: User

    @BeforeEach
    fun setUp() {
        verifiedUser = User(
            email = "verified_user@example.test",
            passwordHash = passwordEncoder.encode("UserPassword123"),
            isVerified = true
        )
        userRepository.save(verifiedUser)
    }

    @Test
    fun `POST refresh should return 200 OK with a new access token for a valid refresh token`() {
        // Arrange
        val validRefreshToken = RefreshToken(
            user = verifiedUser,
            token = UUID.randomUUID().toString(),
            expiryDate = Instant.now().plus(1, ChronoUnit.DAYS)
        )
        refreshTokenRepository.save(validRefreshToken)

        val request = TokenRefreshRequest(token = validRefreshToken.token)

        // Act & Assert
        mockMvc.post(refreshEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isNotEmpty() }
        }
    }

    @Test
    fun `POST refresh should return 400 Bad Request for a non-existent refresh token`() {
        // Arrange
        val nonExistentToken = UUID.randomUUID().toString()
        val request = TokenRefreshRequest(token = nonExistentToken)

        // Act & Assert
        mockMvc.post(refreshEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("The refresh token is invalid.") }
        }
    }

    @Test
    fun `POST refresh should return 400 Bad Request and delete an expired refresh token`() {
        // Arrange
        val expiredRefreshToken = RefreshToken(
            user = verifiedUser,
            token = UUID.randomUUID().toString(),
            expiryDate = Instant.now().minus(1, ChronoUnit.DAYS)
        )
        refreshTokenRepository.save(expiredRefreshToken)

        val request = TokenRefreshRequest(token = expiredRefreshToken.token)

        // Act & Assert
        mockMvc.post(refreshEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Refresh token has expired. Please log in again.") }
        }

        // Verify database state
        Assertions.assertThat(refreshTokenRepository.findByToken(expiredRefreshToken.token)).isNotPresent()
    }

    @Test
    fun `POST refresh should return 422 Unprocessable Entity for a blank refresh token`() {
        // Arrange
        val request = TokenRefreshRequest(token = " ")

        // Act & Assert
        mockMvc.post(refreshEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.message") { value("The given data was invalid.") }
            jsonPath("$.errors.token") { value("Refresh token cannot be blank.") }
        }
    }
}
