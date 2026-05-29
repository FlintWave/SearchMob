package org.searchmob.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.searchmob.R

object AboutTestTags {
    const val SCREEN = "about_screen"
    const val VERSION = "about_version"
    const val REPO_BUTTON = "about_repo_button"
    const val BUG_BUTTON = "about_bug_button"
}

/** Public GitHub repository for the project; opened via an external [Intent.ACTION_VIEW]. */
const val REPO_URL = "https://github.com/FlintWave/SearchMob"

/** Issue-report entry point: the new-issue chooser surfaces the bug-report template. */
const val BUG_URL = "https://github.com/FlintWave/SearchMob/issues/new/choose"

/**
 * Stateful entry point: resolves the app version dynamically and the repo-open intent, then delegates
 * to the stateless [AboutScreenContent] so the latter can be exercised in Compose UI tests.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    AboutScreenContent(
        version = AppVersion.of(context),
        onBack = onBack,
        onOpenRepo = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))) },
        onReportBug = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BUG_URL))) },
        modifier = modifier,
    )
}

/**
 * Stateless About / Privacy screen: a [Scaffold] with a top bar wrapping the calm, scannable
 * privacy copy. Free of platform singletons so it is testable; [onOpenRepo] performs the external open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreenContent(
    version: String,
    onBack: () -> Unit,
    onOpenRepo: () -> Unit,
    onReportBug: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(AboutTestTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(str(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str(R.string.back))
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. What SearchMob is.
            Section(str(R.string.about_what_title)) {
                Body(str(R.string.about_what_body))
            }

            // 2. We never receive your data.
            Section(str(R.string.about_nodata_title)) {
                Body(str(R.string.about_nodata_body))
                Bullets(
                    str(R.string.about_nodata_item_telemetry),
                    str(R.string.about_nodata_item_accounts),
                    str(R.string.about_nodata_item_ids),
                    str(R.string.about_nodata_item_outbound),
                )
            }

            // 3. How it protects you when searching.
            Section(str(R.string.about_protect_title)) {
                Body(str(R.string.about_protect_proxy))
                Body(str(R.string.about_protect_history))
            }

            // 4. Tips to keep searches more private.
            Section(str(R.string.about_tips_title)) {
                Bullets(
                    str(R.string.about_tips_history),
                    str(R.string.about_tips_pii),
                    str(R.string.about_tips_byokey),
                    str(R.string.about_tips_vpn),
                    str(R.string.about_tips_clear),
                )
            }

            // 5. Caveat (prominent).
            CaveatCard {
                Text(
                    str(R.string.about_caveat_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    str(R.string.about_caveat_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            // 6. Footer: version, license, repo link.
            Footer(version = version, onOpenRepo = onOpenRepo, onReportBug = onReportBug)
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Bullets(vararg items: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium)
                Text(item, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CaveatCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun Footer(
    version: String,
    onOpenRepo: () -> Unit,
    onReportBug: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            str(R.string.about_version, version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(AboutTestTags.VERSION),
        )
        Text(
            str(R.string.about_license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            str(R.string.about_copyright),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            str(R.string.about_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            str(R.string.about_trademarks),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onOpenRepo,
            modifier = Modifier.testTag(AboutTestTags.REPO_BUTTON),
        ) {
            Text(str(R.string.about_repo_button))
        }
        OutlinedButton(
            onClick = onReportBug,
            modifier = Modifier.testTag(AboutTestTags.BUG_BUTTON),
        ) {
            Text(str(R.string.about_bug_button))
        }
    }
}

@Composable
private fun str(id: Int): String = LocalContext.current.getString(id)

@Composable
private fun str(
    id: Int,
    vararg formatArgs: Any,
): String = LocalContext.current.getString(id, *formatArgs)
