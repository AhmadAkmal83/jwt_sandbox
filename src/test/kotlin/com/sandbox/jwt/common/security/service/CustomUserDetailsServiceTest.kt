package com.sandbox.jwt.common.security.service

import com.sandbox.jwt.user.domain.Role
import com.sandbox.jwt.user.domain.User
import com.sandbox.jwt.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UsernameNotFoundException
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class CustomUserDetailsServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @InjectMocks
    private lateinit var customUserDetailsService: CustomUserDetailsService

    @Test
    fun `loadUserByUsername should return UserDetails with correct authorities when user is found`() {
        // Arrange
        val userEmail = "existing_user@example.test"
        val existingUser = User(
            email = userEmail,
            passwordHash = "EncodedUserPassword123",
            roles = mutableSetOf(Role.USER, Role.ADMIN)
        )
        whenever(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(existingUser))

        // Act
        val userDetails = customUserDetailsService.loadUserByUsername(userEmail)

        // Assert
        assertThat(userDetails).isNotNull
        assertThat(userDetails.username).isEqualTo(existingUser.email)
        assertThat(userDetails.password).isEqualTo(existingUser.passwordHash)
        assertThat(userDetails.authorities).containsExactlyInAnyOrder(
            SimpleGrantedAuthority("ROLE_USER"),
            SimpleGrantedAuthority("ROLE_ADMIN")
        )
    }

    @Test
    fun `loadUserByUsername should throw UsernameNotFoundException when user is not found`() {
        // Arrange
        val nonExistentEmail = "non_existent_user@example.test"
        whenever(userRepository.findByEmail(nonExistentEmail)).thenReturn(Optional.empty())

        // Act & Assert
        assertThatThrownBy {
            customUserDetailsService.loadUserByUsername(nonExistentEmail)
        }.isInstanceOf(UsernameNotFoundException::class.java)
            .hasMessage("User with email '${nonExistentEmail}' was not found.")
    }
}
