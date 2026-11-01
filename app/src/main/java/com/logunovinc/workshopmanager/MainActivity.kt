package com.logunovinc.workshopmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.*
import com.logunovinc.workshopmanager.core.ui.WorkshopTheme
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
        setContent { WorkshopTheme { AuthRoute(authViewModel) { actor, logout -> WorkshopNavigation(actor.login, logout) } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkshopNavigation(login: String, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(topBar = { TopAppBar(title = { Text(login, maxLines = 1) }, navigationIcon = {
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
            composable("tasks") { entry -> TaskListRoute(hiltViewModel(entry), { navController.navigate("task/$it") }, { navController.navigate("recovery") }) }
            composable("task/{taskId}", arguments = listOf(navArgument("taskId") { type = NavType.LongType })) { entry ->
                TaskDetailRoute(hiltViewModel(entry), { navController.navigate("receive/$it") }, { navController.navigate("dispatch/$it") }, { navController.navigate("manufacturing/$it") }) { navController.navigate("recovery") }
            }
            composable("receive/{taskId}", arguments = listOf(navArgument("taskId") { type = NavType.LongType })) { entry ->
                val taskId = entry.arguments?.getLong("taskId") ?: return@composable
                ReceiveExecutionRoute(hiltViewModel(entry), { navController.navigate("recovery") }, {
                    navController.navigate("task/$taskId") { popUpTo("task/$taskId") { inclusive = true }; launchSingleTop = true }
                })
            }
            composable("dispatch/{taskId}", arguments = listOf(navArgument("taskId") { type = NavType.LongType })) { entry ->
                val taskId = entry.arguments?.getLong("taskId") ?: return@composable
                DispatchExecutionRoute(hiltViewModel(entry), { navController.navigate("recovery") }, {
                    navController.navigate("task/$taskId") { popUpTo("task/$taskId") { inclusive = true }; launchSingleTop = true }
                })
            }
            composable("manufacturing/{taskId}", arguments = listOf(navArgument("taskId") { type = NavType.LongType })) { entry ->
                val taskId = entry.arguments?.getLong("taskId") ?: return@composable
                ManufacturingRoute(hiltViewModel(entry), { navController.navigate("recovery") }, {
                    navController.navigate("task/$taskId") { popUpTo("task/$taskId") { inclusive = true }; launchSingleTop = true }
                })
            }
            composable("recovery") { entry -> RecoveryRoute(hiltViewModel(entry)) { navController.navigate("task/$it") } }
            composable("stock") { entry -> StockListRoute(hiltViewModel(entry)) { navController.navigate("stock-item/$it") } }
            composable("stock-item/{productId}", arguments = listOf(navArgument("productId") { type = NavType.LongType })) { entry -> StockDetailRoute(hiltViewModel(entry)) }
        }
    }
}
