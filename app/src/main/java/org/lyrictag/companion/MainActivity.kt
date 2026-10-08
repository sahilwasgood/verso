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
import org.lyrictag.companion.ui.screens.FoldersScreen
import org.lyrictag.companion.ui.screens.FoldersViewModel
import org.lyrictag.companion.ui.screens.LibraryScreen
import org.lyrictag.companion.ui.screens.ScrobbleScreen
import org.lyrictag.companion.ui.screens.ScrobbleViewModel
import org.lyrictag.companion.ui.screens.LibraryViewModel
import org.lyrictag.companion.ui.screens.SettingsScreen
import org.lyrictag.companion.ui.screens.TrackDetailScreen
import org.lyrictag.companion.ui.theme.LyricTagTheme

class MainActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("lyrictag", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = LyricTagApp.of(this)
        setContent {
            var dynamicColor by remember {
                mutableStateOf(prefs.getBoolean("dynamic_color", true))
            }
            var autoFetch by remember {
                mutableStateOf(prefs.getBoolean("auto_fetch", true))
            }
            var scrobbleEnabled by remember {
                mutableStateOf(prefs.getBoolean("scrobble_enabled", false))
            }
            var cacheSize by remember { mutableStateOf("…") }
            var foldersSummary by remember { mutableStateOf("") }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                cacheSize = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    app.lyricsCacheSize()
                }
            }
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
                                    DashboardViewModel(app.db.tracks(), app.scanner, app)
                                }
                                DashboardScreen(vm)
                            }
                            composable("library") {
                                val vm: LibraryViewModel = viewModel {
                                    LibraryViewModel(app.db.tracks())
                                }
                                LibraryScreen(vm, onTrack = { path ->
                                    nav.navigate("track/${android.net.Uri.encode(path)}")
                                })
                            }
                            composable("track/{path}") { e ->
                                val path = android.net.Uri.decode(e.arguments?.getString("path")).orEmpty()
                                val vm: DetailViewModel = viewModel {
                                    DetailViewModel(app.db.tracks(), app.lyricsRepository, app.scanner)
                                }
                                val track by androidx.compose.runtime.produceState<
                                    org.lyrictag.companion.db.TrackEntity?>(null, path) {
                                    value = app.db.tracks().byPath(path)
                                }
                                TrackDetailScreen(path, vm, track)
                            }
                            composable("settings") {
                                val selected = org.lyrictag.companion.scan.ScanFolders.selected(this@MainActivity)
                                foldersSummary = if (selected.isEmpty())
                                    "Automatic — your Poweramp library when available, else whole device"
                                else if (selected.size == 1)
                                    "Manual override — 1 folder: ${selected.first()}"
                                else
                                    "Manual override — ${selected.size} folders: ${selected.sorted().joinToString(", ")}"
                                SettingsScreen(
                                    dynamicColor = dynamicColor,
                                    onDynamicColor = {
                                        dynamicColor = it
                                        prefs.edit().putBoolean("dynamic_color", it).apply()
                                    },
                                    autoFetch = autoFetch,
                                    onAutoFetch = {
                                        autoFetch = it
                                        prefs.edit().putBoolean("auto_fetch", it).apply()
                                    },
                                    cacheSize = cacheSize,
                                    onClearCache = {
                                        Thread {
                                            app.clearLyricCache()
                                            val s = app.lyricsCacheSize()
                                            runOnUiThread { cacheSize = s }
                                        }.start()
                                    },
                                    foldersSummary = foldersSummary,
                                    onOpenFolders = { nav.navigate("folders") },
                                    scrobbleSummary = if (scrobbleEnabled)
                                        "On — scrobbling Poweramp playback"
                                    else
                                        "Off",
                                    onOpenScrobble = { nav.navigate("scrobble") },
                                    privacyText = PRIVACY_TEXT,
                                )
                            }
                            composable("scrobble") {
                                val vm: ScrobbleViewModel = viewModel {
                                    ScrobbleViewModel(app.db)
                                }
                                ScrobbleScreen(
                                    vm, enabled = scrobbleEnabled,
                                    onEnabled = {
                                        scrobbleEnabled = it
                                        prefs.edit().putBoolean("scrobble_enabled", it).apply()
                                    },
                                )
                            }
                            composable("folders") {
                                val vm: FoldersViewModel = viewModel {
                                    FoldersViewModel(app.scanner)
                                }
                                FoldersScreen(vm, onDone = { nav.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val PRIVACY_TEXT =
    "LyricTag works offline. Network is used only when lyrics are fetched: " +
        "the track title, artist, album and duration are sent to lyric services " +
        "(LRCLIB, AMLL, KuGou, Lyricify, LunaBeat) to find matching lyrics. " +
        "Scrobbling, when enabled, sends the played track name, artist and time " +
        "to ListenBrainz and/or Last.fm using your own tokens. " +
        "Nothing else leaves the device. No accounts, analytics or tracking."

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
