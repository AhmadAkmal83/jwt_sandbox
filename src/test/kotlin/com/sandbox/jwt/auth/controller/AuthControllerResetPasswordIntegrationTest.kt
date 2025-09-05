package com.sandbox.jwt.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sandbox.jwt.auth.domain.RefreshToken
import com.sandbox.jwt.auth.dto.PasswordResetConsumptionRequest
import com.sandbox.jwt.auth.repository.RefreshTokenRepository
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers
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
class AuthControllerResetPasswordIntegrationTest {

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

    private val resetPasswordEndpointPath = "/api/v1/auth/reset-password"

    @Test
    fun `POST reset-password should return 200 OK and update password for valid token`() {
        // Arrange
        val resetToken = UUID.randomUUID().toString()
        val oldPassword = "OldPassword123"
        val newPassword = "NewPassword123"

        val user = User(
            email = "existing_user@example.test",
            passwordHash = passwordEncoder.encode(oldPassword),
            isVerified = true,
            passwordResetToken = resetToken,
            passwordResetTokenExpiry = Instant.now().plus(1, ChronoUnit.HOURS)
        )
        userRepository.save(user)

        // Also save a refresh token to ensure it gets deleted
        val refreshToken = RefreshToken(
            user = user,
            token = UUID.randomUUID().toString(),
            expiryDate = Instant.now().plus(1, ChronoUnit.DAYS)
        )
        refreshTokenRepository.save(refreshToken)

        val request = PasswordResetConsumptionRequest(token = resetToken, newPassword = newPassword)

        // Act & Assert
        mockMvc.post(resetPasswordEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("Password has been reset successfully. You can now log in with your new password.") }
        }

        // Verify database state
        val updatedUser = userRepository.findById(user.id).get()
        assertThat(passwordEncoder.matches(newPassword, updatedUser.passwordHash)).isTrue()
        assertThat(passwordEncoder.matches(oldPassword, updatedUser.passwordHash)).isFalse()
        assertThat(updatedUser.passwordResetToken).isNull()
        assertThat(updatedUser.passwordResetTokenExpiry).isNull()
        assertThat(refreshTokenRepository.findByUser(user)).isNotPresent()
    }

    @Test
    fun `POST reset-password should return 400 Bad Request for non-existent token`() {
        // Arrange
        val nonExistentToken = UUID.randomUUID().toString()
        val request = PasswordResetConsumptionRequest(token = nonExistentToken, newPassword = "NewPassword123")

        // Act & Assert
        mockMvc.post(resetPasswordEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("The password reset token is invalid.") }
        }
    }

    @Test
    fun `POST reset-password should return 400 Bad Request for expired token`() {
        // Arrange
        val expiredToken = UUID.randomUUID().toString()
        val user = User(
            email = "existing_user@example.test",
            passwordHash = "OldPassword123",
            isVerified = true,
            passwordResetToken = expiredToken,
            passwordResetTokenExpiry = Instant.now().minus(1, ChronoUnit.MINUTES)
        )
        userRepository.save(user)

        val request = PasswordResetConsumptionRequest(token = expiredToken, newPassword = "NewPassword123")

        // Act & Assert
        mockMvc.post(resetPasswordEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("The password reset token has expired.") }
        }
    }

    @Test
    fun `POST reset-password should return 422 Unprocessable Entity for invalid token format`() {
        // Arrange
        val request = PasswordResetConsumptionRequest(token = "invalid-token", newPassword = "NewPassword123")

        // Act & Assert
        mockMvc.post(resetPasswordEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.message") { value("The given data was invalid.") }
            jsonPath("$.errors.token") { value(Matchers.hasItem("The reset token format is invalid.")) }
        }
    }

    @Test
    fun `POST reset-password should return 422 Unprocessable Entity for password shorter than 8 characters`() {
        // Arrange
        val request = PasswordResetConsumptionRequest(token = UUID.randomUUID().toString(), newPassword = "Short")

        // Act & Assert
        mockMvc.post(resetPasswordEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.message") { value("The given data was invalid.") }
            jsonPath("$.errors.newPassword") { value(Matchers.hasItem("New password must be between 8 and 30 characters.")) }
        }
    }

    @Test
    fun `POST reset-password should return 422 Unprocessable Entity for password longer than 30 characters`() {
        // Arrange
        val request = PasswordResetConsumptionRequest(
            token = UUID.randomUUID().toString(),
            newPassword = "PasswordLongerThanMaxCharacters"
        )

        // Act & Assert
        mockMvc.post(resetPasswordEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.message") { value("The given data was invalid.") }
            jsonPath("$.errors.newPassword") { value(Matchers.hasItem("New password must be between 8 and 30 characters.")) }
        }
    }

    @Test
    fun `POST reset-password should return 422 Unprocessable Entity for multiple invalid fields`() {
        // Arrange
        val request = PasswordResetConsumptionRequest(token = " ", newPassword = " ")

        // Act & Assert
        mockMvc.post(resetPasswordEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.message") { value("The given data was invalid.") }
            jsonPath("$.errors.token") { value(Matchers.hasItem("The reset token cannot be blank.")) }
            jsonPath("$.errors.newPassword") { value(Matchers.hasItem("New password cannot be blank.")) }
        }
    }
}
