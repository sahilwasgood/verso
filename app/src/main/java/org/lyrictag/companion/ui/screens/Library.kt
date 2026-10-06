package org.lyrictag.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.lyrictag.companion.db.TrackDao
import org.lyrictag.companion.db.TrackEntity

enum class LibraryFilter { ALL, MISSING_LYRICS, INCOMPLETE, NEEDS_REVIEW }

class LibraryViewModel(dao: TrackDao) : ViewModel() {
    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    val filter: StateFlow<LibraryFilter> = _filter
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val tracks: StateFlow<List<TrackEntity>> = dao.observeAll()
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(f: LibraryFilter) { _filter.value = f }
    fun setQuery(q: String) { _query.value = q }

    fun visible(all: List<TrackEntity>, f: LibraryFilter, q: String): List<TrackEntity> {
        var list = when (f) {
            LibraryFilter.ALL -> all
            LibraryFilter.MISSING_LYRICS -> all.filter { !it.hasEmbeddedLyrics && !it.hasExternalLyrics }
            LibraryFilter.INCOMPLETE -> all.filter { it.title.isNullOrBlank() || it.artist.isNullOrBlank() || it.album.isNullOrBlank() }
            LibraryFilter.NEEDS_REVIEW -> all.filter { it.needsReview }
        }
        if (q.isNotBlank()) {
            list = list.filter {
                it.title?.contains(q, true) == true || it.artist?.contains(q, true) == true ||
                    it.album?.contains(q, true) == true
            }
        }
        return list
    }
}

@Composable
fun LibraryScreen(vm: LibraryViewModel, onTrack: (String) -> Unit) {
    val all by vm.tracks.collectAsState()
    val filter by vm.filter.collectAsState()
    val query by vm.query.collectAsState()
    val list = remember(all, filter, query) { vm.visible(all, filter, query) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query, onValueChange = vm::setQuery,
            label = { Text("Search title, artist, album") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { vm.setFilter(f) },
                    label = { Text(f.name.lowercase().replace('_', ' ')) },
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.path }) { t ->
                ListItem(
                    headlineContent = { Text(t.title ?: "(unknown title)") },
                    supportingContent = {
                        Text(
                            listOfNotNull(t.artist, t.album).joinToString(" · ").ifBlank { "unknown artist" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        val ok = t.hasEmbeddedLyrics || t.hasExternalLyrics
                        Icon(
                            if (ok) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = if (ok) "has lyrics" else "missing lyrics",
                            tint = if (ok) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }
        }
    }
}
