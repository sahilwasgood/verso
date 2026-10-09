package org.lyrictag.companion.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import org.lyrictag.companion.core.TrackNormalizer

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val path: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val trackNo: Int?,
    val discNo: Int?,
    val year: String?,
    val genre: String?,
    val durationMs: Long?,
    val mimeType: String?,
    val lastModified: Long,
    val hasEmbeddedLyrics: Boolean,
    val hasExternalLyrics: Boolean,
    val hasCachedLyrics: Boolean = false,
    /** Normalized "title|artist|album" key, matches lyrics_cache keys. */
    val cacheKey: String = "",
    val needsReview: Boolean,
    val ignored: Boolean,
)

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val key: String,
    val lyrics: String,
    val updatedAt: Long,
)

/** Cached album-art URLs (image bytes live in Coil's disk cache). */
@Entity(tableName = "art_cache")
data class ArtCacheEntity(
    @PrimaryKey val key: String,
    val url: String,
    val updatedAt: Long,
)

/** Offline scrobble queue: submitted with their original timestamps when back online. */
@Entity(tableName = "scrobble_queue")
data class ScrobbleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val listenedAtSec: Long,
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE ignored = 0 ORDER BY artist, album, trackNo")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE ignored = 0")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks WHERE ignored = 0 AND (hasEmbeddedLyrics = 1 OR hasExternalLyrics = 1 OR hasCachedLyrics = 1)")
    fun observeWithLyrics(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks WHERE ignored = 0 AND hasEmbeddedLyrics = 0 AND hasExternalLyrics = 0 AND hasCachedLyrics = 0")
    fun observeMissingLyrics(): Flow<Int>

    @Query("SELECT * FROM tracks WHERE path = :path LIMIT 1")
    suspend fun byPath(path: String): TrackEntity?

    @Query("SELECT * FROM tracks")
    suspend fun allTracks(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("SELECT path FROM tracks")
    suspend fun allPaths(): List<String>

    /**
     * Paged library with server-side filter + search (keeps 10k+ libraries smooth).
     * filter: 0 all, 1 missing lyrics, 2 incomplete metadata, 3 needs review.
     */
    @Query(
        "SELECT * FROM tracks WHERE ignored = 0 " +
            "AND (:filter = 0 " +
            "OR (:filter = 1 AND hasEmbeddedLyrics = 0 AND hasExternalLyrics = 0 AND hasCachedLyrics = 0) " +
            "OR (:filter = 2 AND (title IS NULL OR title = '' OR artist IS NULL OR artist = '' OR album IS NULL OR album = '')) " +
            "OR (:filter = 3 AND needsReview = 1)) " +
            "AND (:q = '' OR title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%') " +
            "ORDER BY artist, album, trackNo"
    )
    fun paged(filter: Int, q: String): androidx.paging.PagingSource<Int, TrackEntity>

    @Query("DELETE FROM tracks WHERE path IN (:paths)")
    suspend fun deletePaths(paths: List<String>)

    /** Re-check lyric flags after embed/sidecar writes. */
    @Query("UPDATE tracks SET hasEmbeddedLyrics = :embedded, hasExternalLyrics = :external WHERE path = :path")
    suspend fun updateLyricFlags(path: String, embedded: Boolean, external: Boolean)

    /** Flag rows whose normalized key matches a cached lyric (full or title+artist loose). */
    @Query("UPDATE tracks SET hasCachedLyrics = 1 WHERE cacheKey = :full OR (cacheKey LIKE :loose || '%' AND :loose != '')")
    suspend fun markCached(full: String, loose: String)

    @Query("UPDATE tracks SET hasCachedLyrics = 0")
    suspend fun resetCached()

    @Query("UPDATE tracks SET ignored = :ignored WHERE path = :path")
    suspend fun setIgnored(path: String, ignored: Boolean)
}

@Dao
interface ScrobbleDao {
    @Insert
    suspend fun enqueue(e: ScrobbleEntity)

    @Query("SELECT * FROM scrobble_queue ORDER BY id LIMIT 100")
    suspend fun oldest(): List<ScrobbleEntity>

    @Query("DELETE FROM scrobble_queue WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("SELECT COUNT(*) FROM scrobble_queue")
    fun observePending(): Flow<Int>
}

@Dao
interface LyricsCacheDao {
    @Query("SELECT lyrics FROM lyrics_cache WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    /** Exact match preferred, title+artist prefix fallback. */
    @Query("SELECT lyrics FROM lyrics_cache WHERE `key` = :full OR (`key` LIKE :loose || '%' AND :loose != '') ORDER BY `key` = :full DESC LIMIT 1")
    suspend fun getSmart(full: String, loose: String): String?

    @Query("SELECT `key` FROM lyrics_cache")
    suspend fun allKeys(): List<String>

    @Query("DELETE FROM lyrics_cache WHERE `key` = :key")
    suspend fun deleteKey(key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache")
    fun clear()

    @Query("SELECT COUNT(*) FROM lyrics_cache")
    fun count(): Int

    @Query("SELECT COALESCE(SUM(LENGTH(lyrics)), 0) FROM lyrics_cache")
    fun bytes(): Long
}

@Database(entities = [TrackEntity::class, LyricsCacheEntity::class, ScrobbleEntity::class, ArtCacheEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun lyricsCache(): LyricsCacheDao
    abstract fun scrobbles(): ScrobbleDao
    abstract fun artCache(): ArtDao
}

@Dao
interface ArtDao {
    @Query("SELECT url FROM art_cache WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: ArtCacheEntity)

    @Query("DELETE FROM art_cache")
    fun clear()
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN hasCachedLyrics INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tracks ADD COLUMN cacheKey TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS scrobble_queue (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT, " +
                "durationMs INTEGER, listenedAtSec INTEGER NOT NULL)"
        )
    }
}

/** Split a "title|artist|album" key into (full, title+artist loose prefix). */
fun splitCacheKey(key: String): Pair<String, String> {
    val parts = key.split('|')
    if (parts.size != 3 || parts[0].isBlank() || parts[1].isBlank()) return key to ""
    return key to "${parts[0]}|${parts[1]}|"
}

/** Room-backed art-URL cache using normalized keys. */
class ArtCache(private val dao: ArtDao) {
    suspend fun get(title: String, artist: String): String? {
        val key = TrackNormalizer.cacheKey(title, artist, "")
        return dao.get(key)
    }

    suspend fun put(title: String, artist: String, url: String) {
        val key = TrackNormalizer.cacheKey(title, artist, "")
        dao.put(ArtCacheEntity(key, url, System.currentTimeMillis()))
    }
}

val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS art_cache (" +
                "`key` TEXT PRIMARY KEY NOT NULL, url TEXT NOT NULL, updatedAt INTEGER NOT NULL)"
        )
    }
}
/** Room-backed LyricsCache using normalized keys; flags matching tracks. */
class RoomLyricsCache(
    private val dao: LyricsCacheDao,
    private val tracks: TrackDao,
) : org.lyrictag.companion.lyrics.LyricsCache {
    override suspend fun get(title: String, artist: String, album: String): String? {
        val (full, loose) = splitCacheKey(TrackNormalizer.cacheKey(title, artist, album))
        return dao.getSmart(full, loose)
    }

    override suspend fun put(title: String, artist: String, album: String, lyrics: String) {
        val (full, loose) = splitCacheKey(TrackNormalizer.cacheKey(title, artist, album))
        dao.put(LyricsCacheEntity(full, lyrics, System.currentTimeMillis()))
        tracks.markCached(full, loose)
    }
}
