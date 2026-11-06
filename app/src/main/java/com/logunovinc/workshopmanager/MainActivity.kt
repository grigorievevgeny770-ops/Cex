package com.logunovinc.workshopmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.*
import com.logunovinc.workshopmanager.core.ui.WorkshopTheme
import com.logunovinc.workshopmanager.domain.pending.CommandConfirmation
import com.logunovinc.workshopmanager.feature.auth.AuthRoute
import com.logunovinc.workshopmanager.feature.auth.AuthViewModel
import com.logunovinc.workshopmanager.feature.tasks.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.logunovinc.workshopmanager.feature.stock.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WorkshopTheme { AuthRoute(authViewModel) { _, logout -> WorkshopNavigation(logout) } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkshopNavigation(onLogout: () -> Unit) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val completed: (CommandConfirmation) -> Unit = { receipt ->
        val tasks = navController.getBackStackEntry("tasks")
        tasks.savedStateHandle["completedCommandId"] = receipt.commandId
        navController.popBackStack("tasks", false)
    }
    Scaffold(topBar = { TopAppBar(title = {}, navigationIcon = {
        if (route != "tasks" && route != "stock") TextButton(onClick = { navController.popBackStack() }) { Text("Назад") }
    }, actions = { TextButton(onClick = onLogout) { Text("Выйти") } }) }, bottomBar = {
        if (route != "login") NavigationBar {
            listOf("tasks" to "Задания", "stock" to "Склад цеха").forEach { (destination, title) ->
                NavigationBarItem(modifier = Modifier.testTag("nav-$destination"), selected = route == destination, onClick = {
                    navController.navigate(destination) { launchSingleTop = true; popUpTo("tasks") }
                }, icon = { Text(if (destination == "tasks") "☑" else "▤") }, label = { Text(title) })
            }
        }
    }) { padding ->
        NavHost(navController, startDestination = "tasks", modifier = Modifier.padding(padding)) {
            composable("tasks") { entry -> TaskListRoute(hiltViewModel(entry), { navController.navigate("task/$it") }, consumeCompletion = { entry.savedStateHandle.remove<String>("completedCommandId") }) }
            composable("task/{taskId}", arguments = listOf(navArgument("taskId") { type = NavType.LongType })) { entry ->
                TaskDetailRoute(hiltViewModel(entry), { navController.navigate("receive/$it") }, { navController.navigate("dispatch/$it") }, { navController.navigate("manufacturing/$it") }, { navController.navigate("receive/$it?final=true") }, onCompleted = completed)
            }
            composable("receive/{taskId}?final={final}", arguments = listOf(navArgument("taskId") { type = NavType.LongType }, navArgument("final") { type = NavType.BoolType; defaultValue = false })) { entry ->
                val taskId = entry.arguments?.getLong("taskId") ?: return@composable
                ReceiveExecutionRoute(hiltViewModel(entry), {
                    navController.navigate("task/$taskId") { popUpTo("task/$taskId") { inclusive = true }; launchSingleTop = true }
                }, onCompleted = completed)
            }
            composable("dispatch/{taskId}", arguments = listOf(navArgument("taskId") { type = NavType.LongType })) { entry ->
                val taskId = entry.arguments?.getLong("taskId") ?: return@composable
                DispatchExecutionRoute(hiltViewModel(entry), {
                    navController.navigate("task/$taskId") { popUpTo("task/$taskId") { inclusive = true }; launchSingleTop = true }
                }, onCompleted = completed)
            }
            composable("manufacturing/{taskId}", arguments = listOf(navArgument("taskId") { type = NavType.LongType })) { entry ->
                val taskId = entry.arguments?.getLong("taskId") ?: return@composable
                ManufacturingRoute(hiltViewModel(entry), {
                    navController.navigate("task/$taskId") { popUpTo("task/$taskId") { inclusive = true }; launchSingleTop = true }
                }, onCompleted = completed)
            }
            // Migration only: Navigation 2.9 restores saved destination IDs before composing.
            // Old saved stacks may still contain this removed screen; no recovery UI remains.
            composable("recovery") {
                LaunchedEffect(Unit) {
                    if (!navController.popBackStack("tasks", false)) navController.navigate("tasks") {
                        popUpTo("recovery") { inclusive = true }; launchSingleTop = true
                    }
                }
            }
            composable("stock") { entry -> StockListRoute(hiltViewModel(entry)) { navController.navigate("stock-item/$it") } }
            composable("stock-item/{productId}", arguments = listOf(navArgument("productId") { type = NavType.LongType })) { entry -> StockDetailRoute(hiltViewModel(entry)) }
        }
    }
}
