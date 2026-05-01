package com.example.retailsafetymonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.retailsafetymonitor.ui.dashboard.DashboardScreen
import com.example.retailsafetymonitor.ui.incidents.IncidentsScreen
import com.example.retailsafetymonitor.ui.monitor.MonitorScreen
import com.example.retailsafetymonitor.ui.report.ReportScreen
import com.example.retailsafetymonitor.ui.settings.SettingsScreen
import com.example.retailsafetymonitor.ui.theme.RetailSafetyMonitorTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Bottom navigation destination definition.
 *
 * @property route Navigation route string used by [NavHost].
 * @property icon Material icon displayed in the bottom bar.
 * @property label Human-readable tab label.
 */
private sealed class NavDestination(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    data object Monitor : NavDestination("monitor", Icons.Default.Videocam, "Monitor")
    data object Dashboard : NavDestination("dashboard", Icons.Default.Dashboard, "Dashboard")
    data object Incidents : NavDestination("incidents", Icons.Default.History, "Incidents")
    data object Report : NavDestination("report", Icons.Default.Assessment, "Report")
    data object Settings : NavDestination("settings", Icons.Default.Settings, "Settings")
}

private val bottomNavItems = listOf(
    NavDestination.Monitor,
    NavDestination.Dashboard,
    NavDestination.Incidents,
    NavDestination.Report,
    NavDestination.Settings
)

/**
 * Single-activity entry point for the Retail Safety Monitor.
 *
 * Hosts a [NavHost] with a bottom navigation bar. Each tab is a
 * separate Compose destination. CameraX lifecycle is managed inside
 * [MonitorScreen] and tied to the composable's [androidx.lifecycle.LifecycleOwner].
 *
 * Annotated with [@AndroidEntryPoint][dagger.hilt.android.AndroidEntryPoint] so Hilt
 * can inject dependencies into ViewModels created within this activity's scope.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Sets up edge-to-edge display, the [NavHost] graph, and the bottom navigation bar.
     * Navigation uses single-top + state restore so tab switches feel instant.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RetailSafetyMonitorTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            bottomNavItems.forEach { dest ->
                                NavigationBarItem(
                                    selected = currentRoute == dest.route,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = { Text(dest.label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = NavDestination.Monitor.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(NavDestination.Monitor.route) { MonitorScreen() }
                        composable(NavDestination.Dashboard.route) {
                            DashboardScreen(onNavigateToIncidents = {
                                navController.navigate(NavDestination.Incidents.route)
                            })
                        }
                        composable(NavDestination.Incidents.route) { IncidentsScreen() }
                        composable(NavDestination.Report.route) { ReportScreen() }
                        composable(NavDestination.Settings.route) { SettingsScreen() }
                    }
                }
            }
        }
    }
}
