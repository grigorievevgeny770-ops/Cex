package com.logunovinc.workshopmanager.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.auth.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Password exists only in this volatile screen state; it is never SavedState or persisted. */
data class AuthUiState(
    val restoring: Boolean = true,
    val busy: Boolean = false,
    val actor: Actor? = null,
    val environment: String = "",
    val environmentInput: String = "",
    val serverOption: ServerOption = ServerOption.REMOTE,
    val login: String = "",
    val password: String = "",
    val error: String? = null,
    val notice: String? = null,
) {
    val environmentApplied: Boolean get() = environment.isNotBlank() && CanonicalBaseUrl.parse(environmentInput) == environment
}
sealed interface AuthIntent {
    data class LoginChanged(val value: String) : AuthIntent
    data class PasswordChanged(val value: String) : AuthIntent
    data class EnvironmentChanged(val value: String) : AuthIntent
    data class ServerSelected(val option: ServerOption) : AuthIntent
    data object ApplyEnvironment : AuthIntent
    data object LoginClick : AuthIntent
    data object LogoutClick : AuthIntent
    data object RetryRestore : AuthIntent
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val login: LoginUseCase,
    private val restore: RestoreSessionUseCase,
    private val logout: LogoutUseCase,
    private val changeEnvironment: ChangeEnvironmentUseCase,
    private val prefill: AuthPrefill = AuthPrefill(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = mutableState.asStateFlow()
    private var startupPrefill = true
    private var credentialsEdited = false
    init {
        viewModelScope.launch { repository.session.collect { session ->
            mutableState.update { it.copy(restoring = session.restoring, actor = session.actor,
                password = if (session.actor != null) "" else it.password, notice = session.message) }
        } }
        viewModelScope.launch { repository.environment.collect { url ->
            mutableState.update {
                val changed = it.environment.isNotBlank() && it.environment != url
                it.copy(environment = url, environmentInput = url, serverOption = ServerOption.forUrl(url), login = if (changed) "" else it.login, password = if (changed) "" else it.password)
            }
            if (startupPrefill && !credentialsEdited) applyPrefill()
        } }
        perform(clearPassword = false) { restore() }
    }
    fun onIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.LoginChanged -> { credentialsEdited = true; mutableState.update { it.copy(login = intent.value, error = null) } }
            is AuthIntent.PasswordChanged -> { credentialsEdited = true; mutableState.update { it.copy(password = intent.value, error = null) } }
            is AuthIntent.EnvironmentChanged -> if (!uiState.value.busy && !uiState.value.restoring) mutableState.update { it.copy(environmentInput = intent.value, serverOption = ServerOption.CUSTOM, error = null) }
            is AuthIntent.ServerSelected -> {
                val state = uiState.value
                if (state.busy || state.restoring) return
                startupPrefill = false
                if (intent.option == ServerOption.CUSTOM) mutableState.update { it.copy(serverOption = intent.option, login = "", password = "", error = null) }
                else {
                    val url = checkNotNull(intent.option.baseUrl)
                    if (state.environment == url) mutableState.update { it.copy(serverOption = intent.option, environmentInput = url, error = null) }
                    else {
                        mutableState.update { it.copy(login = "", password = "") }
                        perform { changeEnvironment(url) }
                    }
                }
            }
            AuthIntent.LoginClick -> {
                val current = uiState.value
                if (!current.environmentApplied) {
                    mutableState.update { it.copy(error = "Сначала примените адрес выбранного сервера") }
                    return
                }
                perform { login(current.login, current.password) }
            }
            AuthIntent.LogoutClick -> perform(prefillAfterLogout = true) { logout() }
            AuthIntent.ApplyEnvironment -> {
                val url = uiState.value.environmentInput
                startupPrefill = false
                perform {
                    val result = changeEnvironment(url)
                    if (result is Outcome.Success) mutableState.update { it.copy(serverOption = ServerOption.forUrl(repository.environment.value), environmentInput = repository.environment.value) }
                    result
                }
            }
            AuthIntent.RetryRestore -> perform { restore() }
        }
    }
    private fun applyPrefill() {
        val state = uiState.value
        if (state.actor == null && state.login.isBlank() && state.password.isBlank()) {
            prefill.applyIfEligible(state.environment) { login, password ->
                mutableState.update { it.copy(login = login, password = password) }
            }
        }
    }
    private fun perform(clearPassword: Boolean = true, prefillAfterLogout: Boolean = false, operation: suspend () -> Outcome<*>) {
        if (uiState.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = operation()) {
                    is Outcome.Failure -> mutableState.update { it.copy(error = result.error.message) }
                    is Outcome.Success -> Unit
                }
            } finally {
                mutableState.update { it.copy(busy = false, restoring = false, password = if (clearPassword) "" else it.password) }
                if (!clearPassword) startupPrefill = false
                if (prefillAfterLogout) {
                    credentialsEdited = false
                    mutableState.update { it.copy(login = "", password = "") }
                    applyPrefill()
                }
            }
        }
    }
}
