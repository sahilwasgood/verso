package org.lyrictag.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    dynamicColor: Boolean,
    onDynamicColor: (Boolean) -> Unit,
    autoFetch: Boolean,
    onAutoFetch: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Lyrics", style = MaterialTheme.typography.titleMedium)
        Row2("Automatic lyrics fetching", autoFetch, onAutoFetch)
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Row2("Dynamic (wallpaper) color", dynamicColor, onDynamicColor)
        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        Text(
            "LyricTag works offline. Network is used only when you tap fetch: " +
                "the track title, artist, album and duration are sent to LRCLIB " +
                "(lrclib.net) to find matching lyrics. Nothing else leaves the device. " +
                "No accounts, analytics or tracking.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Row2(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
