package com.sandbox.jwt.auth

import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerVerificationIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    private val verificationEndpointPath = "/api/v1/auth/verify-email"

    @Test
    fun `GET verify-email should activate user and return 200 OK for valid token`() {
        // Arrange: Create an existing user directly in the database
        val validToken = UUID.randomUUID().toString()
        val user = User(
            email = "not_verified_user@example.test",
            password = "UserPassword123",
            isActive = false,
            emailVerificationToken = validToken,
            emailVerificationTokenExpiry = Instant.now().plus(1, ChronoUnit.DAYS)
        )
        userRepository.save(user)

        // Act & Assert
        mockMvc.get(verificationEndpointPath) {
            param("token", validToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { value("Email verified successfully. You can now log in.") }
        }

        // Verify database state
        val updatedUser = userRepository.findById(user.id).get()
        assertThat(updatedUser.isActive).isTrue()
        assertThat(updatedUser.emailVerificationToken).isNull()
        assertThat(updatedUser.emailVerificationTokenExpiry).isNull()
    }

    @Test
    fun `GET verify-email should return 400 Bad Request when token is missing`() {
        mockMvc.get(verificationEndpointPath)
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("Validation Failed") }
                jsonPath("$.messages") { value(hasItem("The verification token cannot be blank.")) }
            }
    }

    @Test
    fun `GET verify-email should return 400 Bad Request when token is blank`() {
        mockMvc.get(verificationEndpointPath) {
            param("token", "")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Validation Failed") }
            jsonPath("$.messages") { value(hasItem("The verification token cannot be blank.")) }
        }
    }

    @Test
    fun `GET verify-email should return 400 Bad Request when token format is invalid`() {
        mockMvc.get(verificationEndpointPath) {
            param("token", "not-a-valid-uuid")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Validation Failed") }
            jsonPath("$.messages") { value(hasItem("The verification token format is invalid.")) }
        }
    }

    @Test
    fun `GET verify-email should return 400 Bad Request for non-existent token`() {
        val nonExistentToken = UUID.randomUUID().toString()
        mockMvc.get(verificationEndpointPath) {
            param("token", nonExistentToken)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("The verification token is invalid.") }
        }
    }

    @Test
    fun `GET verify-email should return 400 Bad Request for expired token`() {
        // Arrange: Create a user with an expired token
        val expiredToken = UUID.randomUUID().toString()
        val expiredUser = User(
            email = "not_verified_user@example.test",
            password = "UserPassword123",
            emailVerificationToken = expiredToken,
            emailVerificationTokenExpiry = Instant.now().minus(1, ChronoUnit.DAYS)
        )
        userRepository.save(expiredUser)

        // Act & Assert
        mockMvc.get(verificationEndpointPath) {
            param("token", expiredToken)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("The verification token has expired.") }
        }
    }
}
