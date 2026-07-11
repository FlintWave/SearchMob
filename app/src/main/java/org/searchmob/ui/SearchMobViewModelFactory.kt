package org.searchmob.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.searchmob.ui.history.HistoryViewModel
import org.searchmob.ui.search.SearchViewModel
import org.searchmob.ui.settings.SettingsViewModel

/**
 * Builds the UI ViewModels from a single [AppDependencies] graph so the search and settings surfaces
 * share the same preference state, engine state, and stores.
 *
 * Every [create] call constructs a FRESH instance. The nav host scopes each ViewModel to its
 * destination's NavBackStackEntry, so a cached instance would come back with a cleared (dead)
 * viewModelScope on the next visit and silently stop persisting anything. Query recording is wired
 * to the application-scoped [AppDependencies.recordQuery] rather than any ViewModel, so history
 * keeps working (and stays gated on the history toggle) no matter which screens are alive.
 */
class SearchMobViewModelFactory(
    private val deps: AppDependencies,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(
                    preferences = deps.preferencesRepository,
                    historyStore = deps.historyStore,
                    engineCatalog = deps.engineCatalog,
                    engineEnabledSink = deps.engineEnabled,
                    apiKeysSink = deps.apiKeys,
                    engineConfig = deps.engineConfig,
                    rankingPreferences = deps.rankingPreferences,
                    personalizationPreferences = deps.personalizationPreferences,
                ) as T
            modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
                HistoryViewModel(historyStore = deps.historyStore) as T
            modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                SearchViewModel(
                    repository = deps.searchRepository,
                    onRecordQuery = deps::recordQuery,
                    rankingPreferences = deps.rankingPreferences,
                    preferences = deps.preferencesRepository,
                    personalizationPreferences = deps.personalizationPreferences,
                ) as T
            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
}
