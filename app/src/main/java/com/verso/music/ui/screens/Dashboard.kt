package com.verso.music.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.verso.music.db.TrackDao
import com.verso.music.scan.LibraryScanner
import com.verso.music.work.LyricsBatchWorker

data class DashboardState(
    val total: Int = 0,
    val missingLyrics: Int = 0,
    val withLyrics: Int = 0,
    val needsReview: Int = 0,
    val scanning: Boolean = false,
    val scanProgress: String? = null,
    val lastResult: String? = null,
    val fixingMeta: Boolean = false,
    val metaProgress: String? = null,
    /** API 30+: tag writes need one-time "All files access" approval. */
    val showWriteAccess: Boolean = false,
    /** Poweramp installed but its library unreadable -> offer the grant flow. */
    val showPowerampConnect: Boolean = false,
)

class DashboardViewModel(
    private val dao: TrackDao,
    private val scanner: LibraryScanner,
    private val app: android.content.Context,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    private val _batch = MutableStateFlow<androidx.work.WorkInfo?>(null)
    val batch: StateFlow<androidx.work.WorkInfo?> = _batch

    private val batchLive = androidx.work.WorkManager.getInstance(app)
        .getWorkInfosForUniqueWorkLiveData(com.verso.music.work.LyricsBatchWorker.UNIQUE)
    private val batchObserver = androidx.lifecycle.Observer<List<androidx.work.WorkInfo>> { list ->
        _batch.value = list.firstOrNull {
            it.state == androidx.work.WorkInfo.State.RUNNING ||
                it.state == androidx.work.WorkInfo.State.ENQUEUED
        }
    }

    init {
        batchLive.observeForever(batchObserver)
        refreshPowerampHint()
        refreshWriteAccess()
        viewModelScope.launch {
            dao.observeCount().collect { total ->
                _state.update { it.copy(total = total) }
            }
        }
        viewModelScope.launch {
            dao.observeMissingLyrics().collect { missing ->
                _state.update { it.copy(missingLyrics = missing) }
            }
        }
        viewModelScope.launch {
            dao.observeWithLyrics().collect { with ->
                _state.update { it.copy(withLyrics = with) }
            }
        }
        viewModelScope.launch {
            dao.observeNeedsReview().collect { review ->
                _state.update { it.copy(needsReview = review) }
            }
        }
    }

    private fun refreshPowerampHint() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Gate only on readability: getPackageInfo visibility is unreliable
            // across OEM builds, while a failed provider read is definitive.
            // Tapping the button with no Poweramp installed is a harmless no-op.
            val show = !com.verso.music.poweramp.PowerampLibrary.isReadable(app)
            _state.update { it.copy(showPowerampConnect = show) }
        }
    }

    /** Re-check after returning from system Settings (call on resume). */
    fun refreshWriteAccess() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _state.update {
                it.copy(showWriteAccess = !com.verso.music.core.StorageAccess.canWrite())
            }
        }
    }

    override fun onCleared() {
        batchLive.removeObserver(batchObserver)
        super.onCleared()
    }

    fun scan() {
        if (_state.value.scanning) return
        viewModelScope.launch {            _state.update { it.copy(scanning = true, scanProgress = null, lastResult = null) }
            val summary = try {
                var last = ""
                val r = scanner.scan { p ->
                    last = "${p.done} / ${p.total}"
                    _state.update { it.copy(scanProgress = last) }
                }
                buildString {
                    append("Scan complete: ${r.scanned} tracks")
                    append(when (r.scope) {
                        "poweramp" -> " (your Poweramp library)"
                        "folders" -> " (selected folders)"
                        else -> " (whole device)"
                    })
                    if (r.removed > 0) append(", ${r.removed} removed")
                    if (r.addedOrUpdated > 0) append(", ${r.addedOrUpdated} updated")
                }.let { base ->
                    // When we fell back to device scan despite Poweramp being
                    // around, say exactly why (diagnosis, not a guess).
                    if (r.scope == "device") {
                        val diag = try {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.verso.music.poweramp.PowerampLibrary.diagnose(app)
                            }
                        } catch (_: Throwable) { "diagnosis failed" }
                        "$base\nPoweramp library: $diag"
                    } else base
                }
            } catch (t: Throwable) {
                "Scan failed: ${t.message?.take(100) ?: "unknown error"}"
            } finally {
                // Counts refresh automatically via the observed flows.
            }
            _state.update { it.copy(scanning = false, scanProgress = null, lastResult = summary) }
            refreshPowerampHint()
        }
    }

    /**
     * Metadata fix for every review-flagged track: MusicBrainz suggest
     * (1 req/s per their policy) + auto-apply only near-certain matches
     * (score >= 90) into empty tags. Anything unsure stays for manual review.
     */
    fun fixAllMetadata() {
        if (_state.value.fixingMeta) return
        if (!com.verso.music.core.StorageAccess.canWrite()) {
            _state.update {
                it.copy(lastResult = "Allow file changes first (button above), then retry.")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(fixingMeta = true, metaProgress = null) }
            var fixed = 0
            var skipped = 0
            try {
                val tracks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    dao.needsReviewTracks()
                }
                for ((i, t) in tracks.withIndex()) {
                    _state.update { it.copy(metaProgress = "${i + 1} / ${tracks.size}") }
                    try {
                        val cands = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.verso.music.metadata.MusicBrainz.suggestFor(
                                t.title, t.artist, t.album, t.path.substringAfterLast('/'),
                            )
                        }
                        val best = cands.firstOrNull()
                        if (best != null && best.score >= 90) {
                            val msg = com.verso.music.metadata.applyCandidate(
                                dao, scanner, t.path, best,
                            )
                            if (msg.endsWith("fields fixed.")) fixed++
                            else skipped++
                        } else skipped++
                    } catch (_: Throwable) {
                        skipped++
                    }
                }
            } catch (_: Throwable) {
                // Summary below reports what happened; nothing to unravel.
            }
            val summary = "Metadata fix: $fixed fixed" +
                (if (skipped > 0) ", $skipped still need review" else "")
            _state.update {
                it.copy(
                    fixingMeta = false, metaProgress = null,
                    lastResult = summary,
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(vm: DashboardViewModel) {
    val s by vm.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Re-check the write-access flag whenever returning from Settings.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refreshWriteAccess()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val batch by vm.batch.collectAsState()
    val prog = batch?.progress
    val batchText = when {
        batch == null -> null
        prog != null && prog.getInt(LyricsBatchWorker.KEY_TOTAL, 0) > 0 ->
            "Fetching lyrics ${prog.getInt(LyricsBatchWorker.KEY_DONE, 0)} / " +
                "${prog.getInt(LyricsBatchWorker.KEY_TOTAL, 0)}"
        else -> "Fetching lyrics…"
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Verso", style = MaterialTheme.typography.displaySmall)
        Text(
            "Lyrics & metadata companion for Poweramp",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize().background(
                com.verso.music.ui.theme.headerGradient(),
                shape = MaterialTheme.shapes.medium,
            ),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("${s.total} tracks", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${s.withLyrics} with lyrics · ${s.missingLyrics} need lyrics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.animation.AnimatedVisibility(visible = s.scanProgress != null) {
                    s.scanProgress?.let {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (s.scanning) CircularProgressIndicator()
                                Text("Scanning music… $it", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = s.lastResult != null) {
                    s.lastResult?.let {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Button(onClick = { vm.scan() }, enabled = !s.scanning, modifier = Modifier.fillMaxWidth()) {
            Text(if (s.scanning) "Scanning…" else "Fix Library")
        }
        if (batch != null) {
            OutlinedButton(
                onClick = { LyricsBatchWorker.cancel(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(batchText ?: "Fetching lyrics… (tap to cancel)")
            }
        } else if (s.missingLyrics > 0) {
            OutlinedButton(
                onClick = { LyricsBatchWorker.enqueue(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Fetch missing lyrics (${s.missingLyrics})")
            }
        }
        if (s.fixingMeta) {
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Fixing metadata… ${s.metaProgress ?: ""}")
            }
        } else if (s.needsReview > 0 && !s.scanning) {
            OutlinedButton(
                onClick = { vm.fixAllMetadata() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Fix all metadata (${s.needsReview})")
            }
        }
        if (s.showWriteAccess && !s.scanning && !s.fixingMeta) {
            TextButton(
                onClick = {
                    com.verso.music.core.StorageAccess.request(context)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Allow file changes")
            }
            Text(
                "Fixing tags, embedding lyrics and saving covers writes into your " +
                    "music files - approve it once in Settings, then retry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (s.showPowerampConnect && !s.scanning) {
            TextButton(
                onClick = {
                    com.verso.music.poweramp.PowerampLibrary.requestAccess(context)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Match my Poweramp library")
            }
            Text(
                "If Poweramp is installed, tapping asks for read-only access to its " +
                    "library - approve it inside Poweramp, then tap Fix Library.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
