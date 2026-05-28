package org.searchmob.ui.setup

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.searchmob.R
import org.searchmob.server.LocalServerState
import org.searchmob.service.ServiceController

object SetupTestTags {
    const val NOT_RUNNING = "setup_not_running"
    const val VISIT_COPY = "setup_visit_copy"
    const val TEMPLATE_COPY = "setup_template_copy"
    const val OPEN_BROWSER = "setup_open_browser"
}

/**
 * Stateful navigation entry point: reads the live bound port from [LocalServerState] and renders the
 * standalone browser-setup guide screen (with its own top bar + snackbar), or a "service not running"
 * state when the port is null.
 */
@Composable
fun BrowserSetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val port by LocalServerState.port.collectAsStateWithLifecycle()
    BrowserSetupScreenContent(
        port = port,
        onBack = onBack,
        onOpenUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        onStartService = { ServiceController.start(context) },
        modifier = modifier,
    )
}

/**
 * Stateless standalone screen: a [Scaffold] with a top bar and snackbar host wrapping the guide body.
 * Kept free of platform singletons so it can be exercised in Compose UI tests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserSetupScreenContent(
    port: Int?,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onStartService: () -> Unit,
    modifier: Modifier = Modifier,
    clipboard: ClipboardManager = LocalClipboardManager.current,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = str(R.string.setup_copied)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(str(R.string.setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrowserSetupBody(
                port = port,
                onOpenUrl = onOpenUrl,
                onStartService = onStartService,
                onCopy = { value ->
                    clipboard.setText(AnnotatedString(value))
                    scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                },
            )
        }
    }
}

/**
 * The guide body: live loopback URLs (or a not-running prompt), one-tap copy controls, per-browser
 * instructions, and an open-in-browser action. Renders directly into the enclosing [ColumnScope] so it
 * can be embedded either in the standalone screen's scaffold or inside the onboarding wizard's pager
 * page without nesting scaffolds/scrolls. [onCopy] performs the clipboard write + confirmation.
 */
@Composable
fun ColumnScope.BrowserSetupBody(
    port: Int?,
    onOpenUrl: (String) -> Unit,
    onStartService: () -> Unit,
    onCopy: (String) -> Unit,
) {
    if (port == null) {
        NotRunningCard(onStartService = onStartService)
        return
    }

    val urls = setupUrls(port)
    Text(str(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium)

    UrlCard(
        label = str(R.string.setup_visit_label),
        url = urls.visitUrl,
        copyTag = SetupTestTags.VISIT_COPY,
        onCopy = { onCopy(urls.visitUrl) },
    )
    UrlCard(
        label = str(R.string.setup_template_label),
        url = urls.searchTemplateUrl,
        copyTag = SetupTestTags.TEMPLATE_COPY,
        onCopy = { onCopy(urls.searchTemplateUrl) },
    )

    Button(
        onClick = { onOpenUrl(urls.visitUrl) },
        modifier = Modifier.fillMaxWidth().testTag(SetupTestTags.OPEN_BROWSER),
    ) {
        Text(str(R.string.setup_open_browser))
    }

    InstructionCard(
        title = str(R.string.setup_generic_title),
        steps =
            listOf(
                str(R.string.setup_generic_step1),
                str(R.string.setup_generic_step2),
                str(R.string.setup_generic_step3),
            ),
    )
    InstructionCard(
        title = str(R.string.setup_firefox_title),
        steps =
            listOf(
                str(R.string.setup_firefox_step1),
                str(R.string.setup_firefox_step2),
                str(R.string.setup_firefox_step3),
            ),
    )
    InstructionCard(
        title = str(R.string.setup_chrome_title),
        steps =
            listOf(
                str(R.string.setup_chrome_step1),
                str(R.string.setup_chrome_step2),
                str(R.string.setup_chrome_step3),
            ),
    )
    InstructionCard(
        title = str(R.string.setup_manual_title),
        steps =
            listOf(
                str(R.string.setup_manual_step1),
                str(R.string.setup_manual_step2),
                str(R.string.setup_manual_step3),
            ),
    )
    InstructionCard(
        title = str(R.string.setup_suggestions_title),
        steps =
            listOf(
                str(R.string.setup_suggestions_step1),
                str(R.string.setup_suggestions_step2),
                str(R.string.setup_suggestions_step3),
                str(R.string.setup_suggestions_step4),
            ),
    )
}

@Composable
private fun NotRunningCard(onStartService: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag(SetupTestTags.NOT_RUNNING)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(str(R.string.setup_not_running_title), style = MaterialTheme.typography.titleMedium)
            Text(str(R.string.setup_not_running_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onStartService) {
                Text(str(R.string.setup_start_service))
            }
        }
    }
}

@Composable
private fun UrlCard(
    label: String,
    url: String,
    copyTag: String,
    onCopy: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(url, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onCopy, modifier = Modifier.testTag(copyTag)) {
                Text(str(R.string.setup_copy))
            }
        }
    }
}

@Composable
private fun InstructionCard(
    title: String,
    steps: List<String>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            steps.forEachIndexed { index, step ->
                Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun str(id: Int): String = LocalContext.current.getString(id)
