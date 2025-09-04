package com.sandbox.jwt.mail

import com.sandbox.jwt.mail.dto.MailProperties
import com.sandbox.jwt.user.domain.User
import org.slf4j.LoggerFactory
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class MailService(
    private val mailSender: JavaMailSender,
    private val mailProperties: MailProperties,
) {

    private val logger = LoggerFactory.getLogger(MailService::class.java)

    @Async
    fun sendVerificationEmail(user: User) {
        val verificationUrl = "${mailProperties.verification.url}?token=${user.emailVerificationToken}"

        val messageBody = """
            Hello,
            
            Thank you for registering. Please click the link below to verify your email address:
            $verificationUrl
            
            If you did not register, please ignore this email.
            
            Thanks,
            JWT Team
        """.trimIndent()

        try {
            val message = SimpleMailMessage()
            message.from = mailProperties.from.address
            message.setTo(user.email)
            message.subject = "Verify Your Email Address"
            message.text = messageBody
            mailSender.send(message)
            logger.info("Verification email sent successfully to ${user.email}")
        } catch (e: MailException) {
            logger.error("Failed to send verification email to ${user.email}", e)
        }
    }

    @Async
    fun sendPasswordResetEmail(user: User) {
        val resetUrl = "${mailProperties.passwordReset.url}?token=${user.passwordResetToken}"

        val messageBody = """
            Hello,
            
            A password reset was requested for your account. Please click the link below to reset your password:
            $resetUrl
            
            This link will expire in 1 hour.
            
            If you did not request a password reset, please ignore this email.
            
            Thanks,
            JWT Team
        """.trimIndent()

        try {
            val message = SimpleMailMessage()
            message.from = mailProperties.from.address
            message.setTo(user.email)
            message.subject = "Password Reset Request"
            message.text = messageBody
            mailSender.send(message)
            logger.info("Password reset email sent successfully to ${user.email}")
        } catch (e: MailException) {
            logger.error("Failed to send password reset email to ${user.email}", e)
        }
    }
}
