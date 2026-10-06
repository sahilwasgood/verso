package org.lyrictag.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.lyrictag.companion.db.TrackDao
import org.lyrictag.companion.scan.LibraryScanner

data class DashboardState(
    val total: Int = 0,
    val missingLyrics: Int = 0,
    val withLyrics: Int = 0,
    val scanning: Boolean = false,
    val scanProgress: String? = null,
    val lastResult: String? = null,
)

class DashboardViewModel(
    private val dao: TrackDao,
    private val scanner: LibraryScanner,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    init {
        viewModelScope.launch {
            dao.observeCount().collect { total ->
                _state.value = _state.value.copy(total = total)
            }
        }
        viewModelScope.launch {
            dao.observeMissingLyrics().collect { missing ->
                _state.value = _state.value.copy(missingLyrics = missing)
            }
        }
        viewModelScope.launch {
            dao.observeWithLyrics().collect { with ->
                _state.value = _state.value.copy(withLyrics = with)
            }
        }
    }

    fun scan() {
        if (_state.value.scanning) return
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = true, scanProgress = null, lastResult = null)
            val summary = try {
                var last = ""
                val r = scanner.scan { p ->
                    last = "${p.done} / ${p.total}"
                    _state.value = _state.value.copy(scanProgress = last)
                }
                buildString {
                    append("Scan complete: ${r.scanned} tracks")
                    if (r.removed > 0) append(", ${r.removed} removed")
                    if (r.addedOrUpdated > 0) append(", ${r.addedOrUpdated} updated")
                }
            } catch (t: Throwable) {
                "Scan failed: ${t.message?.take(100) ?: "unknown error"}"
            } finally {
                // Counts refresh automatically via the observed flows.
            }
            _state.value = _state.value.copy(scanning = false, scanProgress = null, lastResult = summary)
        }
    }
}

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onBrowse: () -> Unit,
    onSettings: () -> Unit,
) {
    val s by vm.state.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("LyricTag", style = MaterialTheme.typography.displaySmall)
        Text(
            "Lyrics & metadata companion for Poweramp",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
            Column(Modifier.padding(20.dp)) {
                Text("${s.total} tracks", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${s.withLyrics} with lyrics · ${s.missingLyrics} need lyrics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.scanProgress?.let {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (s.scanning) CircularProgressIndicator()
                        Text("Scanning music… $it", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                s.lastResult?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Button(onClick = { vm.scan() }, enabled = !s.scanning, modifier = Modifier.fillMaxWidth()) {
            Text(if (s.scanning) "Scanning…" else "Fix Library")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBrowse, modifier = Modifier.weight(1f)) { Text("Browse Library") }
            OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Settings") }
        }
    }
}
