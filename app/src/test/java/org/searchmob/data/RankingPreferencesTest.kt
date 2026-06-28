package org.searchmob.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.data.prefs.Preferences
import org.searchmob.data.prefs.PreferencesStore
import org.searchmob.data.prefs.RankingPreferences
import org.searchmob.engine.rank.DEFAULT_SAMPLE_LENSES
import org.searchmob.engine.rank.GoggleRule
import org.searchmob.engine.rank.Lens
import org.searchmob.engine.rank.RankRule

private class FakeRankingStore : PreferencesStore {
    private val map = mutableMapOf<String, String>()

    override fun observe(): Flow<Preferences> = flowOf(map.toMap())

    override suspend fun getAll(): Preferences = map.toMap()

    override suspend fun get(key: String): String? = map[key]

    override suspend fun put(
        key: String,
        value: String,
    ) {
        map[key] = value
    }

    override suspend fun remove(key: String) {
        map.remove(key)
    }

    override suspend fun clear() = map.clear()
}

/** A store that throws on every read, simulating a locked vault (DEK wiped from memory). */
private class LockedStore : PreferencesStore {
    override fun observe(): Flow<Preferences> = throw IllegalStateException("vault is locked: DEK not present in memory")

    override suspend fun getAll(): Preferences = error("vault is locked: DEK not present in memory")

    override suspend fun get(key: String): String? = error("vault is locked: DEK not present in memory")

    override suspend fun put(key: String, value: String) = error("vault is locked: DEK not present in memory")

    override suspend fun remove(key: String) = error("vault is locked: DEK not present in memory")

    override suspend fun clear() = error("vault is locked: DEK not present in memory")
}

class RankingPreferencesTest {
    @Test
    fun domainRuleSetAndClear() =
        runTest {
            val prefs = RankingPreferences(FakeRankingStore())
            prefs.setDomainRule("A.com", RankRule.BLOCK)
            assertEquals(RankRule.BLOCK, prefs.load().domainRules["a.com"])
            prefs.setDomainRule("a.com", RankRule.NORMAL)
            assertTrue(prefs.load().domainRules.isEmpty())
        }

    @Test
    fun freshProfileSeedsTheSampleScopes() =
        runTest {
            // The sample scopes are the default lens set so the selector is useful before any search.
            val prefs = RankingPreferences(FakeRankingStore())
            assertEquals(DEFAULT_SAMPLE_LENSES, prefs.load().lenses)
        }

    @Test
    fun lensUpsertSelectAndDelete() =
        runTest {
            val prefs = RankingPreferences(FakeRankingStore())
            prefs.upsertLens(Lens("Docs", includeDomains = listOf("kotlinlang.org")))
            prefs.setActiveLens("Docs")
            assertEquals("Docs", prefs.load().activeLens)
            // The new lens is added alongside the seeded samples.
            assertTrue(prefs.load().lenses.any { it.name == "Docs" })
            prefs.removeLens("Docs")
            assertFalse(prefs.load().lenses.any { it.name == "Docs" })
            assertNull(prefs.load().activeLens)
        }

    @Test
    fun exportThenImportRoundTrips() =
        runTest {
            val source = RankingPreferences(FakeRankingStore())
            source.setDomainRule("a.com", RankRule.RAISE)
            source.upsertLens(Lens("L", includeDomains = listOf("x.com")))
            source.importGoggles(listOf(GoggleRule("spam.example", RankRule.BLOCK)))
            val json = source.exportJson()

            val restored = RankingPreferences(FakeRankingStore())
            assertTrue(restored.importJson(json))
            assertEquals(source.load(), restored.load())
        }

    @Test
    fun importJsonRejectsGarbage() =
        runTest {
            val prefs = RankingPreferences(FakeRankingStore())
            assertEquals(false, prefs.importJson("not json"))
        }

    @Test
    fun loadReturnsEmptyWhenVaultIsLocked() =
        runTest {
            val prefs = RankingPreferences(LockedStore())
            val rules = prefs.load()
            assertEquals(DEFAULT_SAMPLE_LENSES, rules.lenses)
            assertTrue(rules.domainRules.isEmpty())
        }
}
