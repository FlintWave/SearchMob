package org.searchmob.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.searchmob.R
import org.searchmob.engine.rank.Lens
import org.searchmob.engine.rank.RankingRules
import org.searchmob.server.LocalServerState
import org.searchmob.server.networkReachableUrl
import org.searchmob.service.BatteryOptimization
import org.searchmob.service.OemGuidance
import org.searchmob.ui.ApiKeyEngines
import org.searchmob.ui.theme.APP_THEMES_BY_ID
import org.searchmob.ui.theme.DARK_THEME_IDS
import org.searchmob.ui.theme.FONT_POINT_STEP
import org.searchmob.ui.theme.LIGHT_THEME_IDS
import org.searchmob.ui.theme.MAX_FONT_POINT_SIZE
import org.searchmob.ui.theme.MIN_FONT_POINT_SIZE
import org.searchmob.ui.theme.ThemeMode

object SettingsTestTags {
    const val THEME_LIGHT = "settings_theme_light"
    const val THEME_DARK = "settings_theme_dark"
    const val THEME_SYSTEM = "settings_theme_system"
    const val DYNAMIC_COLOR = "settings_dynamic_color"
    const val LIGHT_THEME_SELECT = "settings_light_theme_select"
    const val DARK_THEME_SELECT = "settings_dark_theme_select"
    const val FONT_DECREASE = "settings_font_decrease"
    const val FONT_INCREASE = "settings_font_increase"
    const val FONT_SIZE_VALUE = "settings_font_size_value"
    const val HISTORY_SWITCH = "settings_history_switch"
    const val SUGGESTIONS_UPSTREAM_SWITCH = "settings_suggestions_upstream_switch"
    const val SUMMARY_SWITCH = "settings_summary_switch"
    const val UPDATE_CHECK_SWITCH = "settings_update_check_switch"
    const val AI_SLOP_CHIPS = "settings_ai_slop_chips"
    const val PERSONALIZATION_SWITCH = "settings_personalization_switch"
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
    onOpenHistory: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val prefs by viewModel.preferencesState.collectAsStateWithLifecycle()
    val keyPresence by viewModel.apiKeyPresence.collectAsStateWithLifecycle()
    val ranking by viewModel.rankingRules.collectAsStateWithLifecycle()
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

