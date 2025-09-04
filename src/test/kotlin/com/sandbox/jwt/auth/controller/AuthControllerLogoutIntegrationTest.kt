package com.sandbox.jwt.auth.controller

import com.sandbox.jwt.auth.domain.RefreshToken
import com.sandbox.jwt.auth.repository.RefreshTokenRepository
import com.sandbox.jwt.auth.service.JwtService
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
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
class AuthControllerLogoutIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    private val logoutEndpointPath = "/api/v1/auth/logout"
    private lateinit var existingUser: User
    private lateinit var validAccessToken: String

    @BeforeEach
    fun setUp() {
        existingUser = User(
            email = "verified_user@example.test",
            passwordHash = passwordEncoder.encode("UserPassword123"),
            isVerified = true
        )
        userRepository.save(existingUser)

        validAccessToken = jwtService.generateToken(existingUser)

        val refreshToken = RefreshToken(
            user = existingUser,
            token = UUID.randomUUID().toString(),
            expiryDate = Instant.now().plus(1, ChronoUnit.DAYS)
        )
        refreshTokenRepository.save(refreshToken)
    }

    @Test
    fun `POST logout should return 200 OK and delete refresh token for authenticated user`() {
        // Act & Assert
        mockMvc.post(logoutEndpointPath) {
            header("Authorization", "Bearer $validAccessToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("Logged out successfully.") }
        }

        // Verify database state
        Assertions.assertThat(refreshTokenRepository.findByUser(existingUser)).isNotPresent()
    }

    @Test
    fun `POST logout should return 401 Unauthorized for unauthenticated user`() {
        // Act & Assert
        mockMvc.post(logoutEndpointPath)
            .andExpect {
                status { isUnauthorized() }
            }
    }
}
