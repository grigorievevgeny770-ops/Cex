package com.logunovinc.workshopmanager.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logunovinc.workshopmanager.domain.pending.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RecoveryUiState(val commands: List<PendingCommand> = emptyList(), val retrying: Set<String> = emptySet(), val message: String? = null)
sealed interface RecoveryIntent { data class Retry(val id: String) : RecoveryIntent }
@HiltViewModel
class RecoveryViewModel @Inject constructor(repository: PendingCommandRepository, private val retry: RetryPendingCommandUseCase) : ViewModel() {
    private val mutableState = MutableStateFlow(RecoveryUiState())
    val uiState = mutableState.asStateFlow()
    init { viewModelScope.launch { repository.observe().collect { commands -> mutableState.update { it.copy(commands = commands) } } } }
    fun onIntent(intent: RecoveryIntent) {
        when (intent) {
            is RecoveryIntent.Retry -> {
                if (intent.id in uiState.value.retrying) return
                mutableState.update { it.copy(retrying = it.retrying + intent.id, message = null) }
                viewModelScope.launch {
                    try {
                        val outcome = retry(intent.id)
                        mutableState.update { it.copy(message = when (outcome) {
                            is CommandOutcome.Confirmed -> "Действие подтверждено сервером. Обновите карточку и остатки"
                            is CommandOutcome.Unresolved -> "Исход требует восстановления: ${outcome.error.message}"
                            is CommandOutcome.Rejected -> outcome.error.message
                        }) }
                    } finally { mutableState.update { it.copy(retrying = it.retrying - intent.id) } }
                }
            }
        }
    }
}
