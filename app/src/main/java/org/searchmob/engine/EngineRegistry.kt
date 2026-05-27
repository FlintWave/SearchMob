package org.searchmob.engine

import okhttp3.OkHttpClient

/** Per-engine configuration: enabled flag and an optional user-supplied API key. */
data class EngineConfig(
    val engineId: String,
    val enabled: Boolean = true,
    val apiKey: String? = null,
)

/**
 * Holds available adapters and their config. Defaults: all free (non-key) engines enabled; key-gated
 * adapters are inactive until a key is supplied. A Google adapter is a permanent non-goal and is
 * rejected at construction.
 */
class EngineRegistry(
    private val adapters: List<EngineAdapter>,
    private val configs: Map<String, EngineConfig> =
        adapters.associate { it.id to EngineConfig(it.id, enabled = !it.requiresApiKey) },
    private val timeoutMs: Long = 5_000L,
) {
    init {
        require(adapters.none { it.id.equals("google", ignoreCase = true) }) {
            "Google is a permanent non-goal (JS wall, litigation, and risk to the user's own IP)."
        }
    }

    /** Adapters that should be queried now, each paired with its [EngineContext] (including any key). */
    fun activeEngines(httpClient: OkHttpClient): List<Pair<EngineAdapter, EngineContext>> =
        adapters.mapNotNull { adapter ->
            val config = configs[adapter.id] ?: return@mapNotNull null
            if (!config.enabled) return@mapNotNull null
            if (adapter.requiresApiKey && config.apiKey.isNullOrBlank()) return@mapNotNull null
            adapter to EngineContext(httpClient = httpClient, apiKey = config.apiKey, timeoutMs = timeoutMs)
        }
}
