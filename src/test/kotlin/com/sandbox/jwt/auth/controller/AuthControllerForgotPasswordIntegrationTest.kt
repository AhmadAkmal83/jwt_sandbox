package com.sandbox.jwt.auth.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sandbox.jwt.auth.dto.PasswordResetRequest
import com.sandbox.jwt.mail.MailService
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerForgotPasswordIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @MockitoBean
    private lateinit var mailService: MailService

    private lateinit var existingUser: User

    private val forgotPasswordEndpointPath = "/api/v1/auth/forgot-password"

    @BeforeEach
    fun setUp() {
        existingUser = User(
            email = "existing_user@example.test",
            passwordHash = passwordEncoder.encode("UserPassword123"),
            isVerified = true
        )
        userRepository.save(existingUser)
    }

    @Test
    fun `POST forgot-password should return 200 OK and send email for existing user`() {
        // Arrange
        val request = PasswordResetRequest(existingUser.email)

        // Act & Assert
        mockMvc.post(forgotPasswordEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("If an account with that email exists, a password reset link has been sent.") }
        }

        // Verify database state
        val updatedUser = userRepository.findByEmail(existingUser.email).get()
        assertThat(updatedUser.passwordResetToken).isNotNull()
        assertThat(updatedUser.passwordResetTokenExpiry).isNotNull()

        // Verify that the mail service was called
        verify(mailService).sendPasswordResetEmail(updatedUser)
    }

    @Test
    fun `POST forgot-password should return 200 OK and not send email for non-existent user`() {
        // Arrange
        val nonExistentEmail = "non_existent_user@example.test"
        val request = PasswordResetRequest(nonExistentEmail)

        // Act & Assert
        mockMvc.post(forgotPasswordEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("If an account with that email exists, a password reset link has been sent.") }
        }

        // Verify that the mail service was not called
        verify(mailService, never()).sendPasswordResetEmail(any())
    }

    @Test
    fun `POST forgot-password should return 422 Unprocessable Entity for blank email`() {
        // Arrange
        val request = PasswordResetRequest(" ")

        // Act & Assert
        mockMvc.post(forgotPasswordEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.message") { value("The given data was invalid.") }
            jsonPath("$.errors.email") { value(Matchers.hasItem("Email cannot be blank.")) }
        }
    }

    @Test
    fun `POST forgot-password should return 422 Unprocessable Entity for invalid email format`() {
        // Arrange
        val request = PasswordResetRequest("invalid-email")

        // Act & Assert
        mockMvc.post(forgotPasswordEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.message") { value("The given data was invalid.") }
            jsonPath("$.errors.email") { value(Matchers.hasItem("Email format is invalid.")) }
        }
    }
}
