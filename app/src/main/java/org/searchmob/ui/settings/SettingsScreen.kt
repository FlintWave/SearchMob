package org.searchmob.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.searchmob.R
import org.searchmob.server.LocalServerState
import org.searchmob.server.networkReachableUrl
import org.searchmob.service.BatteryOptimization
import org.searchmob.service.OemGuidance
import org.searchmob.ui.ApiKeyEngines
import org.searchmob.ui.theme.ThemeMode

object SettingsTestTags {
    const val THEME_LIGHT = "settings_theme_light"
    const val THEME_DARK = "settings_theme_dark"
    const val THEME_SYSTEM = "settings_theme_system"
    const val DYNAMIC_COLOR = "settings_dynamic_color"
    const val HISTORY_SWITCH = "settings_history_switch"
    const val NETWORK_SWITCH = "settings_network_switch"
    const val NETWORK_ADDRESS_COPY = "settings_network_address_copy"
    const val BROWSER_SETUP = "settings_browser_setup"
    const val ABOUT = "settings_about"

    fun engineSwitch(id: String) = "settings_engine_$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenBrowserSetup: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs by viewModel.preferencesState.collectAsStateWithLifecycle()
    val keyPresence by viewModel.apiKeyPresence.collectAsStateWithLifecycle()
    val showNetworkWarning by viewModel.showNetworkWarning.collectAsStateWithLifecycle()
    val port by LocalServerState.port.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = str(R.string.setup_copied)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(str(R.string.settings_title)) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // --- Theme ---
            SectionTitle(str(R.string.settings_section_theme))
            ThemeOption(str(R.string.theme_light), prefs.themeMode == ThemeMode.LIGHT, SettingsTestTags.THEME_LIGHT) {
                viewModel.setThemeMode(ThemeMode.LIGHT)
            }
            ThemeOption(str(R.string.theme_dark), prefs.themeMode == ThemeMode.DARK, SettingsTestTags.THEME_DARK) {
                viewModel.setThemeMode(ThemeMode.DARK)
            }
            ThemeOption(
                str(R.string.theme_system),
                prefs.themeMode == ThemeMode.SYSTEM,
                SettingsTestTags.THEME_SYSTEM,
            ) {
                viewModel.setThemeMode(ThemeMode.SYSTEM)
            }
            ToggleRow(
                label = str(R.string.settings_dynamic_color),
                supporting =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        null
                    } else {
                        str(R.string.settings_dynamic_color_unsupported)
                    },
                checked = prefs.dynamicColor,
                tag = SettingsTestTags.DYNAMIC_COLOR,
                onCheckedChange = viewModel::setDynamicColor,
            )

            HorizontalDivider()

            // --- Engines ---
            SectionTitle(str(R.string.settings_section_engines))
            viewModel.engines.forEach { engine ->
                ToggleRow(
                    label = engine.displayName,
                    supporting = if (engine.requiresApiKey) str(R.string.settings_engine_needs_key) else null,
                    checked = prefs.isEngineEnabled(engine.id),
                    tag = SettingsTestTags.engineSwitch(engine.id),
                    onCheckedChange = { viewModel.setEngineEnabled(engine.id, it) },
                )
            }

            HorizontalDivider()

            // --- BYO API keys ---
            SectionTitle(str(R.string.settings_section_keys))
            ApiKeyRow(
                label = str(R.string.settings_key_brave),
                hasKey = ApiKeyEngines.BRAVE in keyPresence,
                onSave = { viewModel.setApiKey(ApiKeyEngines.BRAVE, it) },
                onClear = { viewModel.clearApiKey(ApiKeyEngines.BRAVE) },
            )
            ApiKeyRow(
                label = str(R.string.settings_key_mojeek),
                hasKey = ApiKeyEngines.MOJEEK in keyPresence,
                onSave = { viewModel.setApiKey(ApiKeyEngines.MOJEEK, it) },
                onClear = { viewModel.clearApiKey(ApiKeyEngines.MOJEEK) },
            )

            HorizontalDivider()

            // --- History ---
            SectionTitle(str(R.string.settings_section_history))
            ToggleRow(
                label = str(R.string.settings_history),
                supporting = str(R.string.settings_history_supporting),
                checked = prefs.historyEnabled,
                tag = SettingsTestTags.HISTORY_SWITCH,
                onCheckedChange = viewModel::setHistoryEnabled,
            )
            OutlinedButton(onClick = viewModel::clearHistory) {
                Text(str(R.string.settings_history_clear))
            }
            ZeroKnowledgeRow()

            HorizontalDivider()

            // --- Network mode (opt-in LAN/Tailscale exposure) ---
            SectionTitle(str(R.string.settings_section_network))
            ToggleRow(
                label = str(R.string.settings_network_access),
                supporting = str(R.string.settings_network_access_supporting),
                checked = prefs.networkAccessEnabled,
                tag = SettingsTestTags.NETWORK_SWITCH,
                onCheckedChange = viewModel::onNetworkAccessToggle,
            )
            if (prefs.networkAccessEnabled) {
                NetworkAddressCard(
                    url = port?.let { networkReachableUrl(it) },
                    onCopy = { value ->
                        clipboard.setText(AnnotatedString(value))
                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                    },
                )
            }
            if (showNetworkWarning) {
                AlertDialog(
                    onDismissRequest = viewModel::cancelNetworkAccess,
                    title = { Text(str(R.string.settings_network_warning_title)) },
                    text = { Text(str(R.string.settings_network_warning)) },
                    confirmButton = {
                        TextButton(onClick = viewModel::confirmNetworkAccess) {
                            Text(str(R.string.settings_network_warning_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::cancelNetworkAccess) { Text(str(R.string.cancel)) }
                    },
                )
            }

            HorizontalDivider()

            // --- Browser setup ---
            SectionTitle(str(R.string.settings_section_browser))
            OutlinedButton(
                onClick = onOpenBrowserSetup,
                modifier = Modifier.testTag(SettingsTestTags.BROWSER_SETUP),
            ) {
                Text(str(R.string.settings_browser_setup))
            }

            HorizontalDivider()

            // --- Device setup guidance (from add-foreground-service) ---
            SectionTitle(str(R.string.settings_section_device))
            OutlinedButton(onClick = {
                context.startActivity(BatteryOptimization.requestExemptionIntent(context))
            }) {
                Text(str(R.string.battery_allow))
            }
            OutlinedButton(onClick = {
                val guidance = OemGuidance.forManufacturer(Build.MANUFACTURER)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guidance.url)))
            }) {
                Text(str(R.string.oem_guidance_button))
            }

            HorizontalDivider()

            // --- About & privacy ---
            SectionTitle(str(R.string.settings_section_about))
            OutlinedButton(
                onClick = onOpenAbout,
                modifier = Modifier.testTag(SettingsTestTags.ABOUT),
            ) {
                Text(str(R.string.settings_about))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    tag: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onSelect)
                .testTag(tag)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ToggleRow(
    label: String,
    supporting: String?,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun ApiKeyRow(
    label: String,
    hasKey: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var value by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (hasKey) "$label: ${str(R.string.settings_key_set)}" else label,
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(str(R.string.settings_key_hint)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onSave(value)
                value = ""
            }) {
                Text(str(R.string.settings_key_save))
            }
            OutlinedButton(onClick = {
                onClear()
                value = ""
            }) {
                Text(str(R.string.settings_key_clear))
            }
        }
    }
}

@Composable
private fun NetworkAddressCard(
    url: String?,
    onCopy: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                str(R.string.settings_network_address_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (url == null) {
                Text(str(R.string.settings_network_address_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(url, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { onCopy(url) },
                        modifier = Modifier.testTag(SettingsTestTags.NETWORK_ADDRESS_COPY),
                    ) {
                        Text(str(R.string.setup_copy))
                    }
                }
            }
        }
    }
}

@Composable
private fun ZeroKnowledgeRow() {
    var showDialog by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showDialog = true }) {
        Text(str(R.string.settings_zk_setup))
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(str(R.string.settings_zk_title)) },
            text = { Text(str(R.string.settings_zk_warning)) },
            confirmButton = {
                // Hand-off point: add-encrypted-storage owns the passphrase-capture flow.
                // TODO(storage phase): launch the zero-knowledge passphrase setup here.
                TextButton(onClick = { showDialog = false }) { Text(str(R.string.settings_zk_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(str(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun str(id: Int): String = LocalContext.current.getString(id)
