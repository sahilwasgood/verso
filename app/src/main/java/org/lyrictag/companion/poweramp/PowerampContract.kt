package org.lyrictag.companion.poweramp

/**
 * Minimal vendored Poweramp Lyrics-plugin contract.
 *
 * Values copied from the official Poweramp API documentation / example
 * (github.com/maxmpz/powerampapi, PowerampAPI.Lyrics + Track extras).
 * This avoids depending on the whole api-lib module while staying
 * byte-compatible. License note: see api/LICENSE-poweramp.txt.
 */
object PowerampContract {
    const val ACTION_NEED_LYRICS = "com.maxmpz.audioplayer.ACTION_NEED_LYRICS"
    const val ACTION_UPDATE_LYRICS = "com.maxmpz.audioplayer.ACTION_UPDATE_LYRICS"
    const val ACTION_LYRICS_LINK = "com.maxmpz.audioplayer.ACTION_LYRICS_LINK"

    const val EXTRA_ID = "id"
    const val EXTRA_LYRICS = "lyrics"
    const val EXTRA_INFO_LINE = "info_line"
    const val EXTRA_TRACK = "track"

    const val TRACK_REAL_ID = "realId"
    const val TRACK_TITLE = "title"
    const val TRACK_ALBUM = "album"
    const val TRACK_ARTIST = "artist"
    const val TRACK_DURATION_MS = "durMs"
    const val TRACK_FILE_TYPE = "fileType"

    const val NO_ID = -1L
    const val FILE_TYPE_STREAM = 23

    const val POWERAMP_PACKAGE = "com.maxmpz.audioplayer"
    const val POWERAMP_API_RECEIVER = "com.maxmpz.audioplayer.player.PowerampAPIReceiver"
    const val EXTRA_PACKAGE = "pak"
    const val INFO_LINE = "Lyrics via LyricTag"
}
