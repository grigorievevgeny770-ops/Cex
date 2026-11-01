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
    val login: String = "",
    val password: String = "",
    val error: String? = null,
    val notice: String? = null,
)
sealed interface AuthIntent {
    data class LoginChanged(val value: String) : AuthIntent
    data class PasswordChanged(val value: String) : AuthIntent
    data class EnvironmentChanged(val value: String) : AuthIntent
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
) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = mutableState.asStateFlow()
    init {
        viewModelScope.launch { repository.session.collect { session ->
            mutableState.update { it.copy(restoring = session.restoring, actor = session.actor,
                password = if (session.actor != null) "" else it.password, notice = session.message) }
        } }
        viewModelScope.launch { repository.environment.collect { url ->
            mutableState.update { it.copy(environment = url, environmentInput = url) }
        } }
        perform { restore() }
    }
    fun onIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.LoginChanged -> mutableState.update { it.copy(login = intent.value, error = null) }
            is AuthIntent.PasswordChanged -> mutableState.update { it.copy(password = intent.value, error = null) }
            is AuthIntent.EnvironmentChanged -> mutableState.update { it.copy(environmentInput = intent.value, error = null) }
            AuthIntent.LoginClick -> {
                val current = uiState.value
                perform { login(current.login, current.password) }
            }
            AuthIntent.LogoutClick -> perform { logout() }
            AuthIntent.ApplyEnvironment -> { val url = uiState.value.environmentInput; perform { changeEnvironment(url) } }
            AuthIntent.RetryRestore -> perform { restore() }
        }
    }
    private fun perform(operation: suspend () -> Outcome<*>) {
        if (uiState.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = operation()) {
                    is Outcome.Failure -> mutableState.update { it.copy(error = result.error.message) }
                    is Outcome.Success -> Unit
                }
            } finally { mutableState.update { it.copy(busy = false, restoring = false, password = "") } }
        }
    }
}
