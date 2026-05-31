package org.searchmob.ui.search

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.searchmob.R
import org.searchmob.engine.rank.DomainRanker
import org.searchmob.engine.rank.RankRule
import org.searchmob.engine.sort.SortMode
import org.searchmob.engine.summary.WikiSummary
import org.searchmob.engine.vertical.Vertical
import org.searchmob.server.SearchResult

/** Test tags so Compose UI tests can target each state distinctly. */
object SearchTestTags {
    const val QUERY_FIELD = "search_query_field"
    const val SUBMIT = "search_submit"
    const val LOADING = "search_loading"
    const val EMPTY = "search_empty"
    const val ERROR = "search_error"
    const val RETRY = "search_retry"
    const val RESULTS = "search_results"
    const val SUMMARY = "search_summary"
    const val SORT = "search_sort"
    const val VERTICAL = "search_vertical"
    const val SCOPE = "search_scope"
    const val DID_YOU_MEAN = "search_did_you_mean"
    const val RANK_MENU = "search_rank_menu"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val vertical by viewModel.vertical.collectAsStateWithLifecycle()
    val lenses by viewModel.lenses.collectAsStateWithLifecycle()
    val activeLens by viewModel.activeLens.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().testTag(SearchTestTags.QUERY_FIELD),
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                keyboardActions =
                    androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { viewModel.submit() },
                    ),
                keyboardOptions =
                    androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Search,
                    ),
                trailingIcon = {
                    IconButton(
                        onClick = { viewModel.submit() },
                        modifier = Modifier.testTag(SearchTestTags.SUBMIT),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_submit))
                    }
                },
            )

            // The scope selector shows whenever scopes exist (the sample scopes are seeded by
            // default), so it is usable before a search; the vertical/sort controls follow results.
            if (lenses.isNotEmpty()) {
                ScopeChips(lenses = lenses, active = activeLens, onSelect = viewModel::setActiveScope)
            }
            if (state is SearchUiState.Results) {
                VerticalTabs(current = vertical, onSelect = viewModel::setVertical)
                SortChips(current = sortMode, onSelect = viewModel::setSortMode)
            }

            when (val s = state) {
                SearchUiState.Idle ->
                    CenteredMessage(stringResource(R.string.search_idle))
                SearchUiState.Loading ->
                    LoadingState()
                SearchUiState.Empty ->
                    CenteredMessage(stringResource(R.string.search_empty), tag = SearchTestTags.EMPTY)
                is SearchUiState.Error ->
                    ErrorState(message = s.message, onRetry = viewModel::retry)
                is SearchUiState.Results ->
                    ResultsList(
                        results = s.results,
                        didYouMean = s.didYouMean,
                        showingResultsFor = s.showingResultsFor,
                        summary = s.summary,
                        onSearchCorrected = viewModel::searchCorrected,
                        onSetDomainRule = viewModel::setDomainRule,
                        onOpen = { url ->
                            // Open only the result URL; no query/identifier is attached.
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                    )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize().testTag(SearchTestTags.LOADING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.search_loading))
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    tag: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().let { if (tag != null) it.testTag(tag) else it },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().testTag(SearchTestTags.ERROR),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.search_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry, modifier = Modifier.testTag(SearchTestTags.RETRY)) {
            Text(stringResource(R.string.search_retry))
        }
    }
}

@Composable
private fun ResultsList(
    results: List<SearchResult>,
    didYouMean: String?,
    showingResultsFor: String?,
    summary: WikiSummary?,
    onSearchCorrected: (String) -> Unit,
    onSetDomainRule: (String, RankRule) -> Unit,
    onOpen: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(SearchTestTags.RESULTS),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (summary != null) {
            item { SummaryCard(summary = summary, onOpen = onOpen) }
        }
        if (didYouMean != null || showingResultsFor != null) {
            item {
                DidYouMeanBanner(
                    didYouMean = didYouMean,
                    showingResultsFor = showingResultsFor,
                    onSearchCorrected = onSearchCorrected,
                )
            }
        }
        if (results.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.search_empty),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(results) { result ->
            ResultCard(result = result, onOpen = onOpen, onSetDomainRule = onSetDomainRule)
        }
    }
}

/**
 * "Did you mean" / "Showing results for" banner. The suggestion form is tappable and re-runs the
 * search with the correction; the auto-corrected form is informational (the original found nothing).
 */
