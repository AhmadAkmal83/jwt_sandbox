package com.sandbox.jwt.auth

import com.sandbox.jwt.auth.dto.LoginRequest
import com.sandbox.jwt.auth.dto.LoginResponse
import com.sandbox.jwt.auth.dto.RegisterRequest
import com.sandbox.jwt.auth.dto.VerifyEmailRequest
import com.sandbox.jwt.auth.service.AuthService
import com.sandbox.jwt.common.dto.MessageResponse
import com.sandbox.jwt.user.dto.UserResponse
import com.sandbox.jwt.user.dto.toUserResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
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

    @GetMapping("/verify-email")
    fun verifyEmail(@Valid request: VerifyEmailRequest): ResponseEntity<MessageResponse> {
        request.token?.let { token ->
            authService.verifyEmail(token)
        }
        val messageResponse = MessageResponse("Email verified successfully. You can now log in.")

        return ResponseEntity.status(HttpStatus.OK).body(messageResponse)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val loginResult = authService.login(request)
        val loginResponse = LoginResponse(
            accessToken = loginResult.accessToken,
            refreshToken = loginResult.refreshToken,
        )

        return ResponseEntity.status(HttpStatus.OK).body(loginResponse)
    }
}
