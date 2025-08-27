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
        assertThat(userInDb.isActive).isFalse()
        assertThat(userInDb.roles).containsExactly(Role.USER)
        assertThat(passwordEncoder.matches(request.password, userInDb.password)).isTrue()

        // Verify that the mail service was called
        verify(mailService).sendVerificationEmail(userInDb)
    }

    @Test
    fun `POST register should return 409 Conflict and not send verification email when email already exists`() {
        // Arrange: Create an existing user directly in the database
        val existingUser = User(
            email = "existing_user@example.test",
            password = passwordEncoder.encode("UserPassword123"),
        )
        userRepository.save(existingUser)

        val request = RegisterRequest("existing_user@example.test", "DifferentPassword123")

        // Act & Assert
        mockMvc.post(registrationEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("A user with the email 'existing_user@example.test' already exists.") }
        }

        // Verify that the mail service was not called
        verify(mailService, never()).sendVerificationEmail(any())
    }

    @Test
    fun `POST register should return 400 Bad Request for blank email`() {
        val request = RegisterRequest("", "ValidPassword123")

        mockMvc.post(registrationEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Validation Failed") }
            jsonPath("$.messages") { value(hasItem("Email cannot be blank")) }
        }
    }

    @Test
    fun `POST register should return 400 Bad Request for invalid email format`() {
        // Arrange
        val request = RegisterRequest("invalid_email", "ValidPassword123")

        // Act & Assert
        mockMvc.post(registrationEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Validation Failed") }
            jsonPath("$.messages") { value(hasItem("Email format is invalid")) }
        }
    }

    @Test
    fun `POST register should return 400 Bad Request for blank password`() {
        val request = RegisterRequest("valid_email@example.test", "")

        mockMvc.post(registrationEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Validation Failed") }
            jsonPath("$.messages") { value(hasItem("Password cannot be blank")) }
        }
    }

    @Test
    fun `POST register should return 400 Bad Request for password shorter than 8 characters`() {
        val request = RegisterRequest("valid_email@example.test", "Short")

        mockMvc.post(registrationEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Validation Failed") }
            jsonPath("$.messages") { value(hasItem("Password must be between 8 and 30 characters")) }
        }
    }

    @Test
    fun `POST register should return 400 Bad Request for password longer than 30 characters`() {
        val request = RegisterRequest("valid_email@example.test", "PasswordLongerThanMaxCharacters")

        mockMvc.post(registrationEndpointPath) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Validation Failed") }
            jsonPath("$.messages") { value(hasItem("Password must be between 8 and 30 characters")) }
        }
    }
}
