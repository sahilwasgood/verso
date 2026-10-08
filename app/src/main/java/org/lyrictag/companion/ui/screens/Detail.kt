package org.lyrictag.companion.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.HorizontalDivider
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
    val metaLoading: Boolean = false,
    val meta: List<org.lyrictag.companion.metadata.MusicBrainz.Candidate> = emptyList(),
    val message: String? = null,
)

class DetailViewModel(
    private val dao: TrackDao,
    private val repo: LyricsRepository,
    private val scanner: org.lyrictag.companion.scan.LibraryScanner,
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

    /** MusicBrainz lookup for missing/suspicious tags. */
    fun fetchMetadata(path: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(metaLoading = true, meta = emptyList())
            val t = dao.byPath(path)
            val list = if (t == null) emptyList() else withContext(Dispatchers.IO) {
                org.lyrictag.companion.metadata.MusicBrainz.searchRecording(
                    t.title.orEmpty(), t.artist.orEmpty(), t.album.orEmpty(),
                )
            }
            _state.value = _state.value.copy(metaLoading = false, meta = list)
        }
    }

    /**
     * Writes only fields that are currently missing or suspicious
     * (MetadataRules ONLY_EMPTY policy) — never overwrites good tags.
     */
    fun applyMetadata(
        path: String,
        candidate: org.lyrictag.companion.metadata.MusicBrainz.Candidate,
        onDone: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val t = dao.byPath(path)
            if (t == null) {
                withContext(Dispatchers.Main) { onDone("Track no longer in library.") }
                return@launch
            }
            val updates = linkedMapOf<org.jaudiotagger.tag.FieldKey, String>()
            val rules = mapOf(
                org.jaudiotagger.tag.FieldKey.TITLE to
                    org.lyrictag.companion.core.MetadataField(t.title, candidate.title),
                org.jaudiotagger.tag.FieldKey.ARTIST to
                    org.lyrictag.companion.core.MetadataField(t.artist, candidate.artist),
                org.jaudiotagger.tag.FieldKey.ALBUM to
                    org.lyrictag.companion.core.MetadataField(t.album, candidate.album),
                org.jaudiotagger.tag.FieldKey.YEAR to
                    org.lyrictag.companion.core.MetadataField(t.year, candidate.year),
            )
            for ((field, rule) in rules) {
                rule.resolved(org.lyrictag.companion.core.OverwritePolicy.ONLY_EMPTY)?.let {
                    updates[field] = it
                }
            }
            val msg = if (updates.isEmpty()) {
                "Nothing to fix — existing tags are already filled."
            } else {
                val err = TagManager.write(java.io.File(path), updates)
                if (err == null) {
                    scanner.refreshRow(path)
                    "${updates.size} fields fixed."
                } else err
            }
            withContext(Dispatchers.Main) { onDone(msg) }
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
    var metaReview by remember {
        mutableStateOf<org.lyrictag.companion.metadata.MusicBrainz.Candidate?>(null)
    }

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
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        ) {
            OutlinedButton(onClick = { vm.load(path) }) { Text("Fetch lyrics", maxLines = 1) }
            OutlinedButton(onClick = { vm.fetchMetadata(path) }) { Text("Fetch metadata", maxLines = 1) }
            OutlinedButton(onClick = { notice = "Track ignored. It is hidden from counts and lists."; vm.setIgnored(path, true) }) {
                Text("Ignore track", maxLines = 1)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        if (s.metaLoading) {
            CircularProgressIndicator()
        } else if (s.meta.isNotEmpty()) {
            Text("Metadata matches (MusicBrainz)", style = MaterialTheme.typography.titleMedium)
            s.meta.forEach { m ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        "${m.artist ?: "?"} — ${m.title ?: "?"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Album ${m.album ?: "?"} · ${m.year ?: "?"} · score ${m.score}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val fills = listOfNotNull(
                        "title".takeIf { track?.title.isNullOrBlank() && !m.title.isNullOrBlank() },
                        "artist".takeIf { track?.artist.isNullOrBlank() && !m.artist.isNullOrBlank() },
                        "album".takeIf { track?.album.isNullOrBlank() && !m.album.isNullOrBlank() },
                        "year".takeIf { track?.year.isNullOrBlank() && !m.year.isNullOrBlank() },
                    )
                    Text(
                        if (fills.isEmpty()) "Tags already filled — nothing would change."
                        else "Would fill: ${fills.joinToString(", ")}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { metaReview = m },
                            enabled = fills.isNotEmpty(),
                        ) { Text("Review & apply") }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
        }
        metaReview?.let { m ->
            val beforeAfter = listOf(
                "Title" to (track?.title to m.title),
                "Artist" to (track?.artist to m.artist),
                "Album" to (track?.album to m.album),
                "Year" to (track?.year to m.year),
            )
            AlertDialog(
                onDismissRequest = { metaReview = null },
                title = { Text("Apply metadata?") },
                text = {
                    Text(
                        beforeAfter.joinToString("\n\n") { (label, pair) ->
                            val (before, after) = pair
                            val change = before == after || after.isNullOrBlank()
                            "$label\nBEFORE ${before ?: "—"}\nAFTER " +
                                (if (change) "(unchanged)" else after)
                        } + "\n\nOnly empty tags are filled. Existing values are kept."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        metaReview = null
                        vm.applyMetadata(path, m) { notice = it }
                    }) { Text("Apply") }
                },
                dismissButton = {
                    TextButton(onClick = { metaReview = null }) { Text("Cancel") }
                },
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
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
