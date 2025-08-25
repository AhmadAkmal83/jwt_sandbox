package com.sandbox.jwt.auth

import com.sandbox.jwt.auth.dto.RegisterRequest
import com.sandbox.jwt.user.dto.UserResponse
import com.sandbox.jwt.user.dto.toUserResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<UserResponse> {
        val newUser = authService.registerUser(request)
        val userResponse = newUser.toUserResponse()

        return ResponseEntity.status(HttpStatus.CREATED).body(userResponse)
    }
}
