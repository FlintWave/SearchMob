package org.searchmob.data.history

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/**
 * Opt-in, on-device search history backed by a SQLCipher-encrypted Room database
 * (`net.zetetic:sqlcipher-android`, NOT the deprecated `android-database-sqlcipher`). The SQLCipher
 * passphrase is the shared DEK, so the whole DB file (rows and indices) is encrypted at rest.
 *
 * History is OFF by default: while disabled, no DB file is created or opened and the current session
 * stays in memory only ([list] returns empty, [add] is a no-op). [setEnabled]`(true)` creates the
 * encrypted DB lazily on first use.
 *
 * TTL is enforced inline: every [list] sweeps expired rows opportunistically and returns only
 * non-expired ones; there is no background timer or wake-lock. [clear] deletes all rows but keeps the
 * DB; [disable] closes the handle and deletes the DB file.
 *
 * [dekProvider] is invoked lazily so a locked (zero-knowledge) vault never forces a DEK to exist; it
 * throws while locked, which is the intended "history unavailable until unlock" behaviour.
 */
class SqlCipherHistoryStore(
    private val context: Context,
    private val dekProvider: () -> ByteArray,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val nowMsProvider: () -> Long = System::currentTimeMillis,
) : HistoryStore {
    private var on = false
    private var db: HistoryDatabase? = null

    override val enabled: Boolean get() = on

    override fun setEnabled(enabled: Boolean) {
        if (enabled) {
            on = true
            // DB is created lazily on first access; nothing is written to disk just by enabling.
        } else {
            disable()
        }
    }

    override fun add(entry: HistoryEntry) {
        if (!on) return
        val dao = database().historyDao()
        sweep(dao)
        dao.insert(HistoryRow(query = entry.query, timestampMs = entry.timestampMs))
    }

    override fun list(nowMs: Long): List<HistoryEntry> {
        if (!on) return emptyList()
        val dao = database().historyDao()
        sweep(dao, nowMs)
        return dao
            .listSince(nowMs - ttlMs)
            .map { HistoryEntry(it.query, it.timestampMs) }
    }

    override fun suggest(
        prefix: String,
        limit: Int,
        nowMs: Long,
    ): List<String> {
        if (!on || prefix.isBlank() || limit <= 0) return emptyList()
        // Fail-soft: a locked vault makes the DEK provider throw, and we must never let a suggestion
        // lookup surface that as an error (typing would break). Treat any failure as "no suggestions".
        return runCatching {
            val dao = database().historyDao()
            sweep(dao, nowMs)
            dao.suggestSince(prefix, nowMs - ttlMs, limit)
        }.getOrDefault(emptyList())
    }

    override fun clear() {
        if (db == null && !dbFile().exists()) return
        database().historyDao().deleteAll()
    }

    override fun disable() {
        on = false
        db?.close()
        db = null
        deleteDbFiles()
    }

    override fun closeHandle() {
        // Drop the live (DEK-keyed) handle but keep history enabled and the encrypted file on disk.
        // The next access re-opens it lazily once the DEK is available again.
        db?.close()
        db = null
    }

    private fun sweep(
        dao: HistoryDao,
        nowMs: Long = nowMsProvider(),
    ) {
        dao.deleteOlderThan(nowMs - ttlMs)
    }

    private fun database(): HistoryDatabase = db ?: openDatabase().also { db = it }

    private fun openDatabase(): HistoryDatabase {
        ensureNativeLib()
        val factory = SupportOpenHelperFactory(dekProvider())
        return Room
            .databaseBuilder(context, HistoryDatabase::class.java, dbFile().absolutePath)
            .openHelperFactory(factory)
            .build()
    }

    private fun dbFile(): File = context.getDatabasePath(HistoryDatabase.DB_FILE_NAME)

    private fun deleteDbFiles() {
        val base = dbFile()
        // Also remove the WAL/SHM sidecar files Room/SQLCipher may create.
        listOf(base, File(base.path + "-wal"), File(base.path + "-shm"), File(base.path + "-journal"))
            .forEach { if (it.exists()) it.delete() }
    }

    companion object {
        const val DEFAULT_TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

        @Volatile private var nativeLibLoaded = false

        private fun ensureNativeLib() {
            if (!nativeLibLoaded) {
                synchronized(this) {
                    if (!nativeLibLoaded) {
                        System.loadLibrary("sqlcipher")
                        nativeLibLoaded = true
                    }
                }
            }
        }
    }
}
