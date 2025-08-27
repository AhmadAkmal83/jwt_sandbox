package com.sandbox.jwt.auth

import com.sandbox.jwt.auth.dto.RegisterRequest
import com.sandbox.jwt.auth.exception.EmailAlreadyExistsException
import com.sandbox.jwt.auth.exception.InvalidVerificationTokenException
import com.sandbox.jwt.auth.exception.VerificationTokenExpiredException
import com.sandbox.jwt.mail.MailService
import com.sandbox.jwt.user.domain.Role
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
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
            password = passwordEncoder.encode(request.password),
            roles = mutableSetOf(Role.USER),
            isActive = false,
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

        if (user.isActive) {
            return
        }

        if (user.emailVerificationTokenExpiry?.isBefore(Instant.now()) == true) {
            throw VerificationTokenExpiredException("The verification token has expired.")
        }

        user.isActive = true
        user.emailVerificationToken = null
        user.emailVerificationTokenExpiry = null
    }
}
