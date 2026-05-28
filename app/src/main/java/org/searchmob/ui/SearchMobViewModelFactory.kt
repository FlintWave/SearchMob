package org.searchmob.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.searchmob.ui.search.SearchViewModel
import org.searchmob.ui.settings.SettingsViewModel

/**
 * Builds the UI ViewModels from a single [AppDependencies] graph so the search and settings surfaces
 * share the same preference state, engine state, and stores. The [SearchViewModel] is wired to the
 * [SettingsViewModel.recordQuery] recorder so query recording is gated on the history toggle.
 */
class SearchMobViewModelFactory(
    private val deps: AppDependencies,
) : ViewModelProvider.Factory {
    private val settingsViewModel: SettingsViewModel by lazy {
        SettingsViewModel(
            preferences = deps.preferencesRepository,
            historyStore = deps.historyStore,
            engineCatalog = deps.engineCatalog,
            engineEnabledSink = deps.engineEnabled,
            apiKeysSink = deps.apiKeys,
            engineConfig = deps.engineConfig,
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> settingsViewModel as T
            modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                SearchViewModel(
                    repository = deps.searchRepository,
                    onRecordQuery = { settingsViewModel.recordQuery(it) },
                ) as T
            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
}
