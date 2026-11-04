package com.logunovinc.workshopmanager.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logunovinc.workshopmanager.domain.auth.Actor

@Composable
fun AuthRoute(viewModel: AuthViewModel, authenticatedContent: @Composable (Actor, () -> Unit) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actor = state.actor
    if (actor != null) authenticatedContent(actor) { viewModel.onIntent(AuthIntent.LogoutClick) }
    else AuthScreen(state, viewModel::onIntent)
}

@Composable
fun AuthScreen(state: AuthUiState, onIntent: (AuthIntent) -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Цех", style = MaterialTheme.typography.headlineLarge)
        var serverMenu by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { serverMenu = true }, enabled = !state.busy && !state.restoring,
                modifier = Modifier.fillMaxWidth().testTag("auth-server-selector")) { Text(state.serverOption.title) }
            DropdownMenu(expanded = serverMenu, onDismissRequest = { serverMenu = false }) {
                ServerOption.entries.forEach { option ->
                    DropdownMenuItem(text = { Column { Text(option.title); option.baseUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall) } } },
                        modifier = Modifier.testTag("auth-server-${option.name.lowercase()}"),
                        onClick = { serverMenu = false; onIntent(AuthIntent.ServerSelected(option)) })
                }
            }
        }
        if (state.serverOption == ServerOption.CUSTOM) {
            OutlinedTextField(value = state.environmentInput, onValueChange = { onIntent(AuthIntent.EnvironmentChanged(it)) },
                label = { Text("Адрес другого сервера") }, singleLine = true, enabled = !state.busy && !state.restoring,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth().testTag("auth-server-custom-url"))
            OutlinedButton(onClick = { onIntent(AuthIntent.ApplyEnvironment) }, enabled = !state.busy && !state.restoring,
                modifier = Modifier.testTag("auth-server-apply")) { Text("Применить адрес") }
            Text("Выбран: ${state.environment}", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(value = state.login, onValueChange = { onIntent(AuthIntent.LoginChanged(it)) },
            label = { Text("Логин") }, singleLine = true, enabled = !state.busy && !state.restoring, modifier = Modifier.fillMaxWidth().testTag("auth-login"))
        OutlinedTextField(value = state.password, onValueChange = { onIntent(AuthIntent.PasswordChanged(it)) },
            label = { Text("Пароль") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            enabled = !state.busy && !state.restoring, modifier = Modifier.fillMaxWidth().testTag("auth-password"))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.notice?.takeIf { it.isNotBlank() && state.error == null }?.let { Text(it) }
        if (state.restoring) Text("Входим…")
        if (state.busy || state.restoring) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = { onIntent(AuthIntent.LoginClick) }, enabled = !state.busy && !state.restoring && state.environmentApplied, modifier = Modifier.fillMaxWidth().testTag("auth-submit")) { Text("Войти") }
    }
}
