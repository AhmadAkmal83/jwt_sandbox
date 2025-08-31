package com.sandbox.jwt.auth.service

import com.sandbox.jwt.auth.dto.LoginRequest
import com.sandbox.jwt.auth.dto.RegisterRequest
import com.sandbox.jwt.auth.exception.AccountNotVerifiedException
import com.sandbox.jwt.auth.exception.EmailAlreadyExistsException
import com.sandbox.jwt.auth.exception.InvalidVerificationTokenException
import com.sandbox.jwt.auth.exception.VerificationTokenExpiredException
import com.sandbox.jwt.auth.service.dto.LoginResult
import com.sandbox.jwt.mail.MailService
import com.sandbox.jwt.user.domain.Role
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val mailService: MailService,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
) {

    fun login(request: LoginRequest): LoginResult {
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
            refreshToken = refreshToken.token
        )
    }

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

        if (user.emailVerificationTokenExpiry?.isBefore(Instant.now()) == true) {
            throw VerificationTokenExpiredException("The verification token has expired.")
        }

        user.isVerified = true
        user.emailVerificationToken = null
        user.emailVerificationTokenExpiry = null
    }
}
