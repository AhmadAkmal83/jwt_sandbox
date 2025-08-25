package com.sandbox.jwt.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.sandbox.jwt.user.domain.Role
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.auth.dto.RegisterRequest
import com.sandbox.jwt.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.hasItem
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `POST register should create a new user and return 201 Created`() {
        // Arrange
        val request = RegisterRequest("valid_email@example.com", "ValidPassword123")

        // Act & Assert
        mockMvc.post("/api/v1/auth/register") {
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
    }

    @Test
    fun `POST register should return 409 Conflict when email already exists`() {
        // Arrange: Create an existing user directly in the database
        val existingUser = User(email = "valid_email@example.com", password = "ValidPassword123")
        userRepository.save(existingUser)

        val request = RegisterRequest("valid_email@example.com", "DifferentValidPassword")

        // Act & Assert
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("A user with the email 'valid_email@example.com' already exists.") }
        }
    }

    @Test
    fun `POST register should return 400 Bad Request for blank email`() {
        val request = RegisterRequest("", "ValidPassword123")

        mockMvc.post("/api/v1/auth/register") {
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
        mockMvc.post("/api/v1/auth/register") {
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
        val request = RegisterRequest("valid_email@example.com", "")

        mockMvc.post("/api/v1/auth/register") {
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
        val request = RegisterRequest("valid_email@example.com", "short")

        mockMvc.post("/api/v1/auth/register") {
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
        val request = RegisterRequest("valid_email@example.com", "passwordLongerThanMaxCharacters")

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Validation Failed") }
            jsonPath("$.messages") { value(hasItem("Password must be between 8 and 30 characters")) }
        }
    }
}