@Composable
private fun DidYouMeanBanner(
    didYouMean: String?,
    showingResultsFor: String?,
    onSearchCorrected: (String) -> Unit,
) {
    when {
        didYouMean != null ->
            Text(
                text = stringResource(R.string.search_did_you_mean) + " " + didYouMean,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(SearchTestTags.DID_YOU_MEAN)
                        // Give TalkBack a button role + a verb ("double-tap to search for X").
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Search for $didYouMean",
                        ) { onSearchCorrected(didYouMean) }
                        .padding(vertical = 8.dp),
            )
        showingResultsFor != null ->
            Text(
                text = stringResource(R.string.search_showing_results_for) + " " + showingResultsFor,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(SearchTestTags.DID_YOU_MEAN)
                        .padding(vertical = 8.dp),
            )
    }
}

/** Sort selector chips (Freshest + Relevant / Date / Relevance) shown above the results. */
@Composable
private fun SortChips(
    current: SortMode,
    onSelect: (SortMode) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag(SearchTestTags.SORT),
    ) {
        listOf(
            SortMode.FRESH_RELEVANT to R.string.sort_fresh,
            SortMode.DATE to R.string.sort_date,
            SortMode.RELEVANCE to R.string.sort_relevance,
        ).forEach { (mode, labelRes) ->
            FilterChip(
                selected = current == mode,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

/** Scope (lens) selector. "All" clears the active scope; each chip narrows results to that scope. */
@Composable
private fun ScopeChips(
    lenses: List<String>,
    active: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(SearchTestTags.SCOPE),
    ) {
        FilterChip(
            selected = active == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.scope_all)) },
        )
        lenses.forEach { name ->
            FilterChip(
                selected = active == name,
                onClick = { onSelect(name) },
                label = { Text(name) },
            )
        }
    }
}

/** Category tabs (Web / News / Forums / Academic). Each scopes the same query over the same engines. */
@Composable
private fun VerticalTabs(
    current: Vertical,
    onSelect: (Vertical) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(SearchTestTags.VERTICAL),
    ) {
        listOf(
            Vertical.WEB to R.string.vertical_web,
            Vertical.NEWS to R.string.vertical_news,
            Vertical.FORUMS to R.string.vertical_forums,
            Vertical.ACADEMIC to R.string.vertical_academic,
        ).forEach { (value, labelRes) ->
            FilterChip(
                selected = current == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

/** A knowledge-panel-style Wikipedia summary card shown above the results; tapping opens the article. */
@Composable
private fun SummaryCard(
    summary: WikiSummary,
    onOpen: (String) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(SearchTestTags.SUMMARY)
                // Merge the child texts into one summary announcement instead of disjointed items.
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "${summary.title}. ${summary.description}. ${summary.extract}. " +
                        "From Wikipedia. Double-tap to open the article."
                },
        onClick = { if (summary.url.isNotBlank()) onOpen(summary.url) },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = summary.title, style = MaterialTheme.typography.titleMedium)
            if (summary.description.isNotBlank()) {
                Text(
                    text = summary.description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = summary.extract, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.search_summary_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultCard(
    result: SearchResult,
    onOpen: (String) -> Unit,
    onSetDomainRule: (String, RankRule) -> Unit,
) {
    val host = remember(result.url) { DomainRanker.host(result.url) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(result.url) },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = result.title.ifBlank { result.url },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (host != null) {
                    RankMenu(onSelect = { rule -> onSetDomainRule(host, rule) })
                }
            }
            if (result.snippet.isNotBlank()) {
                Text(
                    text = result.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (result.engine.isNotBlank()) {
                Text(
                    text = stringResource(R.string.search_source_prefix) + " " + result.engine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Overflow menu on a result that sets the per-domain ranking rule for that result's site. */
@Composable
private fun RankMenu(onSelect: (RankRule) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(SearchTestTags.RANK_MENU),
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.rank_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val options =
                listOf(
                    R.string.rank_block to RankRule.BLOCK,
                    R.string.rank_lower to RankRule.LOWER,
                    R.string.rank_raise to RankRule.RAISE,
                    R.string.rank_pin to RankRule.PIN,
                    R.string.rank_normal to RankRule.NORMAL,
                )
            options.forEach { (labelRes, rule) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    onClick = {
                        expanded = false
                        onSelect(rule)
                    },
                )
            }
        }
    }
}

@Composable
private fun stringResource(id: Int): String = LocalContext.current.getString(id)
