package org.lyrictag.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    dynamicColor: Boolean,
    onDynamicColor: (Boolean) -> Unit,
    autoFetch: Boolean,
    onAutoFetch: (Boolean) -> Unit,
    cacheSize: String,
    onClearCache: () -> Unit,
    foldersSummary: String,
    onOpenFolders: () -> Unit,
    scrobbleSummary: String,
    onOpenScrobble: () -> Unit,
    privacyText: String,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var enabled by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            org.lyrictag.companion.lyrics.LyricsRepository.enabledNames(context)
        )
    }
    Column(
        Modifier.fillMaxSize().padding(20.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Lyrics", style = MaterialTheme.typography.titleMedium)
        Row2("Automatic lyrics fetching", autoFetch, onAutoFetch)
        Text("Lyric sources", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tried in order until lyrics are found. All are free and need no account.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        org.lyrictag.companion.lyrics.LyricsRepository.ALL_PROVIDERS.forEach { name ->
            Row2(
                label = name,
                checked = name in enabled,
                onChange = {
                    enabled = if (name in enabled) enabled - name else enabled + name
                    if (enabled.isNotEmpty()) {
                        org.lyrictag.companion.lyrics.LyricsRepository.setEnabled(context, enabled)
                    } else {
                        enabled = setOf(name)
                        org.lyrictag.companion.lyrics.LyricsRepository.setEnabled(context, enabled)
                    }
                },
            )
        }
        Text("Library", style = MaterialTheme.typography.titleMedium)
        Text(foldersSummary, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onOpenFolders) { Text("Choose music folders") }
        Text("Scrobbling", style = MaterialTheme.typography.titleMedium)
        Text(scrobbleSummary, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onOpenScrobble) { Text("Scrobbling setup") }
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Row2("Dynamic (wallpaper) color", dynamicColor, onDynamicColor)
        Text("Storage", style = MaterialTheme.typography.titleMedium)
        Text("Lyric cache: $cacheSize", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onClearCache) { Text("Clear lyric cache") }
        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        Text(privacyText, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Row2(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
