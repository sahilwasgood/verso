package org.lyrictag.companion

import android.app.Application
import androidx.room.Room
import androidx.work.WorkManager
import org.lyrictag.companion.db.AppDatabase
import org.lyrictag.companion.db.RoomLyricsCache
import org.lyrictag.companion.lyrics.LyricsRepository
import org.lyrictag.companion.scan.LibraryScanner

/** Manual DI container: no Hilt → fewer deps, easier F-Droid audit. */
class LyricTagApp : Application() {

    val db: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "lyrictag.db")
            .addMigrations(org.lyrictag.companion.db.MIGRATION_1_2)
            .build()
    }
    val lyricsRepository: LyricsRepository by lazy {
        LyricsRepository.default(RoomLyricsCache(db.lyricsCache(), db.tracks()))
    }
    val scanner: LibraryScanner by lazy { LibraryScanner(this, db.tracks(), db.lyricsCache()) }
    val work: WorkManager by lazy { WorkManager.getInstance(this) }

    /** Clears cached lyrics (blocking — call off the main thread). */
    fun clearLyricCache() {
        db.lyricsCache().clear()
        kotlinx.coroutines.runBlocking { db.tracks().resetCached() }
    }

    /** True unless the user disabled automatic fetching in Settings. */
    fun autoFetchEnabled(): Boolean =
        getSharedPreferences("lyrictag", MODE_PRIVATE).getBoolean("auto_fetch", true)

    /** Human-readable cache size (blocking — call off the main thread). */
    fun lyricsCacheSize(): String {
        val n = db.lyricsCache().count()
        val kb = db.lyricsCache().bytes() / 1024
        return "$n entries · ${kb} KB"
    }

    companion object {
        fun of(ctx: android.content.Context): LyricTagApp =
            ctx.applicationContext as LyricTagApp
    }
}

/** Receiver-safe accessor (BroadcastReceiver has no Application handy). */
object ServiceLocator {
    fun lyricsRepository(ctx: android.content.Context): LyricsRepository =
        LyricTagApp.of(ctx).lyricsRepository
}
