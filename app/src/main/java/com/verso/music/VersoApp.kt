package com.verso.music

import android.app.Application
import androidx.room.Room
import androidx.work.WorkManager
import com.verso.music.db.AppDatabase
import com.verso.music.db.RoomLyricsCache
import com.verso.music.lyrics.LyricsRepository
import com.verso.music.scan.LibraryScanner

/** Manual DI container: no Hilt → fewer deps, easier F-Droid audit. */
class VersoApp : Application() {

    val db: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "lyrictag.db")
            .addMigrations(
                com.verso.music.db.MIGRATION_1_2,
                com.verso.music.db.MIGRATION_2_3,
                com.verso.music.db.MIGRATION_3_4,
                com.verso.music.db.MIGRATION_4_5,
            )
            .build()
    }
    /** Rebuilt on each access so provider toggles apply immediately. */
    val lyricsRepository: LyricsRepository
        get() = LyricsRepository.forPrefs(this, RoomLyricsCache(db.lyricsCache(), db.tracks()))
    val scanner: LibraryScanner by lazy { LibraryScanner(this, db.tracks(), db.lyricsCache()) }
    val artwork: com.verso.music.artwork.ArtworkRepository by lazy {
        com.verso.music.artwork.ArtworkRepository(
            com.verso.music.db.ArtCache(db.artCache())
        )
    }
    val work: WorkManager by lazy { WorkManager.getInstance(this) }

    /** Clears cached lyrics + artwork URLs (blocking - call off the main thread). */
    fun clearLyricCache() {
        db.lyricsCache().clear()
        db.artCache().clear()
        kotlinx.coroutines.runBlocking { db.tracks().resetCached() }
    }

    /** True unless the user disabled automatic fetching in Settings. */
    fun autoFetchEnabled(): Boolean =
        getSharedPreferences("lyrictag", MODE_PRIVATE).getBoolean("auto_fetch", true)

    /** Human-readable cache size (blocking - call off the main thread). */
    fun lyricsCacheSize(): String {
        val n = db.lyricsCache().count()
        val kb = db.lyricsCache().bytes() / 1024
        return "$n entries · ${kb} KB"
    }

    companion object {
        fun of(ctx: android.content.Context): VersoApp =
            ctx.applicationContext as VersoApp
    }
}

/** Receiver-safe accessor (BroadcastReceiver has no Application handy). */
object ServiceLocator {
    fun lyricsRepository(ctx: android.content.Context): LyricsRepository =
        VersoApp.of(ctx).lyricsRepository
}
