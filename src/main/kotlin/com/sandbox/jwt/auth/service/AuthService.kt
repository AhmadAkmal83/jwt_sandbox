package com.sandbox.jwt.auth.service

import com.sandbox.jwt.auth.dto.LoginRequest
import com.sandbox.jwt.auth.dto.RegisterRequest
import com.sandbox.jwt.auth.exception.AccountNotVerifiedException
import com.sandbox.jwt.auth.exception.EmailAlreadyExistsException
import com.sandbox.jwt.auth.exception.InvalidVerificationTokenException
import com.sandbox.jwt.auth.exception.VerificationTokenExpiredException
import com.sandbox.jwt.auth.repository.RefreshTokenRepository
import com.sandbox.jwt.auth.service.dto.LoginResult
import com.sandbox.jwt.mail.MailService
import com.sandbox.jwt.user.domain.Role
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.security.core.userdetails.User as SpringUser

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val mailService: MailService,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    @Transactional
    fun registerUser(request: RegisterRequest): User {
        if (userRepository.existsByEmail(request.email)) {
            throw EmailAlreadyExistsException("A user with the email '${request.email}' already exists.")
        }

        val verificationToken = UUID.randomUUID().toString()
        val tokenExpiry = Instant.now().plus(1, ChronoUnit.DAYS)

        val newUser = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password),
            roles = mutableSetOf(Role.USER),
            isVerified = false,
            emailVerificationToken = verificationToken,
            emailVerificationTokenExpiry = tokenExpiry,
        )

        val savedUser = userRepository.save(newUser)

        mailService.sendVerificationEmail(savedUser)

        return savedUser
    }

    @Transactional
    fun verifyEmail(token: String) {
        val user = userRepository.findByEmailVerificationToken(token)
            .orElseThrow { InvalidVerificationTokenException("The verification token is invalid.") }

        if (user.isVerified) {
            return
        }

        val expiry = user.emailVerificationTokenExpiry
            ?: throw InvalidVerificationTokenException("The verification token is invalid.")

        if (expiry.isBefore(Instant.now())) {
            throw VerificationTokenExpiredException("The verification token has expired.")
        }

        user.isVerified = true
        user.emailVerificationToken = null
        user.emailVerificationTokenExpiry = null
    }

    fun loginUser(request: LoginRequest): LoginResult {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { BadCredentialsException("Invalid email or password.") }

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw BadCredentialsException("Invalid email or password.")
        }

        if (!user.isVerified) {
            throw AccountNotVerifiedException("Account is not verified. Please check your email.")
        }

        val accessToken = jwtService.generateToken(user)
        val refreshToken = refreshTokenService.createRefreshToken(user)

        return LoginResult(
            accessToken = accessToken,
            refreshToken = refreshToken.token,
        )
    }

    fun logoutUser(authentication: Authentication) {
        val principal = authentication.principal as SpringUser
        val user = userRepository.findByEmail(principal.username)
            .orElseThrow { UsernameNotFoundException("User with email '${principal.username}' was not found.") }

        refreshTokenService.logout(user)
    }

    @Transactional
    fun initiatePasswordReset(email: String) {
        userRepository.findByEmail(email).ifPresent { user ->
            val resetToken = UUID.randomUUID().toString()
            val tokenExpiry = Instant.now().plus(1, ChronoUnit.HOURS)

            user.passwordResetToken = resetToken
            user.passwordResetTokenExpiry = tokenExpiry

            mailService.sendPasswordResetEmail(user)
        }
    }

    @Transactional
    fun finalizePasswordReset(token: String, newPassword: String) {
        val user = userRepository.findByPasswordResetToken(token)
            .orElseThrow { InvalidVerificationTokenException("The password reset token is invalid.") }

        val expiry = user.passwordResetTokenExpiry
            ?: throw InvalidVerificationTokenException("The password reset token is invalid.")

        if (expiry.isBefore(Instant.now())) {
            throw VerificationTokenExpiredException("The password reset token has expired.")
        }

        user.passwordHash = passwordEncoder.encode(newPassword)
        user.passwordResetToken = null
        user.passwordResetTokenExpiry = null

        refreshTokenRepository.deleteByUser(user)
    }
}
