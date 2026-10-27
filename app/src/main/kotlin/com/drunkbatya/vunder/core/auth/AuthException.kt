package com.drunkbatya.vunder.core.auth

import com.drunkbatya.vunder.core.network.VkApiError

sealed class AuthException(message: String) : Exception(message) {
    class Failure(message: String) : AuthException(message)
    class CodeRejected(val error: VkApiError) : AuthException("${error.message} (code ${error.code})")
}
