package org.searchmob.data.prefs

import kotlinx.serialization.json.Json
import org.searchmob.engine.rank.GoggleRule
import org.searchmob.engine.rank.Lens
import org.searchmob.engine.rank.RankRule
import org.searchmob.engine.rank.RankingRules

/**
 * Persists the result-personalization [RankingRules] as a single JSON blob in the DEK-encrypted
 * preferences store, so domain rules, lenses, and imported goggles are AES-256-GCM-encrypted at rest
 * like every other preference. Reads are fail-soft: a missing or corrupt value yields [RankingRules.EMPTY].
 * The stored JSON is exactly what [exportJson] returns and [importJson] accepts, so backup/sharing is
 * just the same document.
 */
class RankingPreferences(private val store: PreferencesStore) {
    suspend fun load(): RankingRules {
        val raw = store.get(KEY) ?: return RankingRules.EMPTY
        return runCatching { json.decodeFromString<RankingRules>(raw) }.getOrDefault(RankingRules.EMPTY)
    }

    suspend fun save(rules: RankingRules) {
        store.put(KEY, json.encodeToString(rules))
    }

    suspend fun setDomainRule(
        domain: String,
        rule: RankRule,
    ) {
        val current = load()
        val map = current.domainRules.toMutableMap()
        val key = domain.lowercase().removePrefix("www.")
        if (rule == RankRule.NORMAL) map.remove(key) else map[key] = rule
        save(current.copy(domainRules = map))
    }

    suspend fun upsertLens(lens: Lens) {
        val current = load()
        val lenses = current.lenses.filterNot { it.name.equals(lens.name, ignoreCase = true) } + lens
        save(current.copy(lenses = lenses))
    }

    suspend fun removeLens(name: String) {
        val current = load()
        save(
            current.copy(
                lenses = current.lenses.filterNot { it.name.equals(name, ignoreCase = true) },
                activeLens = current.activeLens?.takeUnless { it.equals(name, ignoreCase = true) },
            ),
        )
    }

    suspend fun setActiveLens(name: String?) {
        save(load().copy(activeLens = name))
    }

    suspend fun importGoggles(goggles: List<GoggleRule>) {
        save(load().copy(goggles = goggles))
    }

    suspend fun exportJson(): String = json.encodeToString(load())

    /** Replace all rules from an exported JSON document. Returns true on success, false if unparseable. */
    suspend fun importJson(text: String): Boolean {
        val parsed = runCatching { json.decodeFromString<RankingRules>(text) }.getOrNull() ?: return false
        save(parsed)
        return true
    }

    private companion object {
        const val KEY = "ranking.rules"
        val json = Json { ignoreUnknownKeys = true }
    }
}
