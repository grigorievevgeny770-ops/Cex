package com.logunovinc.workshopmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import com.logunovinc.workshopmanager.core.ui.WorkshopTheme
import com.logunovinc.workshopmanager.feature.auth.AuthScreen
import com.logunovinc.workshopmanager.feature.tasks.TasksScreen
import com.logunovinc.workshopmanager.feature.stock.StockScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WorkshopTheme { WorkshopNavigation() } }
    }
}

@Composable
private fun WorkshopNavigation() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(bottomBar = {
        if (route != "login") NavigationBar {
            listOf("tasks" to "Задания", "stock" to "Склад цеха").forEach { (destination, title) ->
                NavigationBarItem(selected = route == destination, onClick = {
                    navController.navigate(destination) { launchSingleTop = true; popUpTo("tasks") }
                }, icon = { Text(if (destination == "tasks") "☑" else "▤") }, label = { Text(title) })
            }
        }
    }) { padding ->
        NavHost(navController, startDestination = "login", modifier = Modifier.padding(padding)) {
            composable("login") { AuthScreen() }
            composable("tasks") { TasksScreen() }
            composable("stock") { StockScreen() }
        }
    }
}
