package org.searchmob.ui.prefs

import org.searchmob.ui.theme.DEFAULT_DARK_ID
import org.searchmob.ui.theme.DEFAULT_FONT_POINT_SIZE
import org.searchmob.ui.theme.DEFAULT_LIGHT_ID
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
    // The named theme filling each slot of the two-slot model (see `ui/theme/Themes.kt`). The quick
    // light/dark/system control swaps between these. Defaults keep the original SearchMob look.
    val lightThemeId: String = DEFAULT_LIGHT_ID,
    val darkThemeId: String = DEFAULT_DARK_ID,
    // Base UI font size in points (8..24, default 12). Scales the whole type scale; a local UI pref.
    val fontPointSize: Int = DEFAULT_FONT_POINT_SIZE,
    val engineEnabled: Map<String, Boolean> = emptyMap(),
    val historyEnabled: Boolean = false,
    val networkAccessEnabled: Boolean = false,
    val upstreamSuggestionsEnabled: Boolean = false,
    val summaryEnabled: Boolean = true,
    // Result sort order: "fresh" (freshness+relevance blend, default), "date", or "relevance".
    val sortMode: String = "fresh",
    val updateCheckEnabled: Boolean = true,
    // AI-slop / low-quality domain filter: "downrank" (on by default), "hide", or "off".
    val aiSlopMode: String = "downrank",
) {
    fun isEngineEnabled(engineId: String): Boolean = engineEnabled[engineId] ?: true
}

/** Storage keys for [PreferencesStore]. Kept in one place so the storage phase can mirror them. */
object PreferenceKeys {
    const val THEME_MODE = "theme_mode"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val LIGHT_THEME = "light_theme"
    const val DARK_THEME = "dark_theme"

    // Stored as a string because the store has no Int type; parsed back to a clamped point size.
    const val FONT_POINT_SIZE = "font_point_size"
    const val ENGINE_ENABLED = "engine_enabled"
    const val HISTORY_ENABLED = "history_enabled"
    const val PERSONALIZATION_ENABLED = "personalization_enabled"
    const val ONBOARDING_COMPLETED = "onboarding_completed"

    // The onboarding revision the user last saw; the wizard re-appears once after an update that adds
    // a step worth showing (when this is below the app's ONBOARDING_VERSION). Stored as a string
    // because the store has no Int type.
    const val ONBOARDING_VERSION = "onboarding_version"
    const val NETWORK_ACCESS_ENABLED = "network_access_enabled"
    const val NETWORK_ACCESS_TOKEN = "network_access_token"
    const val UPSTREAM_SUGGESTIONS_ENABLED = "upstream_suggestions_enabled"
    const val SUMMARY_ENABLED = "summary_enabled"
    const val SORT_MODE = "sort_mode"
    const val UPDATE_CHECK_ENABLED = "update_check_enabled"
    const val AI_SLOP_MODE = "ai_slop_mode"

    // Stored as a string because the store has no Long type; parsed back to a Long timestamp.
    const val LAST_UPDATE_CHECK_MS = "last_update_check_ms"

    // The newest release the last update check found (empty when none / up to date). These drive the
    // in-app and served-page "update available" banners and the notification, so a found update
    // survives a restart until a later check supersedes or clears it.
    const val PENDING_UPDATE_VERSION = "pending_update_version"
    const val PENDING_UPDATE_URL = "pending_update_url"
}
