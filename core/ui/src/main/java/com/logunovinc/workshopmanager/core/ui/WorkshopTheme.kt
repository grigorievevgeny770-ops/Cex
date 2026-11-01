package com.logunovinc.workshopmanager.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun WorkshopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF355D47), secondary = Color(0xFF786333),
        background = Color(0xFFF8F9F4), surface = Color(0xFFF8F9F4),
    ), content = content)
}
