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
        Room.databaseBuilder(this, AppDatabase::class.java, "lyrictag.db").build()
    }
    val lyricsRepository: LyricsRepository by lazy {
        LyricsRepository.default(RoomLyricsCache(db.lyricsCache()))
    }
    val scanner: LibraryScanner by lazy { LibraryScanner(this, db.tracks()) }
    val work: WorkManager by lazy { WorkManager.getInstance(this) }

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
