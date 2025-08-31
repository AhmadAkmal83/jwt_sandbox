package com.sandbox.jwt.auth.service

import com.sandbox.jwt.auth.config.JwtProperties
import com.sandbox.jwt.user.domain.User
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(jwtProperties: JwtProperties) {

    private val secretKey: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secretKey.toByteArray())
    private val accessTokenExpirationMs: Long = jwtProperties.accessTokenExpirationMs

    fun generateToken(user: User): String {
        val now = Date()
        val expiryDate = Date(now.time + accessTokenExpirationMs)

        return Jwts.builder()
            .subject(user.email)
            .claim("roles", user.roles.map { it.name })
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact()
    }
}
