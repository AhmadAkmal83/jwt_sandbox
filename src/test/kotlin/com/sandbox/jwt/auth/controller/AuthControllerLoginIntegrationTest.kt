package com.sandbox.jwt.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sandbox.jwt.auth.dto.LoginRequest
import com.sandbox.jwt.auth.repository.RefreshTokenRepository
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.assertj.core.api.Assertions
import org.hamcrest.Matchers
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
import kotlin.collections.get

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerLoginIntegrationTest {

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

    private val loginEndpointPath = "/api/v1/auth/login"
    private val userPassword = "UserPassword123"
    private lateinit var verifiedUser: User
    private lateinit var unverifiedUser: User

    @BeforeEach
    fun setUp() {
        verifiedUser = User(
            email = "verified_user@example.test",
            passwordHash = passwordEncoder.encode(userPassword),
            isVerified = true
        )
        userRepository.save(verifiedUser)

        unverifiedUser = User(
            email = "unverified_user@example.test",
            passwordHash = passwordEncoder.encode(userPassword),
            isVerified = false
        )
        userRepository.save(unverifiedUser)
    }

    @Test
    fun `POST login should return 200 OK with tokens for valid credentials and verified user`() {
        // Arrange
        val request = LoginRequest(verifiedUser.email, userPassword)

        // Act & Assert
        mockMvc.post(loginEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isNotEmpty() }
            jsonPath("$.refreshToken") { isNotEmpty() }
        }

        // Verify database state
        val refreshTokenInDb = refreshTokenRepository.findByUser(verifiedUser)
        Assertions.assertThat(refreshTokenInDb).isPresent
    }

    @Test
    fun `POST login should return 401 Unauthorized for non-existent user`() {
        // Arrange
        val request = LoginRequest("non_existent_user@example.test", userPassword)

        // Act & Assert
        mockMvc.post(loginEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.message") { value("Invalid email or password.") }
        }
    }

    @Test
    fun `POST login should return 401 Unauthorized for incorrect password`() {
        // Arrange
        val request = LoginRequest(verifiedUser.email, "wrong-password")

        // Act & Assert
        mockMvc.post(loginEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.message") { value("Invalid email or password.") }
        }
    }

    @Test
    fun `POST login should return 401 Unauthorized for unverified user`() {
        // Arrange
        val request = LoginRequest(unverifiedUser.email, userPassword)

        // Act & Assert
        mockMvc.post(loginEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.message") { value("Account is not verified. Please check your email.") }
        }
    }

    @Test
    fun `POST login should issue a new refresh token and delete the old one on subsequent logins`() {
        // Arrange
        val request = LoginRequest(verifiedUser.email, userPassword)

        // First login
        val resultAction = mockMvc.post(loginEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect { status { isOk() } }

        val responseString = resultAction.andReturn().response.contentAsString
        val firstResponse = objectMapper.readValue(responseString, Map::class.java)
        val firstRefreshToken = firstResponse["refreshToken"] as String
        Assertions.assertThat(refreshTokenRepository.findByToken(firstRefreshToken)).isPresent

        // Second login Act & Assert
        mockMvc.post(loginEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.refreshToken") { value(Matchers.not(firstRefreshToken)) }
        }

        // Verify database state
        Assertions.assertThat(refreshTokenRepository.findByToken(firstRefreshToken)).isNotPresent
        Assertions.assertThat(refreshTokenRepository.count()).isEqualTo(1)
    }
}
