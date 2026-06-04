package org.searchmob

import android.Manifest
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.searchmob.engine.http.HttpClientFactory
import org.searchmob.server.LocalServerState
import org.searchmob.service.ServiceController
import org.searchmob.ui.AppDependencies
import org.searchmob.ui.Routes
import org.searchmob.ui.SearchMobNavHost
import org.searchmob.ui.SearchMobViewModelFactory
import org.searchmob.ui.onboarding.ONBOARDING_VERSION
import org.searchmob.ui.onboarding.OnboardingWizard
import org.searchmob.ui.prefs.DataStorePreferencesStore
import org.searchmob.ui.prefs.UserPreferences
import org.searchmob.ui.theme.SearchMobTheme
import org.searchmob.update.RELEASES_PAGE_URL
import org.searchmob.update.UpdateCheckCoordinator
import org.searchmob.update.UpdateChecker
import org.searchmob.update.UpdateFlow
import org.searchmob.update.UpdateInstaller
import org.searchmob.update.UpdateNotifier
import org.searchmob.update.VersionTag
import org.searchmob.widget.SearchDeepLink

class MainActivity : ComponentActivity() {
    // App-scoped dependency graph. Non-secret UI prefs use the plaintext DataStore; the history store
    // and the encrypted API-key prefs come from the process-wide StorageProvider so the UI and the
    // foreground service share one encrypted store.
    private val deps: AppDependencies by lazy {
        val app = application as SearchMobApplication
        AppDependencies(
            preferencesStore = DataStorePreferencesStore(applicationContext),
            historyStore = app.storage.history,
            engineConfig = app.storage.engineConfig,
            spellCorrector = app.spellCorrector,
            rankingPreferences = app.rankingPreferences,
            personalizationPreferences = app.personalizationPreferences,
            slopDomains = { app.aiSlopBlocklistLoader.current() },
        )
    }

    // Set when the launching/relaunching intent asks to open Search (home-screen widget deep link).
    // Compose observes it and routes the nav to the Search route. A nullable token rather than a plain
    // Boolean so each fresh request (incl. onNewIntent relaunches) re-triggers navigation.
    private var openSearchToken by mutableStateOf<Long?>(null)

    // True while a one-click update download/verify is in flight, so the banner shows progress and the
    // Update button is disabled. The banner itself is driven by the persisted pending-update prefs.
    private var updateInProgress by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the always-on service when the app is opened (idempotent if already running).
        ServiceController.start(this)

        // Load persisted (encrypted) BYO API keys into the engine registry's runtime cache.
        lifecycleScope.launch { withContext(Dispatchers.IO) { deps.hydrateApiKeys() } }

        if (SearchDeepLink.shouldOpenSearch(intent)) openSearchToken = System.nanoTime()

        // Launch-time update check: lifecycle-scoped, off the main thread, and fully fail-soft. It only
        // does anything when the preference is on and the once-a-day throttle is due, so it is safe to
        // run on every launch and never blocks startup or the search UI. A found update only sets state;
        // the browser is never opened without an explicit tap.
        lifecycleScope.launch {
            val update =
                withContext(Dispatchers.IO) {
                    val checker =
                        UpdateChecker(HttpClientFactory.create(connectTimeoutMs = 4_000, readTimeoutMs = 4_000))
                    UpdateCheckCoordinator(
                        preferences = deps.preferencesRepository,
                        checker = checker,
                        currentVersionCode = currentVersionCode(),
                    ).checkIfDue()
                }
            // A found update persists to prefs (driving the banner reactively); also post a system
            // notification so the user is told while the app is open. The notification opens the app.
            if (update != null) UpdateNotifier.notify(this@MainActivity, update.latestVersionName)
        }

        setContent {
            // Ask for notification permission on Android 13+ so the service notification is visible.
            val notifPermission =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { /* result handled by the OS; the service runs regardless */ }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                LaunchedEffect(Unit) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            SearchMobApp(
                deps = deps,
                openSearchToken = openSearchToken,
                currentVersionCode = currentVersionCode(),
                updateInProgress = updateInProgress,
                onStartUpdate = ::startUpdate,
            )
        }
    }

    /**
     * One-click update: re-fetch the latest release (for fresh asset URLs + checksum), download and
     * verify the APK, then hand it to the system installer. Falls back to opening the release page
     * when there is no usable asset (or on failure). Runs the network work off the main thread; the
     * banner shows progress via [updateInProgress].
     */
    private fun startUpdate() {
        if (updateInProgress) return
        updateInProgress = true
        lifecycleScope.launch {
            val fallback = deps.preferencesRepository.pendingUpdateUrl().ifBlank { RELEASES_PAGE_URL }
            val result =
                withContext(Dispatchers.IO) {
                    val client = HttpClientFactory.create(connectTimeoutMs = 10_000, readTimeoutMs = 60_000)
                    UpdateFlow.prepare(client, cacheDir, fallback)
                }
            updateInProgress = false
            when (result) {
                is UpdateFlow.Result.Installable -> {
                    UpdateNotifier.cancel(this@MainActivity)
                    runCatching { UpdateInstaller.installApk(this@MainActivity, result.file) }
                        .onFailure { openUrl(fallback) }
                }
                is UpdateFlow.Result.OpenPage -> openUrl(result.url)
                is UpdateFlow.Result.Failed -> {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.update_download_failed, result.message),
                        Toast.LENGTH_LONG,
                    ).show()
                    openUrl(result.url)
                }
            }
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    /**
     * The running build's version code, used to decide whether the latest release is newer. Reads
     * [PackageInfo.longVersionCode] on API 28+ and the deprecated `versionCode` below it; returns 0 on
     * any failure so a read error simply means "never newer" rather than a crash.
     */
    private fun currentVersionCode(): Int =
        runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        }.getOrDefault(0)

    // When the activity is already running (launchMode reuse via SINGLE_TOP), a fresh widget tap
    // arrives here; re-arm the token so Compose navigates to Search again.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (SearchDeepLink.shouldOpenSearch(intent)) openSearchToken = System.nanoTime()
    }
}

