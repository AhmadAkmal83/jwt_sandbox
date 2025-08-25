package com.sandbox.jwt.user.dto

import com.sandbox.jwt.user.domain.Role
import com.sandbox.jwt.user.domain.User

data class UserResponse(
    val id: Long,
    val email: String,
    val roles: Set<Role>,
)

fun User.toUserResponse(): UserResponse {
    return UserResponse(
        id = this.id,
        email = this.email,
        roles = this.roles.toSet(),
    )
}
