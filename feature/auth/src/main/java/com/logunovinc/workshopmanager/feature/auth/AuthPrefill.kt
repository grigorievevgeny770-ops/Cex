package com.logunovinc.workshopmanager.feature.auth

import java.net.URI

/** Local debug convenience only. Empty by default; never logs or submits credentials. */
class AuthPrefill(private val login: String = "", private val password: String = "") {
    fun applyIfEligible(environment: String, apply: (String, String) -> Unit): Boolean {
        val uri = runCatching { URI(environment) }.getOrNull() ?: return false
        val eligible = uri.scheme in setOf("http", "https") && uri.host?.lowercase() in setOf("10.0.2.2", "127.0.0.1", "localhost") &&
            uri.port == 8000 && uri.userInfo == null && uri.query == null && uri.fragment == null
        if (!eligible || login.isBlank() || password.isBlank()) return false
        apply(login, password)
        return true
    }
    override fun toString(): String = "AuthPrefill(redacted)"
}
