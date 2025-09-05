package com.sandbox.jwt.auth.controller

import com.sandbox.jwt.auth.dto.LoginRequest
import com.sandbox.jwt.auth.dto.LoginResponse
import com.sandbox.jwt.auth.dto.PasswordResetConsumptionRequest
import com.sandbox.jwt.auth.dto.PasswordResetRequest
import com.sandbox.jwt.auth.dto.RegisterRequest
import com.sandbox.jwt.auth.dto.TokenRefreshRequest
import com.sandbox.jwt.auth.dto.TokenRefreshResponse
import com.sandbox.jwt.auth.dto.VerifyEmailRequest
import com.sandbox.jwt.auth.service.AuthService
import com.sandbox.jwt.auth.service.RefreshTokenService
import com.sandbox.jwt.common.dto.MessageResponse
import com.sandbox.jwt.user.dto.UserResponse
import com.sandbox.jwt.user.dto.toUserResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val refreshTokenService: RefreshTokenService,
) {

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
        val loginResult = authService.loginUser(request)
        val loginResponse = LoginResponse(
            accessToken = loginResult.accessToken,
            refreshToken = loginResult.refreshToken,
        )

        return ResponseEntity.status(HttpStatus.OK).body(loginResponse)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: TokenRefreshRequest): ResponseEntity<TokenRefreshResponse> {
        val newAccessToken = refreshTokenService.refreshAccessToken(request.token)
        val tokenRefreshResponse = TokenRefreshResponse(accessToken = newAccessToken)

        return ResponseEntity.status(HttpStatus.OK).body(tokenRefreshResponse)
    }

    @PostMapping("/logout")
    fun logout(authentication: Authentication): ResponseEntity<MessageResponse> {
        authService.logoutUser(authentication)
        val messageResponse = MessageResponse("Logged out successfully.")

        return ResponseEntity.status(HttpStatus.OK).body(messageResponse)
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody request: PasswordResetRequest): ResponseEntity<MessageResponse> {
        authService.initiatePasswordReset(request.email)
        val messageResponse =
            MessageResponse("If an account with that email exists, a password reset link has been sent.")

        return ResponseEntity.status(HttpStatus.OK).body(messageResponse)
    }

    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody request: PasswordResetConsumptionRequest): ResponseEntity<MessageResponse> {
        authService.finalizePasswordReset(request.token, request.newPassword)
        val messageResponse =
            MessageResponse("Password has been reset successfully. You can now log in with your new password.")

        return ResponseEntity.status(HttpStatus.OK).body(messageResponse)
    }
}