/**
 * Root composable: gates the first-run wizard on the persisted completion flag, observes theme
 * preferences (apply-immediately), hosts navigation, and routes the widget deep link to Search.
 * Kept separate from [MainActivity] so it can be exercised in Compose UI tests.
 */
@Composable
fun SearchMobApp(
    deps: AppDependencies,
    openSearchToken: Long? = null,
    currentVersionCode: Int = 0,
    updateInProgress: Boolean = false,
    onStartUpdate: () -> Unit = {},
) {
    val prefs: UserPreferences by deps.preferencesRepository.preferences
        .collectAsStateWithLifecycle(initialValue = UserPreferences())
    val factory = remember(deps) { SearchMobViewModelFactory(deps) }
    val navController = rememberNavController()

    // Gate the first-run wizard on the persisted completion flag and the onboarding revision: the
    // wizard shows on first run and once more after an update that adds a step (version behind the
    // app's current one). `null` while either value loads so we don't flash the wizard.
    val onboardingCompleted: Boolean? by deps.preferencesRepository.onboardingCompleted
        .collectAsStateWithLifecycle(initialValue = null)
    val onboardingVersion: Int? by deps.preferencesRepository.onboardingVersion
        .collectAsStateWithLifecycle(initialValue = null)
    val personalizationEnabled: Boolean by deps.preferencesRepository.personalizationEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    // The "update available" banner is driven by the persisted pending-update record (written by the
    // launch-time check), so it survives a restart and clears itself once the user is current.
    val pendingVersion: String by deps.preferencesRepository.pendingUpdateVersion
        .collectAsStateWithLifecycle(initialValue = "")
    val pendingUrl: String by deps.preferencesRepository.pendingUpdateUrl
        .collectAsStateWithLifecycle(initialValue = "")
    var updateDismissed by remember { mutableStateOf(false) }
    val showUpdateBanner =
        !updateDismissed &&
            pendingVersion.isNotBlank() &&
            pendingUrl.isNotBlank() &&
            (VersionTag.toVersionCode(pendingVersion) ?: 0) > currentVersionCode
    val showOnboarding: Boolean? =
        if (onboardingCompleted == null || onboardingVersion == null) {
            null
        } else {
            onboardingCompleted == false || (onboardingVersion ?: 0) < ONBOARDING_VERSION
        }
    val port: Int? by LocalServerState.port.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val completeOnboarding = {
        scope.launch {
            deps.preferencesRepository.setOnboardingCompleted(true)
            deps.preferencesRepository.setOnboardingVersion(ONBOARDING_VERSION)
        }
        Unit
    }

    // Widget deep link: each new token navigates to Search, but only once onboarding is done and the
    // nav host is actually composed (navigating before then has no host to receive it).
    LaunchedEffect(openSearchToken, showOnboarding) {
        if (openSearchToken != null && showOnboarding == false) {
            navController.navigate(Routes.SEARCH) {
                launchSingleTop = true
            }
        }
    }

    SearchMobTheme(
        themeMode = prefs.themeMode,
        dynamicColor = prefs.dynamicColor,
        lightThemeId = prefs.lightThemeId,
        darkThemeId = prefs.darkThemeId,
        fontPointSize = prefs.fontPointSize,
    ) {
        // The update banner is pinned above the content (only past onboarding so it never competes
        // with the wizard); the rest of the app renders below it.
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showUpdateBanner && showOnboarding == false) {
                UpdateBanner(
                    version = pendingVersion,
                    inProgress = updateInProgress,
                    onUpdate = onStartUpdate,
                    onDismiss = { updateDismissed = true },
                )
            }
            when (showOnboarding) {
                null -> Unit // loading; render nothing for the first frame
                true ->
                    OnboardingWizard(
                        port = port,
                        onComplete = { completeOnboarding() },
                        onOpenUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                        onStartService = { ServiceController.start(context) },
                        // Finishing the wizard hands off to the main app, where the history /
                        // zero-knowledge privacy controls live in Settings.
                        onOpenPrivacySettings = { completeOnboarding() },
                        personalizationEnabled = personalizationEnabled,
                        onSetPersonalization = {
                            scope.launch { deps.preferencesRepository.setPersonalizationEnabled(it) }
                        },
                    )
                else -> SearchMobNavHost(factory = factory, navController = navController)
            }
        }
    }
}

/**
 * The "update available" banner, pinned at the top of the app. Shows the available version with an
 * Update action (the verified one-click download + system install) and a dismiss. While a download is
 * in flight the action is disabled and the label shows progress.
 */
@Composable
private fun UpdateBanner(
    version: String,
    inProgress: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            // Edge-to-edge is on, so pad for the status bar this banner sits beneath.
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val context = LocalContext.current
            Text(
                text =
                    if (inProgress) {
                        context.getString(R.string.update_banner_downloading, version)
                    } else {
                        context.getString(R.string.update_banner_available, version)
                    },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onUpdate, enabled = !inProgress) {
                Text(context.getString(R.string.update_banner_action))
            }
            TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text(context.getString(R.string.update_banner_dismiss))
            }
        }
    }
}
