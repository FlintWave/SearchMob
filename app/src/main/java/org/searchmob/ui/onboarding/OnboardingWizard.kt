package org.searchmob.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.searchmob.R
import org.searchmob.service.BatteryOptimization
import org.searchmob.ui.setup.BrowserSetupBody

object OnboardingTestTags {
    const val SKIP = "onboarding_skip"
    const val BACK = "onboarding_back"
    const val NEXT = "onboarding_next"
    const val FINISH = "onboarding_finish"
    const val NOTIFICATIONS_GRANT = "onboarding_notifications_grant"
    const val BATTERY_GRANT = "onboarding_battery_grant"
    const val PRIVACY_SETTINGS = "onboarding_privacy_settings"
    const val PERSONALIZE_SWITCH = "onboarding_personalize_switch"
}

/**
 * Persists [OnboardingProgress] across configuration changes / process death as its page index. The
 * restored index is clamped into the current step range so a stale saved value (e.g. from a build
 * that had more steps) can never trip the wrapper's range check while restoring.
 */
private val onboardingProgressSaver: Saver<OnboardingProgress, Int> =
    Saver(
        save = { it.index },
        restore = { OnboardingProgress(it.coerceIn(0, OnboardingStep.entries.lastIndex)) },
    )

/**
 * First-run wizard host. A skippable pager over [OnboardingStep] with Back/Next/Finish controls.
 *
 * Skip (any page) and Finish (last page) both call [onComplete], which the caller wires to persist the
 * onboarding-completed flag so the wizard never reappears. [port] is the live bound loopback port for
 * the embedded browser-setup guidance (null when the server isn't running). Permission prompts are
 * user-initiated only; the wizard never auto-requests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingWizard(
    port: Int?,
    onComplete: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onStartService: () -> Unit,
    onOpenPrivacySettings: () -> Unit,
    personalizationEnabled: Boolean = false,
    onSetPersonalization: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Saveable so a rotation (or process death) resumes the wizard on the same page rather than
    // snapping back to Welcome; persisted as the page index via [onboardingProgressSaver].
    var progress by rememberSaveable(stateSaver = onboardingProgressSaver) {
        mutableStateOf(OnboardingProgress())
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val copiedMessage = str(R.string.setup_copied)
    val onCopy: (String) -> Unit = { value ->
        clipboard.setText(AnnotatedString(value))
        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
    }

    // Gesture/system back pages backwards through the wizard instead of exiting the app mid-setup.
    // Disabled on the first page so back there keeps its default behavior (leaving the app).
    BackHandler(enabled = !progress.isFirst) { progress = progress.back() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(str(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onComplete, modifier = Modifier.testTag(OnboardingTestTags.SKIP)) {
                        Text(str(R.string.onboarding_skip))
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
                    .padding(innerPadding),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (progress.step) {
                    OnboardingStep.WELCOME -> WelcomePage()
                    OnboardingStep.PERMISSIONS -> PermissionsPage()
                    OnboardingStep.DEFAULT_SEARCH ->
                        DefaultSearchPage(
                            port = port,
                            onOpenUrl = onOpenUrl,
                            onStartService = onStartService,
                            onCopy = onCopy,
                        )
                    OnboardingStep.PRIVACY -> PrivacyPage(onOpenPrivacySettings = onOpenPrivacySettings)
                    OnboardingStep.PERSONALIZE ->
                        PersonalizePage(enabled = personalizationEnabled, onToggle = onSetPersonalization)
                }
            }

            NavRow(
                progress = progress,
                onBack = { progress = progress.back() },
                onNext = { progress = progress.next() },
                onFinish = onComplete,
            )
        }
    }
}

@Composable
private fun NavRow(
    progress: OnboardingProgress,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!progress.isFirst) {
            OutlinedButton(onClick = onBack, modifier = Modifier.testTag(OnboardingTestTags.BACK)) {
                Text(str(R.string.onboarding_back))
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (progress.isLast) {
            Button(onClick = onFinish, modifier = Modifier.testTag(OnboardingTestTags.FINISH)) {
                Text(str(R.string.onboarding_finish))
            }
        } else {
            Button(onClick = onNext, modifier = Modifier.testTag(OnboardingTestTags.NEXT)) {
                Text(str(R.string.onboarding_next))
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Text(str(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineSmall)
    Text(str(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun PermissionsPage() {
    val context = LocalContext.current
    Text(str(R.string.onboarding_permissions_title), style = MaterialTheme.typography.headlineSmall)
    Text(str(R.string.onboarding_permissions_body), style = MaterialTheme.typography.bodyLarge)

    // Notifications (Android 13+). On older versions the permission is implicit, so reflect granted.
    var notificationsGranted by remember { mutableStateOf(hasNotificationsPermission(context)) }
    val notifLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsGranted = granted || hasNotificationsPermission(context)
        }
    PermissionCard(
        label = str(R.string.onboarding_notifications_label),
        granted = notificationsGranted,
        grantText = str(R.string.onboarding_notifications_grant),
        grantedText = str(R.string.onboarding_notifications_granted),
        grantTag = OnboardingTestTags.NOTIFICATIONS_GRANT,
        onGrant = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )

    // Battery-optimization exemption. The grant happens in a SYSTEM dialog with no result callback,
    // so re-read the state on every ON_RESUME — exactly when the user lands back here from that
    // dialog — rather than synchronously after startActivity (which fires before the dialog is even
    // shown, so it always reads the stale pre-grant value).
    var batteryExempt by remember {
        mutableStateOf(runCatching { BatteryOptimization.isExempt(context) }.getOrDefault(false))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    batteryExempt = runCatching { BatteryOptimization.isExempt(context) }.getOrDefault(false)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    PermissionCard(
        label = str(R.string.onboarding_battery_label),
        granted = batteryExempt,
        grantText = str(R.string.onboarding_battery_grant),
        grantedText = str(R.string.onboarding_battery_granted),
        grantTag = OnboardingTestTags.BATTERY_GRANT,
        onGrant = {
            // Guarded: some OEM builds ship without the request-exemption settings activity, and a
            // missing handler must not crash onboarding. The ON_RESUME observer above reflects the
            // outcome once the user returns.
            runCatching { context.startActivity(BatteryOptimization.requestExemptionIntent(context)) }
        },
    )
}

@Composable
private fun PermissionCard(
    label: String,
    granted: Boolean,
    grantText: String,
    grantedText: String,
    grantTag: String,
    onGrant: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (granted) {
                Text(grantedText, color = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onGrant, modifier = Modifier.testTag(grantTag)) {
                    Text(grantText)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DefaultSearchPage(
    port: Int?,
    onOpenUrl: (String) -> Unit,
    onStartService: () -> Unit,
    onCopy: (String) -> Unit,
) {
    Text(str(R.string.onboarding_default_search_title), style = MaterialTheme.typography.headlineSmall)
    Text(str(R.string.onboarding_default_search_body), style = MaterialTheme.typography.bodyLarge)
    // Embed the browser-setup guide body into the wizard's scrolling page (no nested scaffold/scroll).
    BrowserSetupBody(
        port = port,
        onOpenUrl = onOpenUrl,
        onStartService = onStartService,
        onCopy = onCopy,
    )
}

@Composable
private fun PrivacyPage(onOpenPrivacySettings: () -> Unit) {
    Text(str(R.string.onboarding_privacy_title), style = MaterialTheme.typography.headlineSmall)
    Text(str(R.string.onboarding_privacy_body), style = MaterialTheme.typography.bodyLarge)
    OutlinedButton(
        onClick = onOpenPrivacySettings,
        modifier = Modifier.testTag(OnboardingTestTags.PRIVACY_SETTINGS),
    ) {
        Text(str(R.string.onboarding_privacy_open_settings))
    }
}

@Composable
private fun PersonalizePage(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Text(str(R.string.onboarding_personalize_title), style = MaterialTheme.typography.headlineSmall)
    Text(str(R.string.onboarding_personalize_body), style = MaterialTheme.typography.bodyLarge)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(!enabled) }
                .testTag(OnboardingTestTags.PERSONALIZE_SWITCH),
    ) {
        Text(
            text = str(R.string.onboarding_personalize_toggle),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = enabled, onCheckedChange = null)
    }
    Text(str(R.string.onboarding_personalize_note), style = MaterialTheme.typography.bodySmall)
}

private fun hasNotificationsPermission(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

@Composable
private fun str(id: Int): String = stringResource(id)
