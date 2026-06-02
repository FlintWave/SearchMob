package org.searchmob

import android.Manifest
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import org.searchmob.update.UpdateInfo
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

    // Set when the (opt-out, throttled) launch-time update check finds a strictly newer release; the
    // root composable observes it to show an in-app "Update available" prompt. Null means no prompt.
    private var availableUpdate by mutableStateOf<UpdateInfo?>(null)

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
            if (update != null) availableUpdate = update
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
                availableUpdate = availableUpdate,
                onDismissUpdate = { availableUpdate = null },
            )
        }
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
    availableUpdate: UpdateInfo? = null,
    onDismissUpdate: () -> Unit = {},
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
    ) {
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

        // In-app update prompt: shown only once the user is past onboarding so it never competes with
        // the wizard. Opening the releases page requires an explicit tap; "Not now" just dismisses.
        if (availableUpdate != null && showOnboarding == false) {
            UpdateAvailableDialog(
                update = availableUpdate,
                onOpenReleases = {
                    val url = availableUpdate.releaseUrl.takeIf { it.isNotBlank() } ?: RELEASES_PAGE_URL
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    onDismissUpdate()
                },
                onDismiss = onDismissUpdate,
            )
        }
    }
}

/**
 * Material3 dialog announcing a newer release. It only surfaces the version and a link out; SearchMob
 * never auto-downloads or auto-installs, so the user stays in control of whether and when to update.
 */
@Composable
private fun UpdateAvailableDialog(
    update: UpdateInfo,
    onOpenReleases: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LocalContext.current.getString(R.string.update_available_title)) },
        text = {
            Text(LocalContext.current.getString(R.string.update_available_body, update.latestVersionName))
        },
        confirmButton = {
            TextButton(onClick = onOpenReleases) {
                Text(LocalContext.current.getString(R.string.update_open_releases))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(LocalContext.current.getString(R.string.update_not_now))
            }
        },
    )
}
