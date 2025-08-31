package com.sandbox.jwt.auth.repository

import com.sandbox.jwt.auth.domain.RefreshToken
import com.sandbox.jwt.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByUser(user: User): Optional<RefreshToken>

    fun findByToken(token: String): Optional<RefreshToken>
}
