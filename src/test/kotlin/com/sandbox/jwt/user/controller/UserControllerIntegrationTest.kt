package com.sandbox.jwt.user.controller

import com.sandbox.jwt.auth.service.JwtService
import com.sandbox.jwt.user.domain.Role
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var jwtService: JwtService

    private val meEndpointPath = "/api/v1/users/me"
    private lateinit var existingUser: User
    private lateinit var validToken: String

    @BeforeEach
    fun setUp() {
        existingUser = User(
            email = "existing_user@example.test",
            passwordHash = passwordEncoder.encode("UserPassword123"),
            roles = mutableSetOf(Role.USER),
            isVerified = true
        )
        userRepository.save(existingUser)

        validToken = jwtService.generateToken(existingUser)
    }

    @Test
    fun `GET me should return 401 Unauthorized when no token is provided`() {
        // Act & Assert
        mockMvc.get(meEndpointPath)
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `GET me should return 401 Unauthorized when an invalid token is provided`() {
        // Arrange
        val invalidToken = "Bearer invalid-token-string"

        // Act & Assert
        mockMvc.get(meEndpointPath) {
            header("Authorization", invalidToken)
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `GET me should return 200 OK with user data for a valid token`() {
        // Act & Assert
        mockMvc.get(meEndpointPath) {
            header("Authorization", "Bearer $validToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(existingUser.id) }
            jsonPath("$.email") { value(existingUser.email) }
            jsonPath("$.roles") { value(hasItem(Role.USER.name)) }
            jsonPath("$.password") { doesNotExist() }
        }
    }
}
