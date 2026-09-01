package com.example.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.presentation.antidpi.AntiDpiScreen
import com.example.presentation.configs.ConfigsScreen
import com.example.presentation.dashboard.DashboardScreen
import com.example.presentation.evolution.EvolutionScreen
import com.example.presentation.logs.LogViewerScreen
import com.example.presentation.settings.SettingsScreen
import com.example.ui.theme.CyberCyan

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Speed)
    data object Configs : Screen("configs", "Configs", Icons.Default.FolderZip)
    data object AntiDpi : Screen("antidpi", "Anti-DPI", Icons.Default.Security)
    data object Evolution : Screen("evolution", "Evolution", Icons.Default.AutoAwesome)
    data object Logs : Screen("logs", "Logs", Icons.Default.Terminal)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val screens = listOf(
        Screen.Dashboard,
        Screen.Configs,
        Screen.AntiDpi,
        Screen.Evolution,
        Screen.Logs,
        Screen.Settings
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AppBottomBar(
                screens = screens,
                currentRoute = currentRoute,
                onNavigateToScreen = { screen ->
                    if (currentRoute != screen.route) {
                        navController.navigateToTopLevelDestination(screen.route)
                    }
                }
            )
        }
    ) { paddingValues ->
        AppNavHost(
            navController = navController,
            snackbarHostState = snackbarHostState,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
private fun AppBottomBar(
    screens: List<Screen>,
    currentRoute: String?,
    onNavigateToScreen: (Screen) -> Unit
) {
    NavigationBar {
        screens.forEach { screen ->
            val selected = currentRoute == screen.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigateToScreen(screen) },
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CyberCyan,
                    selectedTextColor = CyberCyan,
                    indicatorColor = CyberCyan.copy(alpha = 0.15f)
                ),
                modifier = Modifier.testTag("nav_tab_${screen.route}")
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToEvolution = { navController.navigateToTopLevelDestination(Screen.Evolution.route) },
                onNavigateToConfigs = { navController.navigateToTopLevelDestination(Screen.Configs.route) },
                onNavigateToAntiDpi = { navController.navigateToTopLevelDestination(Screen.AntiDpi.route) },
                onNavigateToSettings = { navController.navigateToTopLevelDestination(Screen.Settings.route) },
                snackbarHostState = snackbarHostState
            )
        }
        composable(Screen.Configs.route) {
            ConfigsScreen(snackbarHostState = snackbarHostState)
        }
        composable(Screen.AntiDpi.route) {
            AntiDpiScreen(snackbarHostState = snackbarHostState)
        }
        composable(Screen.Evolution.route) {
            EvolutionScreen(snackbarHostState = snackbarHostState)
        }
        composable(Screen.Logs.route) {
            LogViewerScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen(snackbarHostState = snackbarHostState)
        }
    }
}

private fun NavController.navigateToTopLevelDestination(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
