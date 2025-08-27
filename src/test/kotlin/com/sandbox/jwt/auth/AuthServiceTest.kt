package com.sandbox.jwt.auth

import com.sandbox.jwt.auth.dto.RegisterRequest
import com.sandbox.jwt.auth.exception.EmailAlreadyExistsException
import com.sandbox.jwt.mail.MailService
import com.sandbox.jwt.user.domain.Role
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
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

    @Mock
    private lateinit var mailService: MailService

    @InjectMocks
    private lateinit var authService: AuthService

    @Captor
    private lateinit var userCaptor: ArgumentCaptor<User>

    @Test
    fun `registerUser should create and save user, and send verification email when email is not taken`() {
        // Arrange
        val request = RegisterRequest("non_existing_user@example.test", "ValidPassword123")
        val encodedPassword = "EncodedValidPassword123"

        whenever(userRepository.existsByEmail(request.email)).thenReturn(false)
        whenever(passwordEncoder.encode(request.password)).thenReturn(encodedPassword)
        // Mock the save call to return the user that was passed to it
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }

        // Act
        val result = authService.registerUser(request)

        // Assert
        verify(userRepository).save(userCaptor.capture())
        val savedUser = userCaptor.value

        assertThat(result).isEqualTo(savedUser)
        assertThat(savedUser.email).isEqualTo(request.email)
        assertThat(savedUser.password).isEqualTo(encodedPassword)
        assertThat(savedUser.roles).containsExactly(Role.USER)
        assertThat(savedUser.isActive).isFalse()
        assertThat(savedUser.emailVerificationToken).isNotNull()
        assertThat(savedUser.emailVerificationTokenExpiry).isNotNull()

        // Verify email is sent
        verify(mailService).sendVerificationEmail(savedUser)
    }

    @Test
    fun `registerUser should throw EmailAlreadyExistsException when email is already taken`() {
        // Arrange
        val request = RegisterRequest("existing_user@example.test", "UserPassword123")
        whenever(userRepository.existsByEmail(request.email)).thenReturn(true)

        // Act & Assert
        assertThatThrownBy { authService.registerUser(request) }
            .isInstanceOf(EmailAlreadyExistsException::class.java)
            .hasMessage("A user with the email 'existing_user@example.test' already exists.")

        // Verify that no further processing occurred
        verify(passwordEncoder, never()).encode(any())
        verify(userRepository, never()).save(any())

        // Verify email is not sent
        verify(mailService, never()).sendVerificationEmail(any())
    }
}
