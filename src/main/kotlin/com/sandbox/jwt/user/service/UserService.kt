package com.sandbox.jwt.user.service

import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    fun getCurrentUser(): User {
        val principal = SecurityContextHolder.getContext().authentication.principal

        val username = when (principal) {
            is UserDetails -> principal.username
            is String -> principal
            else -> throw IllegalStateException("Unsupported principal type.")
        }

        return userRepository.findByEmail(username)
            .orElseThrow { UsernameNotFoundException("User with email '${username}' was not found.") }
    }
}
