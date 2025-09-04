package com.sandbox.jwt.auth.repository

import com.sandbox.jwt.auth.domain.RefreshToken
import com.sandbox.jwt.user.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@DataJpaTest
class RefreshTokenRepositoryPersistenceTest {

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    private lateinit var existingUser: User
    private lateinit var expiryDate: Instant

    @BeforeEach
    fun setUp() {
        existingUser = User(email = "existing_user@example.test", passwordHash = "EncodedUserPassword123")
        entityManager.persistAndFlush(existingUser)
        entityManager.clear()

        expiryDate = Instant.now().plus(7, ChronoUnit.DAYS)
    }

    @Test
    fun `save should persist refresh token with correct user association`() {
        // Arrange
        val refreshToken = RefreshToken(
            user = existingUser,
            token = UUID.randomUUID().toString(),
            expiryDate = expiryDate
        )

        // Act
        val savedToken = refreshTokenRepository.save(refreshToken)
        entityManager.flush()
        entityManager.clear()

        // Assert
        val retrievedToken = entityManager.find(RefreshToken::class.java, savedToken.id)
        assertThat(retrievedToken).isNotNull()
        assertThat(retrievedToken.id).isEqualTo(savedToken.id)
        assertThat(retrievedToken.user.id).isEqualTo(existingUser.id)
        assertThat(retrievedToken.token).isEqualTo(savedToken.token)
    }

    @Test
    fun `findByUser should return correct refresh token`() {
        // Arrange
        val tokenValue = UUID.randomUUID().toString()
        val refreshToken = RefreshToken(
            user = existingUser,
            token = tokenValue,
            expiryDate = expiryDate
        )
        entityManager.persistAndFlush(refreshToken)
        entityManager.clear()

        // Act
        val retrievedTokenOptional = refreshTokenRepository.findByUser(existingUser)

        // Assert
        assertThat(retrievedTokenOptional).isPresent()
        assertThat(retrievedTokenOptional.get().token).isEqualTo(tokenValue)
    }

    @Test
    fun `findByToken should return correct refresh token`() {
        // Arrange
        val tokenValue = UUID.randomUUID().toString()
        val refreshToken = RefreshToken(
            user = existingUser,
            token = tokenValue,
            expiryDate = expiryDate
        )
        entityManager.persistAndFlush(refreshToken)
        entityManager.clear()

        // Act
        val retrievedTokenOptional = refreshTokenRepository.findByToken(tokenValue)

        // Assert
        assertThat(retrievedTokenOptional).isPresent()
        assertThat(retrievedTokenOptional.get().user.id).isEqualTo(existingUser.id)
    }

    @Test
    fun `saving two tokens for the same user should throw DataIntegrityViolationException`() {
        // Arrange
        val firstToken = RefreshToken(
            user = existingUser,
            token = UUID.randomUUID().toString(),
            expiryDate = expiryDate
        )
        entityManager.persistAndFlush(firstToken)
        entityManager.clear()

        val secondToken = RefreshToken(
            user = existingUser,
            token = UUID.randomUUID().toString(),
            expiryDate = expiryDate
        )

        // Act & Assert
        assertThatThrownBy {
            refreshTokenRepository.saveAndFlush(secondToken)
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
