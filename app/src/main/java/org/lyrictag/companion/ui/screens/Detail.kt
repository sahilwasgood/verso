package org.lyrictag.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lyrictag.companion.core.Confidence
import org.lyrictag.companion.core.LrcParser
import org.lyrictag.companion.db.TrackDao
import org.lyrictag.companion.lyrics.LyricsRepository
import org.lyrictag.companion.lyrics.ScoredLyrics
import org.lyrictag.companion.tags.TagManager
import java.io.File

data class DetailState(
    val loading: Boolean = true,
    val candidates: List<ScoredLyrics> = emptyList(),
    val fromCache: Boolean = false,
    val message: String? = null,
)

class DetailViewModel(
    private val dao: TrackDao,
    private val repo: LyricsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state

    fun load(path: String) {
        viewModelScope.launch {
            _state.value = DetailState(loading = true)
            val t = dao.byPath(path)
            if (t == null) {
                _state.value = DetailState(loading = false)
                return@launch
            }
            if (t == null) {
                _state.value = DetailState(loading = false)
                return@launch
            }
            // Always search: candidates let the user pick the right version
            // before embedding. fromCache just labels tracks already served.
            val list = withContext(Dispatchers.IO) {
                runCatching {
                    repo.search(t.title.orEmpty(), t.artist.orEmpty(), t.album.orEmpty(), t.durationMs ?: 0)
                }.getOrDefault(emptyList())
            }
            _state.value = DetailState(loading = false, candidates = list, fromCache = t.hasCachedLyrics)
        }
    }

    fun embed(path: String, lyrics: String, onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val err = TagManager.embedLyrics(File(path), lyrics)
            if (err == null) dao.updateLyricFlags(path, embedded = true, external = dao.byPath(path)?.hasExternalLyrics ?: false)
            withContext(Dispatchers.Main) { onDone(err ?: "Lyrics embedded.") }
        }
    }

    fun setIgnored(path: String, ignored: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.setIgnored(path, ignored)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun saveSidecar(path: String, lyrics: String, synced: Boolean, onDone: (String) -> Unit) {        viewModelScope.launch(Dispatchers.IO) {
            val base = path.substringBeforeLast('.')
            val target = File(base + if (synced) ".lrc" else ".txt")
            val msg = runCatching {
                target.writeText(if (synced) lyrics else LrcParser.parse(lyrics).lines.joinToString("\n") { it.text })
                dao.updateLyricFlags(path, embedded = dao.byPath(path)?.hasEmbeddedLyrics ?: false, external = true)
                "Saved ${target.name}."
            }.getOrElse { "Save failed: ${it.message?.take(100)}" }
            withContext(Dispatchers.Main) { onDone(msg) }
        }
    }
}

@Composable
fun TrackDetailScreen(path: String, vm: DetailViewModel, track: org.lyrictag.companion.db.TrackEntity?) {
    val s by vm.state.collectAsState()
    var confirm by remember { mutableStateOf<ScoredLyrics?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(path) { vm.load(path) }
    notice?.let { n ->
        AlertDialog(onDismissRequest = { notice = null },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } },
            text = { Text(n) })
    }
    confirm?.let { c ->
        val text = c.result.synced ?: c.result.plain.orEmpty()
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Apply these lyrics?") },
            text = {
                Text(
                    "From ${c.result.source} · confidence ${c.confidence.name} (${c.score}%).\n" +
                        (c.reasons.joinToString(", ")) + "\n\n" + text.take(300)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    vm.embed(path, text) { notice = it }
                }) { Text("Embed into file") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirm = null
                    vm.saveSidecar(path, text, LrcParser.looksLikeLrc(text)) { notice = it }
                }) { Text("Save as file") }
            },
        )
    }

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text(track?.title ?: "(unknown title)", style = MaterialTheme.typography.headlineSmall)
        Text(
            listOfNotNull(track?.artist, track?.album).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Format ${track?.mimeType ?: "?"} · " +
                "${(track?.durationMs ?: 0) / 1000}s · ${track?.path}",
            style = MaterialTheme.typography.bodySmall,
        )
        Divider(Modifier.padding(vertical = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.load(path) }) { Text("Fetch lyrics") }
            OutlinedButton(onClick = { notice = "Track ignored. It is hidden from counts and lists."; vm.setIgnored(path, true) }) {
                Text("Ignore track")
            }
        }
        Divider(Modifier.padding(vertical = 12.dp))
        if (s.loading) {
            CircularProgressIndicator()
        } else if (s.fromCache) {
            Text(
                "Lyrics for this track are already saved on-device (e.g. fetched for Poweramp). " +
                    "Pick a version below to embed into the file or save as .lrc — or retry for fresh results.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!s.loading && s.candidates.isEmpty() && !s.fromCache) {
            Text("No lyrics found. Check the spelling of title/artist, then retry.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.load(path) }) { Text("Retry") }
            }
        }
        if (!s.loading && (s.candidates.isNotEmpty() || s.fromCache)) {
            if (s.candidates.isNotEmpty()) {
                Text("Candidates (best first)", style = MaterialTheme.typography.titleMedium)
            } else {
                OutlinedButton(onClick = { vm.load(path) }) { Text("Search for versions to embed") }
            }
            s.candidates.forEach { c ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        "${c.result.artist} — ${c.result.title} · ${c.result.source}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Confidence ${c.confidence.name} (${c.score}%) · ${c.reasons.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { confirm = c },
                            enabled = c.confidence != Confidence.REJECT,
                        ) { Text(if (c.confidence == Confidence.HIGH) "Apply" else "Review & apply") }
                    }
                }
            }
        }
    }
}
