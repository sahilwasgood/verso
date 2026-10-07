package org.lyrictag.companion.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.lyrictag.companion.db.TrackDao
import org.lyrictag.companion.db.TrackEntity

enum class LibraryFilter(val label: String) {
    ALL("All"),
    MISSING_LYRICS("No lyrics"),
    INCOMPLETE("Incomplete"),
    NEEDS_REVIEW("Review"),
}

class LibraryViewModel(private val dao: TrackDao) : ViewModel() {
    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    val filter: StateFlow<LibraryFilter> = _filter
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    @OptIn(androidx.paging.ExperimentalPagingApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val paging = kotlinx.coroutines.flow.combine(_filter, _query) { f, q -> f to q }
        .flatMapLatest { (f, q) ->
            androidx.paging.Pager(
                androidx.paging.PagingConfig(pageSize = 50, enablePlaceholders = false)
            ) { dao.paged(f.ordinal, q.trim()) }.flow
        }
        .cachedIn(viewModelScope)

    fun setFilter(f: LibraryFilter) { _filter.value = f }
    fun setQuery(q: String) { _query.value = q }

    fun toggleIgnore(t: TrackEntity) {
        viewModelScope.launch {
            dao.setIgnored(t.path, !t.ignored)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: LibraryViewModel, onTrack: (String) -> Unit) {
    val filter by vm.filter.collectAsState()
    val query by vm.query.collectAsState()
    val lazyTracks = vm.paging.collectAsLazyPagingItems()
    var ignoreTarget by remember { mutableStateOf<TrackEntity?>(null) }

    ignoreTarget?.let { t ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ignoreTarget = null },
            title = { Text("Ignore this track?") },
            text = { Text("“${t.title ?: t.path}” will be hidden from counts and lists.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.toggleIgnore(t); ignoreTarget = null
                }) { Text("Ignore") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { ignoreTarget = null }) { Text("Cancel") }
            },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query, onValueChange = vm::setQuery,
            label = { Text("Search title, artist, album") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LibraryFilter.entries.toList()) { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { vm.setFilter(f) },
                    label = { Text(f.label) },
                )
            }
        }
        Text(
            "${lazyTracks.itemCount} tracks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(
                count = lazyTracks.itemCount,
                key = { i -> lazyTracks[i]?.path ?: i },
            ) { i ->
                val t = lazyTracks[i] ?: return@items
                ListItem(
                    headlineContent = { Text(t.title ?: "(unknown title)") },
                    supportingContent = {
                        Text(
                            listOfNotNull(t.artist, t.album).joinToString(" · ").ifBlank { "unknown artist" },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    trailingContent = {
                        val ok = t.hasEmbeddedLyrics || t.hasExternalLyrics || t.hasCachedLyrics
                        Icon(
                            if (ok) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = if (ok) "has lyrics" else "missing lyrics",
                            tint = if (ok) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = { onTrack(t.path) },
                        onLongClick = { ignoreTarget = t },
                    ),
                )
            }
        }
    }
}
