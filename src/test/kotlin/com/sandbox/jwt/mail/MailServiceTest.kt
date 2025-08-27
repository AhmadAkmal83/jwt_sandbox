package com.sandbox.jwt.mail

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.sandbox.jwt.mail.dto.MailProperties
import com.sandbox.jwt.user.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.mail.MailSendException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MailServiceTest {

    @Mock
    private lateinit var mailSender: JavaMailSender

    @Mock
    private lateinit var mailProperties: MailProperties

    @InjectMocks
    private lateinit var mailService: MailService

    @Captor
    private lateinit var messageCaptor: ArgumentCaptor<SimpleMailMessage>

    private lateinit var listAppender: ListAppender<ILoggingEvent>

    private lateinit var logger: Logger

    @BeforeEach
    fun setUp() {
        // Set up the logger to capture output
        logger = LoggerFactory.getLogger(MailService::class.java) as Logger
        listAppender = ListAppender()
        listAppender.start()
        logger.addAppender(listAppender)

        // Arrange
        whenever(mailProperties.from).thenReturn(
            MailProperties.From(address = "no-reply@jwt.test", name = "JWT Team")
        )
        whenever(mailProperties.verification).thenReturn(
            MailProperties.Verification(url = "http://jwt.test/api/v1/auth/verify-email")
        )
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(listAppender)
    }

    @Test
    fun `sendVerificationEmail should log info and construct and send email correctly`() {
        // Arrange
        val newUser = User(
            email = "new_user@example.test",
            password = "UserPassword123",
            emailVerificationToken = UUID.randomUUID().toString(),
        )

        // Act
        mailService.sendVerificationEmail(newUser)

        // Assert
        verify(mailSender).send(messageCaptor.capture())
        val capturedMessage = messageCaptor.value

        assertThat(capturedMessage.from).isEqualTo(mailProperties.from.address)
        assertThat(capturedMessage.to).containsExactly(newUser.email)
        assertThat(capturedMessage.subject).isEqualTo("Verify Your Email Address")
        assertThat(capturedMessage.text).contains(
            "${mailProperties.verification.url}?token=${newUser.emailVerificationToken}"
        )

        // Assert the log output
        val logsList = listAppender.list
        assertThat(logsList).hasSize(1)
        val logEvent = logsList[0]
        assertThat(logEvent.level).isEqualTo(Level.INFO)
        assertThat(logEvent.formattedMessage).isEqualTo("Verification email sent successfully to ${newUser.email}")
    }

    @Test
    fun `sendVerificationEmail should log error and not throw exception when mail sending fails`() {
        // Arrange
        val newUser = User(email = "new_user@example.test", password = "UserPassword123")
        val exceptionMessage = "SMTP server down"
        whenever(mailSender.send(any<SimpleMailMessage>()))
            .doThrow(MailSendException(exceptionMessage))

        // Act & Assert no exception is thrown
        assertThatCode { mailService.sendVerificationEmail(newUser) }.doesNotThrowAnyException()

        // Assert the log output
        val logsList = listAppender.list
        assertThat(logsList).hasSize(1)
        val logEvent = logsList[0]
        assertThat(logEvent.level).isEqualTo(Level.ERROR)
        assertThat(logEvent.formattedMessage).isEqualTo("Failed to send verification email to ${newUser.email}")
        assertThat(logEvent.throwableProxy.className).isEqualTo(MailSendException::class.java.name)
        assertThat(logEvent.throwableProxy.message).isEqualTo(exceptionMessage)
    }
}
