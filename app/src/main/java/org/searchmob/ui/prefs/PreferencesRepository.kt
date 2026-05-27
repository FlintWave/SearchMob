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
            store.getString(PreferenceKeys.THEME_MODE, ThemeMode.SYSTEM.name),
            store.getBoolean(PreferenceKeys.DYNAMIC_COLOR, true),
            store.getBooleanMap(PreferenceKeys.ENGINE_ENABLED, engineDefaults),
            store.getBoolean(PreferenceKeys.HISTORY_ENABLED, false),
        ) { themeRaw, dynamic, engines, history ->
            UserPreferences(
                themeMode = ThemeMode.fromName(themeRaw),
                dynamicColor = dynamic,
                engineEnabled = engines,
                historyEnabled = history,
            )
        }

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
