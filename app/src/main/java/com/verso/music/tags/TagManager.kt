package com.verso.music.tags

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/** Snapshot of the tags we manage. Artwork and all other fields untouched. */
data class TagSnapshot(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val track: String?,
    val disc: String?,
    val year: String?,
    val genre: String?,
    val lyrics: String?,
)

data class AudioInfo(val durationMs: Long?, val mimeType: String?)

sealed interface TagResult {
    data class Ok(val tags: TagSnapshot, val info: AudioInfo) : TagResult
    data class Unsupported(val reason: String) : TagResult
    data class Failed(val reason: String) : TagResult
}

/**
 * Reads/writes audio tags via jaudiotagger. Write path: modify in-memory tag,
 * commit (library uses temp-file replace), then verify size changed sanely.
 * Never touches fields outside [TagSnapshot].
 */
object TagManager {

    init {
        // jaudiotagger is chatty; silence below WARNING on Android logcat.
        Logger.getLogger("org.jaudiotagger").level = Level.WARNING
    }

    fun read(file: File): TagResult {
        return try {
            val audio = AudioFileIO.read(file)
            val tag = audio.tagOrCreateAndSetDefault
            TagResult.Ok(
                TagSnapshot(
                    title = tag.getFirst(FieldKey.TITLE).ifBlank { null },
                    artist = tag.getFirst(FieldKey.ARTIST).ifBlank { null },
                    album = tag.getFirst(FieldKey.ALBUM).ifBlank { null },
                    albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST).ifBlank { null },
                    track = tag.getFirst(FieldKey.TRACK).ifBlank { null },
                    disc = tag.getFirst(FieldKey.DISC_NO).ifBlank { null },
                    year = tag.getFirst(FieldKey.YEAR).ifBlank { null },
                    genre = tag.getFirst(FieldKey.GENRE).ifBlank { null },
                    lyrics = tag.getFirst(FieldKey.LYRICS).ifBlank { null },
                ),
                AudioInfo(
                    durationMs = runCatching { audio.audioHeader.trackLength * 1000L }.getOrNull(),
                    mimeType = runCatching { audio.audioHeader.encodingType }.getOrNull(),
                )
            )
        } catch (e: Exception) {
            TagResult.Failed(humanize(e))
        }
    }

    /**
     * Writes [updates] (field → new value; null values skipped). Only writes
     * fields present in the map. Returns error string on failure, null on success.
     */
    fun write(file: File, updates: Map<FieldKey, String>): String? {
        if (!file.canWrite()) return "File is not writable."
        val beforeSize = file.length()
        return try {
            val audio = AudioFileIO.read(file)
            val tag = audio.tagOrCreateAndSetDefault
            for ((field, value) in updates) tag.setField(field, value)
            audio.commit()
            val afterSize = file.length()
            // Sanity: a tag edit must not truncate or explode the file.
            if (afterSize < beforeSize / 2 || afterSize > beforeSize * 2 + 1_000_000) {
                "Write verification failed (size $beforeSize → $afterSize). File left as-is by the tag library."
            } else null
        } catch (e: Exception) {
            humanize(e)
        }
    }

    fun embedLyrics(file: File, lyrics: String): String? =
        write(file, mapOf(FieldKey.LYRICS to lyrics))

    private fun humanize(e: Exception): String = when {
        e.message?.contains("Unable to read", ignoreCase = true) == true ->
            "Could not read this file (unsupported or corrupted)."
        e.message?.contains("Unable to write", ignoreCase = true) == true ->
            "Could not write tags (format may not support it)."
        else -> "Tag operation failed: ${e.message?.take(120) ?: e.javaClass.simpleName}"
    }

    private fun String.ifBlank(block: () -> String?): String? =
        if (isBlank()) block() else this
}
