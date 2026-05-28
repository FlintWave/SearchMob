package org.searchmob.ui.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.searchmob.ui.theme.ThemeMode

/**
 * Typed facade over [PreferencesStore]. Exposes a single [preferences] [Flow] that the UI observes for
 * apply-immediately re-composition, plus suspend writers for each setting. Engine defaults (all
 * enabled) are supplied at construction from the live engine registry so newly added engines default
 * to enabled.
 */
class PreferencesRepository(
    private val store: PreferencesStore,
    private val knownEngineIds: List<String> = emptyList(),
) {
    private val engineDefaults: Map<String, Boolean> = knownEngineIds.associateWith { true }

    val preferences: Flow<UserPreferences> =
        combine(
            // First five flows feed the typed five-arg combine; the sixth is folded in below because
            // there is no typed six-arg combine overload. Keep this in sync with the data class fields.
            combine(
                store.getString(PreferenceKeys.THEME_MODE, ThemeMode.SYSTEM.name),
                store.getBoolean(PreferenceKeys.DYNAMIC_COLOR, true),
                store.getBooleanMap(PreferenceKeys.ENGINE_ENABLED, engineDefaults),
                store.getBoolean(PreferenceKeys.HISTORY_ENABLED, false),
                store.getBoolean(PreferenceKeys.NETWORK_ACCESS_ENABLED, false),
            ) { themeRaw, dynamic, engines, history, networkAccess ->
                UserPreferences(
                    themeMode = ThemeMode.fromName(themeRaw),
                    dynamicColor = dynamic,
                    engineEnabled = engines,
                    historyEnabled = history,
                    networkAccessEnabled = networkAccess,
                )
            },
            store.getBoolean(PreferenceKeys.UPSTREAM_SUGGESTIONS_ENABLED, false),
        ) { base, upstreamSuggestions ->
            base.copy(upstreamSuggestionsEnabled = upstreamSuggestions)
        }

    /** Whether the first-run wizard has been completed or skipped. Gates the wizard in navigation. */
    val onboardingCompleted: Flow<Boolean> =
        store.getBoolean(PreferenceKeys.ONBOARDING_COMPLETED, false)

    suspend fun setOnboardingCompleted(completed: Boolean) =
        store.setBoolean(PreferenceKeys.ONBOARDING_COMPLETED, completed)

    /**
     * Whether the opt-in network mode is enabled. OFF by default; the server binds to loopback only
     * unless this is true. The service observes this to (re)bind the embedded server.
     */
    val networkAccessEnabled: Flow<Boolean> =
        store.getBoolean(PreferenceKeys.NETWORK_ACCESS_ENABLED, false)

    suspend fun setNetworkAccessEnabled(enabled: Boolean) =
        store.setBoolean(PreferenceKeys.NETWORK_ACCESS_ENABLED, enabled)

    /**
     * Whether the opt-in upstream (web) suggestions source is enabled. OFF by default: with it off,
     * suggestions come only from the local encrypted history and nothing is sent off-device. When ON,
     * partial queries are sent to DuckDuckGo's suggestion service through the privacy proxy as the user
     * types. The /suggest route observes this to decide whether to contact the upstream provider.
     */
    val upstreamSuggestionsEnabled: Flow<Boolean> =
        store.getBoolean(PreferenceKeys.UPSTREAM_SUGGESTIONS_ENABLED, false)

    suspend fun setUpstreamSuggestionsEnabled(enabled: Boolean) =
        store.setBoolean(PreferenceKeys.UPSTREAM_SUGGESTIONS_ENABLED, enabled)

    suspend fun setThemeMode(mode: ThemeMode) = store.setString(PreferenceKeys.THEME_MODE, mode.name)

    suspend fun setDynamicColor(enabled: Boolean) = store.setBoolean(PreferenceKeys.DYNAMIC_COLOR, enabled)

    suspend fun setHistoryEnabled(enabled: Boolean) = store.setBoolean(PreferenceKeys.HISTORY_ENABLED, enabled)

    suspend fun setEngineEnabled(
        engineId: String,
        enabled: Boolean,
        current: Map<String, Boolean>,
    ) {
        store.setBooleanMap(PreferenceKeys.ENGINE_ENABLED, current + (engineId to enabled))
    }
}
