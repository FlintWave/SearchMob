package org.searchmob

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.searchmob.service.ServiceController
import org.searchmob.ui.AppDependencies
import org.searchmob.ui.SearchMobNavHost
import org.searchmob.ui.SearchMobViewModelFactory
import org.searchmob.ui.prefs.UserPreferences
import org.searchmob.ui.theme.SearchMobTheme

class MainActivity : ComponentActivity() {
    // App-scoped dependency graph. INJECTION POINT: the storage phase swaps the default in-memory
    // PreferencesStore / HistoryStore here for encrypted-DataStore + SQLCipher implementations.
    private val deps: AppDependencies by lazy { AppDependencies() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the always-on service when the app is opened (idempotent if already running).
        ServiceController.start(this)

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

            SearchMobApp(deps)
        }
    }
}

/**
 * Root composable: observes the persisted theme preferences (apply-immediately) and hosts navigation.
 * Kept separate from [MainActivity] so it can be exercised in Compose UI tests.
 */
@Composable
fun SearchMobApp(deps: AppDependencies) {
    val prefs: UserPreferences by deps.preferencesRepository.preferences
        .collectAsStateWithLifecycle(initialValue = UserPreferences())
    val factory = remember(deps) { SearchMobViewModelFactory(deps) }

    SearchMobTheme(
        themeMode = prefs.themeMode,
        dynamicColor = prefs.dynamicColor,
    ) {
        SearchMobNavHost(factory = factory)
    }
}
