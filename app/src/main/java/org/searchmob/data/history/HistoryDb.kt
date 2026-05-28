package org.searchmob.data.history

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * Room row for a single stored search query. The whole database file (including this table and its
 * indices) is SQLCipher-encrypted at rest, so nothing here is ever written in plaintext.
 */
@Entity(tableName = "history")
data class HistoryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val timestampMs: Long,
)

@Dao
interface HistoryDao {
    @Insert
    fun insert(row: HistoryRow)

    /** Non-expired rows (newest first). [cutoffMs] is the oldest timestamp still within the TTL. */
    @Query("SELECT * FROM history WHERE timestampMs >= :cutoffMs ORDER BY timestampMs DESC")
    fun listSince(cutoffMs: Long): List<HistoryRow>

    /**
     * Distinct non-expired past queries that start with [prefix] (case-insensitive via NOCASE),
     * most-recent first, capped to [limit]. Used to power local search suggestions. The
     * most-recent ordering groups by query and ranks each distinct query by its newest occurrence,
     * so a query typed twice is not duplicated and surfaces at its latest position.
     */
    @Query(
        "SELECT query FROM history WHERE timestampMs >= :cutoffMs AND query LIKE :prefix || '%' " +
            "COLLATE NOCASE GROUP BY query COLLATE NOCASE ORDER BY MAX(timestampMs) DESC LIMIT :limit",
    )
    fun suggestSince(
        prefix: String,
        cutoffMs: Long,
        limit: Int,
    ): List<String>

    /** Opportunistic TTL sweep: physically delete rows older than the cutoff. */
    @Query("DELETE FROM history WHERE timestampMs < :cutoffMs")
    fun deleteOlderThan(cutoffMs: Long)

    @Query("DELETE FROM history")
    fun deleteAll()
}

@Database(entities = [HistoryRow::class], version = 1, exportSchema = true)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        const val DB_FILE_NAME = "searchmob-history.db"
    }
}
