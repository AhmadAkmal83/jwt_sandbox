package com.sandbox.jwt.user.repository

import com.sandbox.jwt.user.domain.Role
import com.sandbox.jwt.user.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager

@DataJpaTest
class UserRepositoryPersistenceTest {

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `findByEmail should return user with correct roles when email exists`() {
        // Arrange
        val existingUser = User(
            email = "existing_user@example.test",
            passwordHash = "EncodedUserPassword123",
            roles = mutableSetOf(Role.USER),
        )
        entityManager.persistAndFlush(existingUser)
        entityManager.clear()

        // Act
        val retrievedUserOptional = userRepository.findByEmail("existing_user@example.test")

        // Assert
        assertThat(retrievedUserOptional).isPresent
        val retrievedUser = retrievedUserOptional.get()
        assertThat(retrievedUser.email).isEqualTo(existingUser.email)
        assertThat(retrievedUser.roles).containsExactly(Role.USER)
    }

    @Test
    fun `findByEmail should return empty optional when email does not exist`() {
        // Act
        val retrievedUserOptional = userRepository.findByEmail("non_existent_user@example.test")

        // Assert
        assertThat(retrievedUserOptional).isNotPresent
    }

    @Test
    fun `existsByEmail should return true when email exists`() {
        // Arrange
        val user = User(email = "existing_user@example.test", passwordHash = "EncodedUserPassword123")
        entityManager.persistAndFlush(user)
        entityManager.clear()

        // Act
        val exists = userRepository.existsByEmail("existing_user@example.test")

        // Assert
        assertThat(exists).isTrue
    }

    @Test
    fun `existsByEmail should return false when email does not exist`() {
        // Act
        val exists = userRepository.existsByEmail("non_existent_user@example.test")

        // Assert
        assertThat(exists).isFalse
    }

    @Test
    fun `saving a user should populate auditing fields`() {
        // Arrange
        val newUser = User(email = "new_user@example.test", passwordHash = "EncodedUserPassword123")

        // Act
        val savedUser = userRepository.save(newUser)
        val retrievedUser = userRepository.findById(savedUser.id).get()

        // Assert
        assertThat(retrievedUser.id).isNotNull
        assertThat(retrievedUser.createdAt).isNotNull
        assertThat(retrievedUser.updatedAt).isNotNull
        assertThat(retrievedUser.createdAt).isEqualTo(retrievedUser.updatedAt)
    }
}
