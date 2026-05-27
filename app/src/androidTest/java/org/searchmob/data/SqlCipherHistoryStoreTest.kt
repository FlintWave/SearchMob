package org.searchmob.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.searchmob.data.crypto.Dek
import org.searchmob.data.history.HistoryDatabase
import org.searchmob.data.history.HistoryEntry
import org.searchmob.data.history.SqlCipherHistoryStore

/**
 * On-device SQLCipher/Room history behaviour: OFF by default (no DB file), lazy creation on opt-in,
 * ciphertext on disk, TTL sweep, clear, and disable-deletes-file.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherHistoryStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dek = Dek.generate()
    private var now = 1_000_000L

    private fun store(ttlMs: Long = 1_000) =
        SqlCipherHistoryStore(
            context = context,
            dekProvider = { dek },
            ttlMs = ttlMs,
            nowMsProvider = { now },
        )

    private fun dbFile() = context.getDatabasePath(HistoryDatabase.DB_FILE_NAME)

    @Before
    @After
    fun cleanup() {
        store().disable()
    }

    @Test
    fun offByDefaultCreatesNoFileAndStoresNothing() {
        val s = store()
        assertFalse(s.enabled)
        s.add(HistoryEntry("private", now))
        assertTrue(s.list(now).isEmpty())
        assertFalse("no DB file should exist while disabled", dbFile().exists())
    }

    @Test
    fun enablingCreatesEncryptedDbAndRecords() {
        val s = store()
        s.setEnabled(true)
        s.add(HistoryEntry("kotlin", now))
        assertEquals(listOf("kotlin"), s.list(now).map { it.query })
        assertTrue("DB file created lazily on first write", dbFile().exists())

        // Contents are SQLCipher-encrypted: the plaintext query must not appear in the file bytes.
        val bytes = dbFile().readBytes()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("kotlin"))
    }

    @Test
    fun ttlExpiryIsEnforcedOnReadAndSwept() {
        val s = store(ttlMs = 1_000)
        s.setEnabled(true)
        s.add(HistoryEntry("old", now))
        now += 5_000
        s.add(HistoryEntry("fresh", now))
        // "old" is now 5s old (> 1s TTL) and excluded; "fresh" remains.
        assertEquals(listOf("fresh"), s.list(now).map { it.query })
    }

    @Test
    fun clearEmptiesButKeepsEnabledAndFile() {
        val s = store(ttlMs = 60_000)
        s.setEnabled(true)
        s.add(HistoryEntry("x", now))
        s.clear()
        assertTrue(s.enabled)
        assertTrue(s.list(now).isEmpty())
        assertTrue(dbFile().exists())
    }

    @Test
    fun disableDeletesTheDatabaseFile() {
        val s = store(ttlMs = 60_000)
        s.setEnabled(true)
        s.add(HistoryEntry("x", now))
        assertTrue(dbFile().exists())
        s.disable()
        assertFalse(s.enabled)
        assertFalse("DB file deleted on disable", dbFile().exists())
    }

    @Test
    fun closeHandleKeepsDataForReopen() {
        val s = store(ttlMs = 60_000)
        s.setEnabled(true)
        s.add(HistoryEntry("persist", now))
        s.closeHandle()
        // Re-opens lazily; data survived (handle closed, file not deleted).
        assertEquals(listOf("persist"), s.list(now).map { it.query })
    }
}
