package com.sandbox.jwt.auth.service

import com.sandbox.jwt.auth.domain.RefreshToken
import com.sandbox.jwt.auth.dto.LoginRequest
import com.sandbox.jwt.auth.dto.RegisterRequest
import com.sandbox.jwt.auth.exception.AccountNotVerifiedException
import com.sandbox.jwt.auth.exception.EmailAlreadyExistsException
import com.sandbox.jwt.auth.repository.RefreshTokenRepository
import com.sandbox.jwt.common.security.exception.InvalidTokenException
import com.sandbox.jwt.common.security.exception.TokenExpiredException
import com.sandbox.jwt.mail.MailService
import com.sandbox.jwt.user.domain.Role
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
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
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID
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

    @Mock
    private lateinit var refreshTokenRepository: RefreshTokenRepository

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
        assertThat(savedUser.isVerified).isFalse()
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

    @Test
    fun `verifyEmail should activate user and clear token for valid, non-expired token`() {
        // Arrange
        val verificationToken = UUID.randomUUID().toString()
        val user = User(
            email = "unverified_user@example.test",
            passwordHash = "UserPassword123",
            isVerified = false,
            emailVerificationToken = verificationToken,
            emailVerificationTokenExpiry = Instant.now().plus(1, ChronoUnit.DAYS)
        )
        whenever(userRepository.findByEmailVerificationToken(verificationToken)).thenReturn(Optional.of(user))

        // Act
        authService.verifyEmail(verificationToken)

        // Assert
        assertThat(user.isVerified).isTrue()
        assertThat(user.emailVerificationToken).isNull()
        assertThat(user.emailVerificationTokenExpiry).isNull()
    }

    @Test
    fun `verifyEmail should throw InvalidTokenException for non-existent token`() {
        // Arrange
        val nonExistentToken = UUID.randomUUID().toString()
        whenever(userRepository.findByEmailVerificationToken(nonExistentToken)).thenReturn(Optional.empty())

        // Act & Assert
        assertThatThrownBy { authService.verifyEmail(nonExistentToken) }
            .isInstanceOf(InvalidTokenException::class.java)
            .hasMessage("The verification token is invalid.")
    }

    @Test
    fun `verifyEmail should not change already verified user`() {
        // Arrange
        val verificationToken = UUID.randomUUID().toString()
        val verificationTokenExpiry = Instant.now().plus(1, ChronoUnit.DAYS)
        val user = User(
            email = "already_verified_user@example.test",
            passwordHash = "UserPassword123",
            isVerified = true,
            emailVerificationToken = verificationToken,
            emailVerificationTokenExpiry = verificationTokenExpiry
        )
        whenever(userRepository.findByEmailVerificationToken(verificationToken)).thenReturn(Optional.of(user))

        // Act
        authService.verifyEmail(verificationToken)

        // Assert - Ensure verification details idempotency
        assertThat(user.isVerified).isTrue()
        assertThat(user.emailVerificationToken).isEqualTo(verificationToken)
        assertThat(user.emailVerificationTokenExpiry).isEqualTo(verificationTokenExpiry)
    }

    @Test
    fun `verifyEmail should throw InvalidTokenException for token with null expiry`() {
        // Arrange
        val verificationToken = UUID.randomUUID().toString()
        val user = User(
            email = "unverified_user@example.test",
            passwordHash = "UserPassword123",
            isVerified = false,
            emailVerificationToken = verificationToken,
            emailVerificationTokenExpiry = null // The invalid state we are testing
        )
        whenever(userRepository.findByEmailVerificationToken(verificationToken)).thenReturn(Optional.of(user))

        // Act & Assert
        assertThatThrownBy { authService.verifyEmail(verificationToken) }
            .isInstanceOf(InvalidTokenException::class.java)
            .hasMessage("The verification token is invalid.")
    }

    @Test
    fun `verifyEmail should throw TokenExpiredException for expired token`() {
        // Arrange
        val verificationToken = UUID.randomUUID().toString()
        val user = User(
            email = "unverified_user@example.test",
            passwordHash = "UserPassword123",
            isVerified = false,
            emailVerificationToken = verificationToken,
            emailVerificationTokenExpiry = Instant.now().minus(1, ChronoUnit.MINUTES)
        )
        whenever(userRepository.findByEmailVerificationToken(verificationToken)).thenReturn(Optional.of(user))

        // Act & Assert
        assertThatThrownBy { authService.verifyEmail(verificationToken) }
            .isInstanceOf(TokenExpiredException::class.java)
            .hasMessage("The verification token has expired.")
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
            passwordHash = "EncodedUserPassword123",
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
            passwordHash = "EncodedUserPassword123",
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
        val springUser = SpringUser(userEmail, "EncodedUserPassword123", emptyList())
        val authentication = UsernamePasswordAuthenticationToken(springUser, null)
        val domainUser = User(email = userEmail, passwordHash = "EncodedUserPassword123")

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
        val springUser = SpringUser(userEmail, "EncodedUserPassword123", emptyList())
        val authentication = UsernamePasswordAuthenticationToken(springUser, null)

        whenever(userRepository.findByEmail(userEmail)).thenReturn(Optional.empty())

        // Act & Assert
        assertThatThrownBy { authService.logoutUser(authentication) }
            .isInstanceOf(UsernameNotFoundException::class.java)
            .hasMessage("User with email '${userEmail}' was not found.")

        // Verify
        verify(refreshTokenService, never()).logout(any())
    }

    @Test
    fun `initiatePasswordReset should set token and expiry and send email for existing user`() {
        // Arrange
        val userEmail = "existing_user@example.test"
        val user = User(email = userEmail, passwordHash = "EncodedUserPassword123")
        whenever(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user))

        // Act
        authService.initiatePasswordReset(userEmail)

        // Assert
        assertThat(user.passwordResetToken).isNotNull()
        assertThat(user.passwordResetTokenExpiry).isNotNull()
        assertThat(user.passwordResetTokenExpiry).isAfter(Instant.now())

        verify(mailService).sendPasswordResetEmail(user)
    }

    @Test
    fun `initiatePasswordReset should not throw exception or send email for non-existent user`() {
        // Arrange
        val nonExistentEmail = "non_existent_user@example.test"
        whenever(userRepository.findByEmail(nonExistentEmail)).thenReturn(Optional.empty())

        // Act & Assert
        assertThatCode {
            authService.initiatePasswordReset(nonExistentEmail)
        }.doesNotThrowAnyException()

        // Verify
        verify(mailService, never()).sendPasswordResetEmail(any())
    }

    @Test
    fun `finalizePasswordReset should update password and clear token for valid token`() {
        // Arrange
        val resetToken = UUID.randomUUID().toString()
        val newPassword = "NewPassword123"
        val encodedNewPassword = "EncodedNewPassword123"
        val user = User(
            email = "existing_user@example.test",
            passwordHash = "OldEncodedPassword",
            passwordResetToken = resetToken,
            passwordResetTokenExpiry = Instant.now().plus(1, ChronoUnit.HOURS)
        )

        whenever(userRepository.findByPasswordResetToken(resetToken)).thenReturn(Optional.of(user))
        whenever(passwordEncoder.encode(newPassword)).thenReturn(encodedNewPassword)

        // Act
        authService.finalizePasswordReset(resetToken, newPassword)

        // Assert
        verify(passwordEncoder).encode(newPassword)
        assertThat(user.passwordHash).isEqualTo(encodedNewPassword)
        assertThat(user.passwordResetToken).isNull()
        assertThat(user.passwordResetTokenExpiry).isNull()

        verify(refreshTokenRepository).deleteByUser(user)
    }

    @Test
    fun `finalizePasswordReset should throw InvalidTokenException for non-existent token`() {
        // Arrange
        val nonExistentToken = UUID.randomUUID().toString()
        whenever(userRepository.findByPasswordResetToken(nonExistentToken)).thenReturn(Optional.empty())

        // Act & Assert
        assertThatThrownBy { authService.finalizePasswordReset(nonExistentToken, "NewPassword123") }
            .isInstanceOf(InvalidTokenException::class.java)
            .hasMessage("The password reset token is invalid.")

        verify(passwordEncoder, never()).encode(any())
        verify(refreshTokenRepository, never()).deleteByUser(any())
    }

    @Test
    fun `finalizePasswordReset should throw InvalidTokenException for token with null expiry`() {
        // Arrange
        val resetToken = UUID.randomUUID().toString()
        val user = User(
            email = "existing_user@example.test",
            passwordHash = "OldEncodedPassword",
            passwordResetToken = resetToken,
            passwordResetTokenExpiry = null
        )
        whenever(userRepository.findByPasswordResetToken(resetToken)).thenReturn(Optional.of(user))

        // Act & Assert
        assertThatThrownBy { authService.finalizePasswordReset(resetToken, "NewPassword123") }
            .isInstanceOf(InvalidTokenException::class.java)
            .hasMessage("The password reset token is invalid.")

        verify(passwordEncoder, never()).encode(any())
        verify(refreshTokenRepository, never()).deleteByUser(any())
    }

    @Test
    fun `finalizePasswordReset should throw TokenExpiredException for expired token`() {
        // Arrange
        val resetToken = UUID.randomUUID().toString()
        val user = User(
            email = "existing_user@example.test",
            passwordHash = "OldEncodedPassword",
            passwordResetToken = resetToken,
            passwordResetTokenExpiry = Instant.now().minus(1, ChronoUnit.MINUTES)
        )
        whenever(userRepository.findByPasswordResetToken(resetToken)).thenReturn(Optional.of(user))

        // Act & Assert
        assertThatThrownBy { authService.finalizePasswordReset(resetToken, "NewPassword123") }
            .isInstanceOf(TokenExpiredException::class.java)
            .hasMessage("The password reset token has expired.")

        verify(passwordEncoder, never()).encode(any())
        verify(refreshTokenRepository, never()).deleteByUser(any())
    }
}