            // Named-theme slots (the two-slot model): which theme fills the light slot and which fills
            // the dark slot. The Light/Dark/Follow-system control above swaps between them. When
            // Material You is on it overrides these, so a hint explains that rather than disabling them.
            val dynamicOverrides =
                prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ThemeSlotSelector(
                label = str(R.string.settings_theme_light_slot),
                selectedId = prefs.lightThemeId,
                options = LIGHT_THEME_IDS,
                tag = SettingsTestTags.LIGHT_THEME_SELECT,
                onSelect = viewModel::setLightTheme,
            )
            ThemeSlotSelector(
                label = str(R.string.settings_theme_dark_slot),
                selectedId = prefs.darkThemeId,
                options = DARK_THEME_IDS,
                tag = SettingsTestTags.DARK_THEME_SELECT,
                onSelect = viewModel::setDarkTheme,
            )
            if (dynamicOverrides) {
                Text(
                    str(R.string.settings_theme_dynamic_overrides),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FontSizeStepper(
                pointSize = prefs.fontPointSize,
                onChange = viewModel::setFontPointSize,
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
            ApiKeyRow(
                label = str(R.string.settings_key_kagi),
                hasKey = ApiKeyEngines.KAGI in keyPresence,
                onSave = { viewModel.setApiKey(ApiKeyEngines.KAGI, it) },
                onClear = { viewModel.clearApiKey(ApiKeyEngines.KAGI) },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenHistory) {
                    Text(str(R.string.settings_history_view))
                }
                OutlinedButton(onClick = viewModel::clearHistory) {
                    Text(str(R.string.settings_history_clear))
                }
            }
            ZeroKnowledgeRow()

            HorizontalDivider()

            // --- Result ranking (personalization) ---
            ResultRankingSection(rules = ranking, aiSlopMode = prefs.aiSlopMode, viewModel = viewModel)

            HorizontalDivider()

            // --- Suggestions (opt-in upstream autocomplete) ---
            SectionTitle(str(R.string.settings_section_suggestions))
            ToggleRow(
                label = str(R.string.settings_suggestions_upstream),
                supporting = str(R.string.settings_suggestions_upstream_supporting),
                checked = prefs.upstreamSuggestionsEnabled,
                tag = SettingsTestTags.SUGGESTIONS_UPSTREAM_SWITCH,
                onCheckedChange = viewModel::setUpstreamSuggestionsEnabled,
            )
            ToggleRow(
                label = str(R.string.settings_summary),
                supporting = str(R.string.settings_summary_supporting),
                checked = prefs.summaryEnabled,
                tag = SettingsTestTags.SUMMARY_SWITCH,
                onCheckedChange = viewModel::setSummaryEnabled,
            )

            HorizontalDivider()

            // --- Updates (opt-out launch-time GitHub check) ---
            SectionTitle(str(R.string.settings_section_updates))
            ToggleRow(
                label = str(R.string.settings_update_check),
                supporting = str(R.string.settings_update_check_supporting),
                checked = prefs.updateCheckEnabled,
                tag = SettingsTestTags.UPDATE_CHECK_SWITCH,
                onCheckedChange = viewModel::setUpdateCheckEnabled,
            )

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

/**
 * A theme-slot picker rendered as a dropdown: the label, the current theme's display name, and a
 * menu of the themes valid for this slot (light-mode themes for the light slot, dark for the dark
 * slot). Choosing one persists the slot id; the active appearance updates immediately.
 */
@Composable
private fun ThemeSlotSelector(
    label: String,
    selectedId: String,
    options: List<String>,
    tag: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = APP_THEMES_BY_ID[selectedId]?.displayName ?: selectedId
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().testTag(tag),
            ) {
                Text(selectedName, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { id ->
                    DropdownMenuItem(
                        text = { Text(APP_THEMES_BY_ID[id]?.displayName ?: id) },
                        onClick = {
                            onSelect(id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * The A-/A+ text-size stepper: two square buttons flanking the current point size. Each tap steps by
 * [FONT_POINT_STEP] within the supported bounds; the value is shown in points between the buttons.
 */
@Composable
private fun FontSizeStepper(
    pointSize: Int,
    onChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(str(R.string.settings_font_size), style = MaterialTheme.typography.labelLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val smaller = str(R.string.settings_font_smaller)
            val larger = str(R.string.settings_font_larger)
            OutlinedButton(
                onClick = { onChange(pointSize - FONT_POINT_STEP) },
                enabled = pointSize > MIN_FONT_POINT_SIZE,
                modifier =
                    Modifier
                        .testTag(SettingsTestTags.FONT_DECREASE)
                        .semantics { contentDescription = smaller },
            ) {
                Text("A-")
            }
            Text(
                str(R.string.settings_font_size_value, pointSize),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(SettingsTestTags.FONT_SIZE_VALUE),
            )
            OutlinedButton(
                onClick = { onChange(pointSize + FONT_POINT_STEP) },
                enabled = pointSize < MAX_FONT_POINT_SIZE,
                modifier =
                    Modifier
                        .testTag(SettingsTestTags.FONT_INCREASE)
                        .semantics { contentDescription = larger },
            ) {
                Text("A+")
            }
        }
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
    // The whole row is the click target: a phone-finger tap on the label or supporting text flips the
    // switch, not just a hit on the small Material3 Switch widget. The inner Switch is purely visual
    // (onCheckedChange = null) so we do not register two competing click handlers on the same row.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .padding(vertical = 4.dp)
                .testTag(tag),
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
        Switch(checked = checked, onCheckedChange = null)
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
private fun ResultRankingSection(
    rules: RankingRules,
    aiSlopMode: String,
    viewModel: SettingsViewModel,
) {
    var showAddLens by remember { mutableStateOf(false) }
    var showImportGoggle by remember { mutableStateOf(false) }
    var showImportRules by remember { mutableStateOf(false) }
    var showImportPersonalization by remember { mutableStateOf(false) }
    var exportedJson by remember { mutableStateOf<String?>(null) }
    val personalizationEnabled by viewModel.personalizationEnabled.collectAsStateWithLifecycle()

    SectionTitle(str(R.string.settings_section_ranking))
    Text(str(R.string.settings_ranking_supporting), style = MaterialTheme.typography.bodySmall)

    // Click personalization (opt-in, recommended): learns a bounded boost from the owner's clicks.
    ToggleRow(
        label = str(R.string.settings_personalization),
        supporting = str(R.string.settings_personalization_supporting),
        checked = personalizationEnabled,
        tag = SettingsTestTags.PERSONALIZATION_SWITCH,
        onCheckedChange = viewModel::setPersonalizationEnabled,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { viewModel.exportPersonalizationJson { exportedJson = it } }) {
            Text(str(R.string.settings_personalization_export))
        }
        OutlinedButton(onClick = { showImportPersonalization = true }) {
            Text(str(R.string.settings_personalization_import))
        }
        TextButton(onClick = { viewModel.resetPersonalization() }) {
            Text(str(R.string.settings_personalization_reset))
        }
    }

    // AI-slop / low-quality domain filter (on-device blocklist). Tri-state: downrank (default) / hide /
    // off. Shown first because it affects every result regardless of the per-domain rules below.
    Text(str(R.string.settings_ai_slop), style = MaterialTheme.typography.labelLarge)
    Text(str(R.string.settings_ai_slop_supporting), style = MaterialTheme.typography.bodySmall)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag(SettingsTestTags.AI_SLOP_CHIPS),
    ) {
        listOf(
            "downrank" to R.string.settings_ai_slop_downrank,
            "hide" to R.string.settings_ai_slop_hide,
            "off" to R.string.settings_ai_slop_off,
        ).forEach { (mode, labelRes) ->
            FilterChip(
                selected = aiSlopMode == mode,
                onClick = { viewModel.setAiSlopMode(mode) },
                label = { Text(str(labelRes)) },
            )
        }
    }

    // Active lens (None plus each saved lens, with delete).
    Text(str(R.string.settings_lens_active), style = MaterialTheme.typography.labelLarge)
    LensRadio(label = str(R.string.settings_lens_none), selected = rules.activeLens == null) {
        viewModel.selectLens(null)
    }
    rules.lenses.forEach { lens ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LensRadio(
                label = lens.name,
                selected = rules.activeLens == lens.name,
                modifier = Modifier.weight(1f),
            ) { viewModel.selectLens(lens.name) }
            TextButton(onClick = { viewModel.deleteLens(lens.name) }) {
                Text(str(R.string.settings_lens_delete))
            }
        }
    }
    OutlinedButton(onClick = { showAddLens = true }) { Text(str(R.string.settings_lens_add)) }

    // Per-domain rules (created from the inline result menu; managed/cleared here).
    if (rules.domainRules.isEmpty()) {
        Text(str(R.string.settings_ranking_domains_empty), style = MaterialTheme.typography.bodySmall)
    } else {
        rules.domainRules.entries.sortedBy { it.key }.forEach { (domain, rule) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "$domain — ${rule.name.lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.clearDomainRule(domain) }) {
                    Text(str(R.string.rank_normal))
                }
            }
        }
    }

    // Goggles.
    Text(
        text = str(R.string.settings_goggles) + ": " + rules.goggles.size,
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showImportGoggle = true }) { Text(str(R.string.settings_goggles_import)) }
        if (rules.goggles.isNotEmpty()) {
            TextButton(onClick = viewModel::clearGoggles) { Text(str(R.string.settings_goggles_clear)) }
        }
    }

    // Export / import all rules as JSON.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { viewModel.exportRulesJson { exportedJson = it } }) {
            Text(str(R.string.settings_rules_export))
        }
        OutlinedButton(onClick = { showImportRules = true }) { Text(str(R.string.settings_rules_import)) }
    }

    if (showAddLens) {
        AddLensDialog(
            onDismiss = { showAddLens = false },
            onSave = {
                viewModel.saveLens(it)
                showAddLens = false
            },
        )
    }
    if (showImportGoggle) {
        PasteTextDialog(
            title = str(R.string.settings_goggle_paste_title),
            onDismiss = { showImportGoggle = false },
            onConfirm = {
                viewModel.importGoggles(it)
                showImportGoggle = false
            },
        )
    }
    if (showImportRules) {
        PasteTextDialog(
            title = str(R.string.settings_rules_paste_title),
            onDismiss = { showImportRules = false },
            onConfirm = {
                viewModel.importRulesJson(it)
                showImportRules = false
            },
        )
    }
    if (showImportPersonalization) {
        PasteTextDialog(
            title = str(R.string.settings_personalization_paste_title),
            onDismiss = { showImportPersonalization = false },
            onConfirm = {
                viewModel.importPersonalizationJson(it)
                showImportPersonalization = false
            },
        )
    }
    exportedJson?.let { json ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { exportedJson = null },
            title = { Text(str(R.string.settings_rules_exported_title)) },
            text = { Text(json, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(json)) }) {
                    Text(str(R.string.settings_rules_export))
                }
            },
            dismissButton = {
                TextButton(onClick = { exportedJson = null }) { Text(str(R.string.settings_close)) }
            },
        )
    }
}

