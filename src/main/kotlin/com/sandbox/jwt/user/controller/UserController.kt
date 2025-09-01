package com.sandbox.jwt.user.controller

import com.sandbox.jwt.user.dto.UserResponse
import com.sandbox.jwt.user.dto.toUserResponse
import com.sandbox.jwt.user.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {

    @GetMapping("/me")
    fun getCurrentUser(): ResponseEntity<UserResponse> {
        val userResponse = userService.getCurrentUser().toUserResponse()

        return ResponseEntity.status(HttpStatus.OK).body(userResponse)
    }
}
