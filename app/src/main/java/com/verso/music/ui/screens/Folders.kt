package com.verso.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.verso.music.scan.LibraryScanner
import com.verso.music.scan.ScanFolders

class FoldersViewModel(
    private val scanner: LibraryScanner,
) : ViewModel() {

    private val _dirs = MutableStateFlow<List<ScanFolders.DirInfo>>(emptyList())
    val dirs: StateFlow<List<ScanFolders.DirInfo>> = _dirs

    private val _checked = MutableStateFlow<Set<String>>(emptySet())
    val checked: StateFlow<Set<String>> = _checked

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress

    suspend fun load(context: android.content.Context) {
        _checked.value = ScanFolders.selected(context)
        _dirs.value = ScanFolders.candidates(context)
    }

    fun toggle(dir: String) {
        _checked.value = if (dir in _checked.value) _checked.value - dir else _checked.value + dir
    }

    /** Saves selection and rescans so counts update immediately. */
    fun saveAndRescan(context: android.content.Context, onDone: (String) -> Unit) {
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            val summary = try {
                ScanFolders.save(context, _checked.value)
                var last = ""
                val r = scanner.scan { p ->
                    last = "${p.done} / ${p.total}"
                    _progress.value = last
                }
                "Scan complete: ${r.scanned} tracks" +
                    (if (r.removed > 0) ", ${r.removed} removed" else "")
            } catch (t: Throwable) {
                "Scan failed: ${t.message?.take(100)}"
            }
            _saving.value = false
            _progress.value = null
            withContext(Dispatchers.Main) { onDone(summary) }
        }
    }
}

@Composable
fun FoldersScreen(vm: FoldersViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val dirs by vm.dirs.collectAsState()
    val checked by vm.checked.collectAsState()
    val saving by vm.saving.collectAsState()
    val progress by vm.progress.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.load(context) }
    notice?.let { n ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { notice = null; onDone() },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { notice = null; onDone() }) {
                    Text("OK")
                }
            },
            text = { Text(n) },
        )
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Music folders", style = MaterialTheme.typography.headlineSmall)
        Text(
            "When Poweramp is installed its own library folders are used automatically. " +
                "Pick folders here only to override that — unselected folders are removed from the library. " +
                "Select nothing to scan the whole device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (dirs.isEmpty()) {
            CircularProgressIndicator()
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(dirs, key = { it.dir }) { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.toggle(d.dir) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = d.dir in checked, onCheckedChange = { vm.toggle(d.dir) })
                        Column(Modifier.weight(1f)) {
                            Text(d.dir, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${d.count} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        progress?.let { Text("Scanning… $it", style = MaterialTheme.typography.bodyMedium) }
        Button(
            onClick = { vm.saveAndRescan(context) { notice = it } },
            enabled = !saving && checked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saving) "Scanning…" else "Save & rescan")
        }
    }
}