/** A radio row used to pick the active lens. */
@Composable
private fun LensRadio(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Dialog to create a lens from comma-separated domain/keyword fields. */
@Composable
private fun AddLensDialog(
    onDismiss: () -> Unit,
    onSave: (Lens) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var includeDomains by remember { mutableStateOf("") }
    var excludeDomains by remember { mutableStateOf("") }
    var includeKeywords by remember { mutableStateOf("") }
    var excludeKeywords by remember { mutableStateOf("") }

    fun split(s: String) = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.settings_lens_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text(str(R.string.settings_lens_name)) },
                    singleLine = true,
                )
                OutlinedTextField(includeDomains, {
                    includeDomains = it
                }, label = { Text(str(R.string.settings_lens_include_domains)) })
                OutlinedTextField(excludeDomains, {
                    excludeDomains = it
                }, label = { Text(str(R.string.settings_lens_exclude_domains)) })
                OutlinedTextField(includeKeywords, {
                    includeKeywords = it
                }, label = { Text(str(R.string.settings_lens_include_keywords)) })
                OutlinedTextField(excludeKeywords, {
                    excludeKeywords = it
                }, label = { Text(str(R.string.settings_lens_exclude_keywords)) })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        Lens(
                            name = name.trim(),
                            includeDomains = split(includeDomains),
                            excludeDomains = split(excludeDomains),
                            includeKeywords = split(includeKeywords),
                            excludeKeywords = split(excludeKeywords),
                        ),
                    )
                },
            ) { Text(str(R.string.settings_key_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )
}

/** Dialog with a single multi-line field for pasting goggle rules or exported JSON. */
@Composable
private fun PasteTextDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) {
                Text(str(R.string.settings_rules_import))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )
}

@Composable
private fun str(id: Int): String = LocalContext.current.getString(id)

@Composable
private fun str(
    id: Int,
    vararg formatArgs: Any,
): String = LocalContext.current.getString(id, *formatArgs)
