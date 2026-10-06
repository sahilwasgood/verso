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
    val needsReview: Boolean,
    val ignored: Boolean,
)

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val key: String,
    val lyrics: String,
    val updatedAt: Long,
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE ignored = 0 ORDER BY artist, album, trackNo")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE ignored = 0")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks WHERE ignored = 0 AND hasEmbeddedLyrics = 0 AND hasExternalLyrics = 0")
    fun observeMissingLyrics(): Flow<Int>

    @Query("SELECT * FROM tracks WHERE path = :path LIMIT 1")
    suspend fun byPath(path: String): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET ignored = :ignored WHERE path = :path")
    suspend fun setIgnored(path: String, ignored: Boolean)
}

@Dao
interface LyricsCacheDao {
    @Query("SELECT lyrics FROM lyrics_cache WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: LyricsCacheEntity)
}

@Database(entities = [TrackEntity::class, LyricsCacheEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun lyricsCache(): LyricsCacheDao
}

/** Room-backed LyricsCache using normalized keys. */
class RoomLyricsCache(private val dao: LyricsCacheDao) : org.lyrictag.companion.lyrics.LyricsCache {
    override suspend fun get(title: String, artist: String, album: String): String? =
        dao.get(TrackNormalizer.cacheKey(title, artist, album))

    override suspend fun put(title: String, artist: String, album: String, lyrics: String) =
        dao.put(LyricsCacheEntity(TrackNormalizer.cacheKey(title, artist, album), lyrics, System.currentTimeMillis()))
}
