package org.searchmob.engine

import okhttp3.OkHttpClient
import org.searchmob.engine.http.Politeness

/** Per-engine configuration: enabled flag and an optional user-supplied API key. */
data class EngineConfig(
    val engineId: String,
    val enabled: Boolean = true,
    val apiKey: String? = null,
)

/**
 * Holds available adapters and their config. Defaults: all free (non-key) engines enabled; key-gated
 * adapters are inactive until a key is supplied. When a keyed adapter is active it supersedes its free
 * counterpart (so we don't double-hit the same upstream). A Google adapter is a permanent non-goal and
 * is rejected at construction.
 */
class EngineRegistry(
    private val adapters: List<EngineAdapter>,
    private val configs: Map<String, EngineConfig> =
        adapters.associate { it.id to EngineConfig(it.id, enabled = !it.requiresApiKey) },
    private val timeoutMs: Long = 5_000L,
    private val politeness: Politeness = Politeness(),
) {
    init {
        require(adapters.none { it.id.equals("google", ignoreCase = true) }) {
            "Google is a permanent non-goal (JS wall, litigation, and risk to the user's own IP)."
        }
    }

    /**
     * Adapters that should be queried now, each paired with its [EngineContext] (including any key).
     * [languageRegion] tailors capable engines to the active UI locale, or null to stay region-neutral.
     */
    fun activeEngines(
        httpClient: OkHttpClient,
        languageRegion: LanguageRegion? = null,
    ): List<Pair<EngineAdapter, EngineContext>> {
        val enabled =
            adapters.filter { adapter ->
                val config = configs[adapter.id]
                when {
                    config == null || !config.enabled -> false
                    adapter.requiresApiKey && config.apiKey.isNullOrBlank() -> false
                    else -> true
                }
            }
        val superseded = enabled.flatMap { it.supersedes }.toSet()
        return enabled
            .filterNot { it.id in superseded }
            .map { adapter ->
                adapter to
                    EngineContext(
                        httpClient = httpClient,
                        apiKey = configs[adapter.id]?.apiKey,
                        timeoutMs = timeoutMs,
                        politeness = politeness,
                        languageRegion = languageRegion,
                    )
            }
    }
}
