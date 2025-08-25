package com.sandbox.jwt.auth

import com.sandbox.jwt.auth.dto.RegisterRequest
import com.sandbox.jwt.auth.exception.EmailAlreadyExistsException
import com.sandbox.jwt.user.domain.Role
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @InjectMocks
    private lateinit var authService: AuthService

    @Captor
    private lateinit var userCaptor: ArgumentCaptor<User>

    @Test
    fun `registerUser should create and save a new user when email is not taken`() {
        // Arrange
        val request = RegisterRequest("valid_email@example.com", "ValidPassword123")
        val encodedPassword = "encodedValidPassword123"

        whenever(userRepository.existsByEmail(request.email)).thenReturn(false)
        whenever(passwordEncoder.encode(request.password)).thenReturn(encodedPassword)
        // Mock the save call to return the user that was passed to it
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }

        // Act
        val result = authService.registerUser(request)

        // Assert
        verify(userRepository).save(userCaptor.capture())
        val savedUser = userCaptor.value

        Assertions.assertThat(result).isEqualTo(savedUser)
        Assertions.assertThat(savedUser.email).isEqualTo(request.email)
        Assertions.assertThat(savedUser.password).isEqualTo(encodedPassword)
        Assertions.assertThat(savedUser.roles).containsExactly(Role.USER)
        Assertions.assertThat(savedUser.isActive).isFalse()
        Assertions.assertThat(savedUser.emailVerificationToken).isNotNull()
        Assertions.assertThat(savedUser.emailVerificationTokenExpiry).isNotNull()
    }

    @Test
    fun `registerUser should throw EmailAlreadyExistsException when email is already taken`() {
        // Arrange
        val request = RegisterRequest("valid_email@example.com", "ValidPassword123")
        whenever(userRepository.existsByEmail(request.email)).thenReturn(true)

        // Act & Assert
        val exception = assertThrows<EmailAlreadyExistsException> {
            authService.registerUser(request)
        }

        Assertions.assertThat(exception.message)
            .isEqualTo("A user with the email 'valid_email@example.com' already exists.")

        // Verify that no further processing occurred
        verify(passwordEncoder, never()).encode(any())
        verify(userRepository, never()).save(any())
    }
}
