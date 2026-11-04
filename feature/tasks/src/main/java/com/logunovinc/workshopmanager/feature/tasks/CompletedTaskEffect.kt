package com.logunovinc.workshopmanager.feature.tasks

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.logunovinc.workshopmanager.domain.pending.CommandConfirmation
import kotlinx.coroutines.awaitCancellation

/** Navigation consumes a persisted ACK; quantities, GET responses and effects never manufacture success. */
@Composable
fun CompletedTaskEffect(confirmation: CommandConfirmation?, onCompleted: (CommandConfirmation) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val callback by rememberUpdatedState(onCompleted)
    var handled by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(confirmation, lifecycle) {
        val receipt = confirmation?.takeIf { it.terminal } ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (handled != receipt.commandId) {
                handled = receipt.commandId
                callback(receipt)
            }
            awaitCancellation()
        }
    }
}
