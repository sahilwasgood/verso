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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress

    private val _dirs = MutableStateFlow<List<String>>(emptyList())
    val dirs: StateFlow<List<String>> = _dirs

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    fun load(context: android.content.Context) {
        _dirs.value = ScanFolders.treeUris(context).mapNotNull { raw ->
            runCatching { android.net.Uri.parse(raw) }.getOrNull()?.let { uri ->
                ScanFolders.volumeOf(uri)?.let { vol ->
                    ScanFolders.dirOf(vol)?.substringAfterLast('/')
                }
            } ?: raw.substringAfterLast('/')
        }.sorted()
    }

    fun addTree(context: android.content.Context, uri: android.net.Uri) {
        ScanFolders.addTree(context, uri)
        load(context)
    }

    fun removeTree(context: android.content.Context, uri: android.net.Uri) {
        ScanFolders.removeTree(context, uri)
        load(context)
    }

    /** Rescans with the current grant set so counts update immediately. */
    fun rescan(onDone: (String) -> Unit) {
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            val summary = try {
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
    val saving by vm.saving.collectAsState()
    val progress by vm.progress.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }

    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) vm.addTree(context, uri)
    }

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
            "Verso scans and edits only inside the folders you grant here " +
                "(standard SAF picker, one tap per folder). With no grant it " +
                "falls back to your Poweramp library or the whole device for " +
                "scanning, and file changes stay read-only.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.weight(1f)) {
            items(dirs, key = { it }) { d ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.rescan { notice = it } }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = true, onCheckedChange = {
                        ScanFolders.treeUris(context)
                            .mapNotNull { raw ->
                                runCatching { android.net.Uri.parse(raw) }.getOrNull()
                            }
                            .firstOrNull { ScanFolders.volumeOf(it)?.let { v ->
                                ScanFolders.dirOf(v)?.substringAfterLast('/') } == d }
                            ?.let { vm.removeTree(context, it) }
                    })
                    Column(Modifier.weight(1f)) {
                        Text(d, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        progress?.let { Text("Scanning… $it", style = MaterialTheme.typography.bodyMedium) }
        Button(
            onClick = { treePicker.launch(null) },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add folder")
        }
    }
}
