package com.sandbox.jwt.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.sandbox.jwt.auth.dto.RegisterRequest
import com.sandbox.jwt.mail.MailService
import com.sandbox.jwt.user.domain.Role
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.hasItem
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
class AuthControllerRegistrationIntegrationTest {

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

    private val registrationEndpointPath = "/api/v1/auth/register"

    @Test
    fun `POST register should create a new user, attempt to send verification email, and return 201 Created`() {
        // Arrange
        val request = RegisterRequest("new_user@example.test", "UserPassword123")

        // Act & Assert
        mockMvc.post(registrationEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { isNotEmpty() }
            jsonPath("$.email") { value(request.email) }
            jsonPath("$.roles") { value(hasItem(Role.USER.name)) }
            jsonPath("$.password") { doesNotExist() }
        }

        // Verify database state
        val userInDb = userRepository.findByEmail(request.email).get()
        assertThat(userInDb.email).isEqualTo(request.email)
        assertThat(userInDb.isVerified).isFalse
        assertThat(userInDb.roles).containsExactly(Role.USER)
        assertThat(passwordEncoder.matches(request.password, userInDb.passwordHash)).isTrue

        // Verify that the mail service was called
        verify(mailService).sendVerificationEmail(userInDb)
    }

    @Test
    fun `POST register should return 409 Conflict and not send verification email when email already exists`() {
        // Arrange
        val existingUser = User(
            email = "existing_user@example.test",
            passwordHash = passwordEncoder.encode("UserPassword123"),
        )
        userRepository.save(existingUser)

        val request = RegisterRequest("existing_user@example.test", "DifferentPassword123")

        // Act & Assert
        mockMvc.post(registrationEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.message") { value("A user with the email 'existing_user@example.test' already exists.") }
        }

        // Verify that the mail service was not called
        verify(mailService, never()).sendVerificationEmail(any())
    }

    @Test
    fun `POST register should return 422 Unprocessable Entity for blank email`() {
        // Arrange
        val request = RegisterRequest("", "ValidPassword123")

        // Act & Assert
        mockMvc.post(registrationEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.message") { value("The given data was invalid.") }
            jsonPath("$.errors.email") { value(hasItem("Email cannot be blank.")) }
        }
    }

    @Test
    fun `POST register should return 422 Unprocessable Entity for invalid email format`() {
        // Arrange
        val request = RegisterRequest("invalid_email", "ValidPassword123")

        // Act & Assert
        mockMvc.post(registrationEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.message") { value("The given data was invalid.") }
            jsonPath("$.errors.email") { value(hasItem("Email format is invalid.")) }
        }
    }

    @Test
    fun `POST register should return 422 Unprocessable Entity for blank password`() {
        // Arrange
        val request = RegisterRequest("valid_email@example.test", "")

        // Act & Assert
        mockMvc.post(registrationEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.message") { value("The given data was invalid.") }
            jsonPath("$.errors.password") { value(hasItem("Password cannot be blank.")) }
        }
    }

    @Test
    fun `POST register should return 422 Unprocessable Entity for password shorter than 8 characters`() {
        // Arrange
        val request = RegisterRequest("valid_email@example.test", "Short")

        // Act & Assert
        mockMvc.post(registrationEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.message") { value("The given data was invalid.") }
            jsonPath("$.errors.password") { value(hasItem("Password must be between 8 and 30 characters.")) }
        }
    }

    @Test
    fun `POST register should return 422 Unprocessable Entity for password longer than 30 characters`() {
        // Arrange
        val request = RegisterRequest("valid_email@example.test", "PasswordLongerThanMaxCharacters")

        // Act & Assert
        mockMvc.post(registrationEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.message") { value("The given data was invalid.") }
            jsonPath("$.errors.password") { value(hasItem("Password must be between 8 and 30 characters.")) }
        }
    }

    @Test
    fun `POST register should return 422 Unprocessable Entity with correct errors for multiple errored fields`() {
        // Arrange
        val request = RegisterRequest("", "")

        // Act & Assert
        mockMvc.post(registrationEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.message") { value("The given data was invalid.") }
            jsonPath("$.errors.email") { value(hasItem("Email cannot be blank.")) }
            jsonPath("$.errors.password") { value(hasItem("Password cannot be blank.")) }
        }
    }
}
