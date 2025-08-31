package com.sandbox.jwt.common.exception.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val message: String,
    val errors: Map<String, List<String>>? = null,
)
