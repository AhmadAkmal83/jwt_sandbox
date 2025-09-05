package com.sandbox.jwt.auth.service

import com.sandbox.jwt.auth.config.JwtProperties
import com.sandbox.jwt.auth.domain.RefreshToken
import com.sandbox.jwt.common.security.exception.InvalidTokenException
import com.sandbox.jwt.common.security.exception.TokenExpiredException
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
    private val jwtService: JwtService,
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

    @Transactional
    fun refreshAccessToken(token: String): String {
        val refreshToken = refreshTokenRepository.findByToken(token)
            .orElseThrow { InvalidTokenException("The refresh token is invalid.") }

        verifyExpiration(refreshToken)

        return jwtService.generateToken(refreshToken.user)
    }

    @Transactional
    fun logout(user: User) {
        refreshTokenRepository.deleteByUser(user)
    }

    private fun verifyExpiration(token: RefreshToken) {
        if (token.expiryDate.isBefore(Instant.now())) {
            refreshTokenRepository.delete(token)
            throw TokenExpiredException("Refresh token has expired. Please log in again.")
        }
    }
}
