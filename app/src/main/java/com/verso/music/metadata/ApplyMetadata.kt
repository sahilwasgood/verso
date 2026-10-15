package com.verso.music.metadata

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.verso.music.core.MetadataField
import com.verso.music.core.OverwritePolicy
import com.verso.music.db.TrackDao
import com.verso.music.scan.LibraryScanner
import com.verso.music.tags.TagManager
import java.io.File

/**
 * Shared metadata-apply core: writes only missing tags (ONLY_EMPTY),
 * re-reads the row, reports what changed. Used by the detail review
 * dialog and the fix-all batch - one policy, no drift.
 */
suspend fun applyCandidate(
    dao: TrackDao,
    scanner: LibraryScanner,
    path: String,
    candidate: MusicBrainz.Candidate,
): String = withContext(Dispatchers.IO) {
    if (!com.verso.music.core.StorageAccess.canWrite()) {
        return@withContext "File changes need one-time approval: tap “Allow file changes” on the home screen, then retry."
    }
    val t = dao.byPath(path) ?: return@withContext "Track no longer in library."
    val updates = linkedMapOf<org.jaudiotagger.tag.FieldKey, String>()
    val rules = mapOf(
        org.jaudiotagger.tag.FieldKey.TITLE to
            MetadataField(t.title, candidate.title),
        org.jaudiotagger.tag.FieldKey.ARTIST to
            MetadataField(t.artist, candidate.artist),
        org.jaudiotagger.tag.FieldKey.ALBUM to
            MetadataField(t.album, candidate.album),
        org.jaudiotagger.tag.FieldKey.YEAR to
            MetadataField(t.year, candidate.year),
    )
    for ((field, rule) in rules) {
        rule.resolved(OverwritePolicy.ONLY_EMPTY)?.let { updates[field] = it }
    }
    if (updates.isEmpty()) {
        "Nothing to fix - existing tags are already filled."
    } else {
        val err = TagManager.write(File(path), updates)
        if (err == null) {
            scanner.refreshRow(path)
            "${updates.size} fields fixed."
        } else err
    }
}
