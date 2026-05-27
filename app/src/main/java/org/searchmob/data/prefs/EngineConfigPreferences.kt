package org.searchmob.data.prefs

import org.searchmob.engine.EngineConfig

/**
 * Bridges encrypted preferences to the metasearch core's [EngineConfig] surface, repointing per-engine
 * enabled flags and BYO API keys from in-memory injection to the encrypted DataStore. The
 * [EngineRegistry][org.searchmob.engine.EngineRegistry] still takes a `Map<String, EngineConfig>` via
 * DI; this provider builds that map from decrypted prefs (and writes changes back) without touching
 * the registry. Keys use the conventions below so a single flat prefs map holds everything.
 *
 *   engine.<id>.enabled = "true" | "false"
 *   apiKey.<id>         = "<secret>"
 *
 * BYO API keys are therefore AES-256-GCM-encrypted at rest like every other preference.
 */
class EngineConfigPreferences(private val store: PreferencesStore) {
    /** Build the registry's config map for the given engine ids from decrypted prefs. */
    suspend fun configs(
        engineIds: Collection<String>,
        keyRequired: (String) -> Boolean = { false },
    ): Map<String, EngineConfig> {
        val prefs = store.getAll()
        return engineIds.associateWith { id ->
            val enabledPref = prefs[enabledKey(id)]
            val enabled = enabledPref?.toBooleanStrictOrNull() ?: !keyRequired(id)
            EngineConfig(engineId = id, enabled = enabled, apiKey = prefs[apiKeyKey(id)]?.ifBlank { null })
        }
    }

    suspend fun setEnabled(
        engineId: String,
        enabled: Boolean,
    ) {
        store.put(enabledKey(engineId), enabled.toString())
    }

    suspend fun setApiKey(
        engineId: String,
        apiKey: String?,
    ) {
        if (apiKey.isNullOrBlank()) store.remove(apiKeyKey(engineId)) else store.put(apiKeyKey(engineId), apiKey)
    }

    suspend fun apiKey(engineId: String): String? = store.get(apiKeyKey(engineId))?.ifBlank { null }

    private fun enabledKey(id: String) = "engine.$id.enabled"

    private fun apiKeyKey(id: String) = "apiKey.$id"
}
