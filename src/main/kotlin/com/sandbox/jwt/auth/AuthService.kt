package com.sandbox.jwt.auth

import com.sandbox.jwt.auth.dto.RegisterRequest
import com.sandbox.jwt.auth.exception.EmailAlreadyExistsException
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

        return userRepository.save(newUser)
    }
}
