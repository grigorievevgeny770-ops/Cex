package com.logunovinc.workshopmanager.feature.auth

import com.logunovinc.workshopmanager.domain.auth.CanonicalBaseUrl

/** User-selectable environments; saved canonical URL remains the source of truth. */
enum class ServerOption(val title: String, val baseUrl: String?) {
    REMOTE("Удалённый сервер", "https://147-45-154-211.sslip.io/"),
    EMULATOR("Локальный сервер · эмулятор", "http://10.0.2.2:8000/"),
    USB("Локальный сервер · USB", "http://127.0.0.1:8000/"),
    CUSTOM("Другой сервер", null);

    companion object {
        fun forUrl(url: String): ServerOption = entries.firstOrNull { it.baseUrl != null && it.baseUrl == CanonicalBaseUrl.parse(url) } ?: CUSTOM
    }
}
