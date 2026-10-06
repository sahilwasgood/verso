package org.lyrictag.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.lyrictag.companion.ui.screens.DashboardScreen
import org.lyrictag.companion.ui.screens.DashboardViewModel
import org.lyrictag.companion.ui.screens.DetailViewModel
import org.lyrictag.companion.ui.screens.LibraryScreen
import org.lyrictag.companion.ui.screens.LibraryViewModel
import org.lyrictag.companion.ui.screens.SettingsScreen
import org.lyrictag.companion.ui.screens.TrackDetailScreen
import org.lyrictag.companion.ui.theme.LyricTagTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = LyricTagApp.of(this)
        setContent {
            var dynamicColor by remember { mutableStateOf(true) }
            var autoFetch by remember { mutableStateOf(true) }
            LyricTagTheme(dynamicColor = dynamicColor) {
                val nav = rememberNavController()
                Scaffold(
                    bottomBar = {
                        val back by nav.currentBackStackEntryAsState()
                        val route = back?.destination?.route
                        NavigationBar {
                            BarItem(nav, "home", "Home", Icons.Default.Home, route)
                            BarItem(nav, "library", "Library", Icons.Default.List, route)
                            BarItem(nav, "settings", "Settings", Icons.Default.Settings, route)
                        }
                    }
                ) { pad ->
                    Box(modifier = androidx.compose.ui.Modifier.padding(pad)) {
                        NavHost(nav, startDestination = "home") {
                            composable("home") {
                                val vm: DashboardViewModel = viewModel {
                                    DashboardViewModel(app.db.tracks(), app.scanner)
                                }
                                DashboardScreen(vm,
                                    onBrowse = { nav.navigate("library") },
                                    onSettings = { nav.navigate("settings") })
                            }
                            composable("library") {
                                val vm: LibraryViewModel = viewModel {
                                    LibraryViewModel(app.db.tracks())
                                }
                                LibraryScreen(vm, onTrack = { nav.navigate("track/$it") })
                            }
                            composable("track/{path}") { e ->
                                val path = e.arguments?.getString("path").orEmpty()
                                val vm: DetailViewModel = viewModel {
                                    DetailViewModel(app.db.tracks(), app.lyricsRepository)
                                }
                                val track by androidx.compose.runtime.produceState<
                                    org.lyrictag.companion.db.TrackEntity?>(null, path) {
                                    value = app.db.tracks().byPath(path)
                                }
                                TrackDetailScreen(path, vm, track)
                            }
                            composable("settings") {
                                SettingsScreen(dynamicColor, { dynamicColor = it }, autoFetch,
                                    { autoFetch = it }, onClearCache = {}, onOpenPrivacy = {})
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarItem(
    nav: androidx.navigation.NavController, route: String, label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector, current: String?,
) {
    NavigationBarItem(
        selected = current == route || (route == "home" && current?.startsWith("track") == true),
        onClick = { nav.navigate(route) { launchSingleTop = true } },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}
