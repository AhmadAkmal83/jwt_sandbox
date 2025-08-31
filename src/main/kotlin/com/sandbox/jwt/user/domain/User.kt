package com.sandbox.jwt.user.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@Table(name = "users", schema = "jwt_core")
@EntityListeners(AuditingEntityListener::class)
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0,

    @Column(name = "email", nullable = false, unique = true)
    var email: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Column(name = "is_verified", nullable = false)
    var isVerified: Boolean = false,

    @ElementCollection(targetClass = Role::class, fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_roles",
        schema = "jwt_core",
        joinColumns = [JoinColumn(name = "user_id")],
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    var roles: MutableSet<Role> = mutableSetOf(),

    @Column(name = "email_verification_token")
    var emailVerificationToken: String? = null,

    @Column(name = "email_verification_token_expiry")
    var emailVerificationTokenExpiry: Instant? = null,

    @Column(name = "password_reset_token")
    var passwordResetToken: String? = null,

    @Column(name = "password_reset_token_expiry")
    var passwordResetTokenExpiry: Instant? = null,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
