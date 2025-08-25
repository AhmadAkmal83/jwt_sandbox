package com.sandbox.jwt.user.repository

import com.sandbox.jwt.user.domain.Role
import com.sandbox.jwt.user.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryPersistenceTest {

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `findByEmail should return user when email exists`() {
        // Arrange
        val user = User(email = "valid_email@example.com", password = "ValidPassword123", roles = mutableSetOf(Role.USER))
        entityManager.persistAndFlush(user)

        // Act
        val foundUserOptional = userRepository.findByEmail("valid_email@example.com")

        // Assert
        assertThat(foundUserOptional).isPresent
        val foundUser = foundUserOptional.get()
        assertThat(foundUser.email).isEqualTo(user.email)
        assertThat(foundUser.roles).containsExactly(Role.USER)
    }

    @Test
    fun `findByEmail should return empty optional when email does not exist`() {
        // Act
        val foundUserOptional = userRepository.findByEmail("nonexistent@example.com")

        // Assert
        assertThat(foundUserOptional).isNotPresent
    }

    @Test
    fun `existsByEmail should return true when email exists`() {
        // Arrange
        val user = User(email = "exists@example.com", password = "ValidPassword123")
        entityManager.persistAndFlush(user)

        // Act
        val exists = userRepository.existsByEmail("exists@example.com")

        // Assert
        assertThat(exists).isTrue()
    }

    @Test
    fun `existsByEmail should return false when email does not exist`() {
        // Act
        val exists = userRepository.existsByEmail("nonexistent@example.com")

        // Assert
        assertThat(exists).isFalse()
    }

    @Test
    fun `saving a user should populate auditing fields`() {
        // Arrange
        val newUser = User(email = "valid_email@example.com", password = "ValidPassword123")

        // Act
        val savedUser = userRepository.save(newUser)
        entityManager.flush()
        entityManager.clear() // Detach the entity to ensure we get a fresh copy from the DB

        val foundUser = userRepository.findById(savedUser.id).get()

        // Assert
        assertThat(foundUser.id).isNotNull()
        assertThat(foundUser.createdAt).isNotNull()
        assertThat(foundUser.updatedAt).isNotNull()
        assertThat(foundUser.createdAt).isEqualTo(foundUser.updatedAt)
    }
}
