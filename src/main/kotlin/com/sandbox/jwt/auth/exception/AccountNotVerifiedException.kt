package com.sandbox.jwt.auth.exception

import org.springframework.security.core.AuthenticationException

class AccountNotVerifiedException(message: String) : AuthenticationException(message)
