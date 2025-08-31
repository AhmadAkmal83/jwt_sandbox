package com.sandbox.jwt.auth.service

import com.sandbox.jwt.auth.config.JwtProperties
import com.sandbox.jwt.auth.domain.RefreshToken
import com.sandbox.jwt.auth.repository.RefreshTokenRepository
import com.sandbox.jwt.user.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtProperties: JwtProperties,
) {

    @Transactional
    fun createRefreshToken(user: User): RefreshToken {
        refreshTokenRepository.findByUser(user).ifPresent { existingToken ->
            refreshTokenRepository.delete(existingToken)
            refreshTokenRepository.flush()
        }

        val expiryDate = Instant.now().plusMillis(jwtProperties.refreshTokenExpirationMs)
        val tokenValue = UUID.randomUUID().toString()

        val newRefreshToken = RefreshToken(
            user = user,
            token = tokenValue,
            expiryDate = expiryDate,
        )

        return refreshTokenRepository.save(newRefreshToken)
    }
}
