package org.searchmob.ui.prefs

import org.searchmob.ui.theme.ThemeMode

/**
 * The full set of user-tunable, plaintext-safe preferences. First-run defaults encode the locked
 * decisions: Follow-system theme, dynamic color on (where supported), all default engines enabled,
 * search history OFF (store-nothing default).
 *
 * [engineEnabled] is keyed by engine id (see `EngineAdapter.id`); a missing key defaults to enabled.
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val engineEnabled: Map<String, Boolean> = emptyMap(),
    val historyEnabled: Boolean = false,
    val networkAccessEnabled: Boolean = false,
    val upstreamSuggestionsEnabled: Boolean = false,
    val summaryEnabled: Boolean = true,
    // Result sort order: "fresh" (freshness+relevance blend, default), "date", or "relevance".
    val sortMode: String = "fresh",
    val updateCheckEnabled: Boolean = true,
) {
    fun isEngineEnabled(engineId: String): Boolean = engineEnabled[engineId] ?: true
}

/** Storage keys for [PreferencesStore]. Kept in one place so the storage phase can mirror them. */
object PreferenceKeys {
    const val THEME_MODE = "theme_mode"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val ENGINE_ENABLED = "engine_enabled"
    const val HISTORY_ENABLED = "history_enabled"
    const val ONBOARDING_COMPLETED = "onboarding_completed"
    const val NETWORK_ACCESS_ENABLED = "network_access_enabled"
    const val UPSTREAM_SUGGESTIONS_ENABLED = "upstream_suggestions_enabled"
    const val SUMMARY_ENABLED = "summary_enabled"
    const val SORT_MODE = "sort_mode"
    const val UPDATE_CHECK_ENABLED = "update_check_enabled"

    // Stored as a string because the store has no Long type; parsed back to a Long timestamp.
    const val LAST_UPDATE_CHECK_MS = "last_update_check_ms"
}
