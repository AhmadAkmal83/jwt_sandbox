package com.sandbox.jwt.auth.service

import com.sandbox.jwt.auth.domain.RefreshToken
import com.sandbox.jwt.auth.dto.LoginRequest
import com.sandbox.jwt.auth.dto.RegisterRequest
import com.sandbox.jwt.auth.exception.AccountNotVerifiedException
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
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.Optional
import org.springframework.security.core.userdetails.User as SpringUser

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @Mock
    private lateinit var mailService: MailService

    @Mock
    private lateinit var jwtService: JwtService

    @Mock
    private lateinit var refreshTokenService: RefreshTokenService

    @InjectMocks
    private lateinit var authService: AuthService

    @Captor
    private lateinit var userCaptor: ArgumentCaptor<User>

    @Test
    fun `registerUser should create and save user, and send verification email when email is not taken`() {
        // Arrange
        val request = RegisterRequest("new_user@example.test", "UserPassword123")
        val encodedPassword = "EncodedUserPassword123"

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
        assertThat(savedUser.passwordHash).isEqualTo(encodedPassword)
        assertThat(savedUser.roles).containsExactly(Role.USER)
        assertThat(savedUser.isVerified).isFalse
        assertThat(savedUser.emailVerificationToken).isNotNull
        assertThat(savedUser.emailVerificationTokenExpiry).isNotNull

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

    @Test
    fun `loginUser should return tokens for valid and verified user`() {
        // Arrange
        val request = LoginRequest("verified_user@example.test", "UserPassword123")
        val user = User(
            email = request.email,
            passwordHash = "EncodedUserPassword123",
            isVerified = true
        )
        val accessToken = "test-access-token"
        val refreshToken = RefreshToken(
            user = user,
            token = "test-refresh-token",
            expiryDate = Instant.now()
        )

        whenever(userRepository.findByEmail(request.email)).thenReturn(Optional.of(user))
        whenever(passwordEncoder.matches(request.password, user.passwordHash)).thenReturn(true)
        whenever(jwtService.generateToken(user)).thenReturn(accessToken)
        whenever(refreshTokenService.createRefreshToken(user)).thenReturn(refreshToken)

        // Act
        val result = authService.loginUser(request)

        // Assert
        assertThat(result.accessToken).isEqualTo(accessToken)
        assertThat(result.refreshToken).isEqualTo(refreshToken.token)
    }

    @Test
    fun `loginUser should throw BadCredentialsException for non-existent email`() {
        // Arrange
        val request = LoginRequest("non_existent@example.test", "UserPassword123")
        whenever(userRepository.findByEmail(request.email)).thenReturn(Optional.empty())

        // Act & Assert
        assertThatThrownBy { authService.loginUser(request) }
            .isInstanceOf(BadCredentialsException::class.java)
            .hasMessage("Invalid email or password.")
    }

    @Test
    fun `loginUser should throw BadCredentialsException for incorrect password of verified user`() {
        // Arrange
        val request = LoginRequest("verified_user@example.test", "wrong-password")
        val user = User(
            email = request.email,
            passwordHash = "encodedPassword",
            isVerified = true
        )
        whenever(userRepository.findByEmail(request.email)).thenReturn(Optional.of(user))
        whenever(passwordEncoder.matches(request.password, user.passwordHash)).thenReturn(false)

        // Act & Assert
        assertThatThrownBy { authService.loginUser(request) }
            .isInstanceOf(BadCredentialsException::class.java)
            .hasMessage("Invalid email or password.")
    }

    @Test
    fun `loginUser should throw AccountNotVerifiedException for unverified user`() {
        // Arrange
        val request = LoginRequest("unverified_user@example.test", "UserPassword123")
        val user = User(
            email = request.email,
            passwordHash = "encodedPassword",
            isVerified = false
        )
        whenever(userRepository.findByEmail(request.email)).thenReturn(Optional.of(user))
        whenever(passwordEncoder.matches(request.password, user.passwordHash)).thenReturn(true)

        // Act & Assert
        assertThatThrownBy { authService.loginUser(request) }
            .isInstanceOf(AccountNotVerifiedException::class.java)
            .hasMessage("Account is not verified. Please check your email.")
    }

    @Test
    fun `logoutUser should fetch user and call refreshTokenService logout`() {
        // Arrange
        val userEmail = "existing_user@example.test"
        val springUser = SpringUser(userEmail, "UserPassword123", emptyList())
        val authentication = UsernamePasswordAuthenticationToken(springUser, null)
        val domainUser = User(email = userEmail, passwordHash = "UserPassword123")

        whenever(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(domainUser))

        // Act
        authService.logoutUser(authentication)

        // Assert
        verify(userRepository).findByEmail(userEmail)
        verify(refreshTokenService).logout(domainUser)
    }

    @Test
    fun `logoutUser should throw UsernameNotFoundException if user from token is not found`() {
        // Arrange
        val userEmail = "non_existent_user@example.test"
        val springUser = SpringUser(userEmail, "UserPassword123", emptyList())
        val authentication = UsernamePasswordAuthenticationToken(springUser, null)

        whenever(userRepository.findByEmail(userEmail)).thenReturn(Optional.empty())

        // Act & Assert
        assertThatThrownBy { authService.logoutUser(authentication) }
            .isInstanceOf(UsernameNotFoundException::class.java)
            .hasMessage("User with email '${userEmail}' was not found.")

        // Verify
        verify(refreshTokenService, never()).logout(any())
    }
}
