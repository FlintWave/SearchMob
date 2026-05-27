package org.searchmob

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.searchmob.server.LocalServerState
import org.searchmob.service.ServiceController
import org.searchmob.ui.AppDependencies
import org.searchmob.ui.Routes
import org.searchmob.ui.SearchMobNavHost
import org.searchmob.ui.SearchMobViewModelFactory
import org.searchmob.ui.onboarding.OnboardingWizard
import org.searchmob.ui.prefs.UserPreferences
import org.searchmob.ui.theme.SearchMobTheme
import org.searchmob.widget.SearchDeepLink

class MainActivity : ComponentActivity() {
    // App-scoped dependency graph. INJECTION POINT: the storage phase swaps the default in-memory
    // PreferencesStore / HistoryStore here for encrypted-DataStore + SQLCipher implementations.
    private val deps: AppDependencies by lazy { AppDependencies() }

    // Set when the launching/relaunching intent asks to open Search (home-screen widget deep link).
    // Compose observes it and routes the nav to the Search route. A nullable token rather than a plain
    // Boolean so each fresh request (incl. onNewIntent relaunches) re-triggers navigation.
    private var openSearchToken by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the always-on service when the app is opened (idempotent if already running).
        ServiceController.start(this)

        if (SearchDeepLink.shouldOpenSearch(intent)) openSearchToken = System.nanoTime()

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

            SearchMobApp(deps, openSearchToken)
        }
    }

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
) {
    val prefs: UserPreferences by deps.preferencesRepository.preferences
        .collectAsStateWithLifecycle(initialValue = UserPreferences())
    val factory = remember(deps) { SearchMobViewModelFactory(deps) }
    val navController = rememberNavController()

    // Gate the first-run wizard on the persisted completion flag. `null` while the flag is loading so
    // we don't flash the wizard before the stored value arrives.
    val onboardingCompleted: Boolean? by deps.preferencesRepository.onboardingCompleted
        .collectAsStateWithLifecycle(initialValue = null)
    val port: Int? by LocalServerState.port.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Widget deep link: each new token navigates to Search — but only once onboarding is complete and
    // the nav host is actually composed (navigating before then has no host to receive it).
    LaunchedEffect(openSearchToken, onboardingCompleted) {
        if (openSearchToken != null && onboardingCompleted == true) {
            navController.navigate(Routes.SEARCH) {
                launchSingleTop = true
            }
        }
    }

    SearchMobTheme(
        themeMode = prefs.themeMode,
        dynamicColor = prefs.dynamicColor,
    ) {
        when (onboardingCompleted) {
            null -> Unit // loading; render nothing for the first frame
            false ->
                OnboardingWizard(
                    port = port,
                    onComplete = { scope.launch { deps.preferencesRepository.setOnboardingCompleted(true) } },
                    onOpenUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                    onStartService = { ServiceController.start(context) },
                    // Finishing the wizard hands off to the main app, where the history /
                    // zero-knowledge privacy controls live in Settings.
                    onOpenPrivacySettings = {
                        scope.launch { deps.preferencesRepository.setOnboardingCompleted(true) }
                    },
                )
            else -> SearchMobNavHost(factory = factory, navController = navController)
        }
    }
}
