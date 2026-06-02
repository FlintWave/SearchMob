package org.searchmob.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.searchmob.R
import org.searchmob.SearchMobApplication
import org.searchmob.data.history.HistoryStore
import org.searchmob.engine.EngineConfig
import org.searchmob.engine.EngineRegistry
import org.searchmob.engine.MetaSearchResultProvider
import org.searchmob.engine.adapters.BraveApiAdapter
import org.searchmob.engine.adapters.DuckDuckGoAdapter
import org.searchmob.engine.adapters.KagiApiAdapter
import org.searchmob.engine.adapters.MarginaliaAdapter
import org.searchmob.engine.adapters.MojeekAdapter
import org.searchmob.engine.adapters.MojeekApiAdapter
import org.searchmob.engine.adapters.MwmblAdapter
import org.searchmob.engine.adapters.WikipediaAdapter
import org.searchmob.engine.http.HttpClientFactory
import org.searchmob.engine.summary.WikiSummaryProvider
import org.searchmob.server.LocalServerState
import org.searchmob.server.SearchServer
import org.searchmob.server.WakeLockRequestGuard
import org.searchmob.server.suggest.CompositeSuggestionsProvider
import org.searchmob.server.suggest.HistorySuggestionsProvider
import org.searchmob.server.suggest.UpstreamSuggestionsProvider
import org.searchmob.ui.prefs.DataStorePreferencesStore
import org.searchmob.ui.prefs.PreferencesRepository

/**
 * The always-on backbone: a `specialUse` foreground service.
 *
 * It promotes to the foreground with a persistent, ongoing notification (with a stop action),
 * returns [START_STICKY] so the OS recreates it after a low-memory kill, and publishes lifecycle
 * transitions to [SearchMobServiceState]. It is event-driven and holds NO wake-lock while idle.
 */
class SearchMobService : Service() {
    // Opt-in, local-only search history: the SQLCipher store shared process-wide via the Application,
    // so the browser-facing /suggest source and the in-app recorder read and write the same encrypted
    // DB. Off by default.
    private val historyStore: HistoryStore by lazy { (application as SearchMobApplication).storage.history }

    // Latest opt-in upstream-suggestions value, read by the composite provider's gate at request time.
    @Volatile
    private var upstreamSuggestionsEnabled: Boolean = false

    // Suggestions: always-on local history plus an upstream source contacted ONLY when the opt-in
    // preference is on. The upstream fetch uses a short-timeout privacy-proxy client so typing never
    // hangs; on any failure it returns nothing.
    private val suggestionsProvider by lazy {
        CompositeSuggestionsProvider(
            history = HistorySuggestionsProvider(historyStore),
            upstream =
                UpstreamSuggestionsProvider(
                    httpClient = HttpClientFactory.create(connectTimeoutMs = 2_000, readTimeoutMs = 2_000),
                ),
            upstreamEnabled = { upstreamSuggestionsEnabled },
            // In network mode the server is reachable by other devices, so do not serve the owner's
            // local history as autocomplete to them.
            localEnabled = { !networkAccessEnabled },
        )
    }

    // The metasearch engines for the browser-facing server: free by default, plus the BYO-key APIs
    // (inactive until a key is configured).
    private val engineAdapters =
        listOf(
            DuckDuckGoAdapter(),
            MojeekAdapter(),
            MarginaliaAdapter(),
            MwmblAdapter(),
            WikipediaAdapter(),
            BraveApiAdapter(),
            MojeekApiAdapter(),
            KagiApiAdapter(),
        )

    /**
     * Build the registry for one search from the user's per-engine enabled flags (UI prefs) and the
     * decrypted BYO keys (encrypted store), so a configured Brave/Mojeek/Kagi key activates that engine
     * on the browser path too, not only in the in-app search.
     */
    private suspend fun buildRegistry(): EngineRegistry {
        val app = application as SearchMobApplication
        val userPrefs = preferences.preferences.first()
        val configs =
            engineAdapters.associate { adapter ->
                adapter.id to
                    EngineConfig(
                        engineId = adapter.id,
                        enabled = userPrefs.isEngineEnabled(adapter.id),
                        apiKey = runCatching { app.storage.engineConfig.apiKey(adapter.id) }.getOrNull(),
                    )
            }
        return EngineRegistry(adapters = engineAdapters, configs = configs)
    }

    // Loopback HTTP server backed by the metasearch engine; each request acquires a short wake-lock.
    private val searchServer by lazy {
        SearchServer(
            provider =
                MetaSearchResultProvider(
                    registryProvider = ::buildRegistry,
                    corrector = (application as SearchMobApplication).spellCorrector,
                    rankingRules = { (application as SearchMobApplication).rankingPreferences.load() },
                    // Contextual Wikipedia summary, gated by the user preference; fail-soft.
                    summaryFetcher = { query ->
                        if (preferences.summaryEnabled()) wikiSummaryProvider.fetch(query) else null
                    },
                    // On-device AI-slop filter: the cached blocklist plus the user's mode preference.
                    slopDomains = { (application as SearchMobApplication).aiSlopBlocklistLoader.current() },
                    aiSlopMode = { preferences.aiSlopMode() },
                    // The owner's learned model, when enabled. The server only asks the provider to
                    // apply it for the loopback owner (the `personalize` flag in SearchServer).
                    personalization = {
                        if (preferences.personalizationEnabled()) {
                            (application as SearchMobApplication).personalizationPreferences.load()
                        } else {
                            null
                        }
                    },
                ),
            guard = WakeLockRequestGuard(AndroidWorkLock(applicationContext)),
            suggestionsProvider = suggestionsProvider,
            // Lets the served page's personalization controls read and persist rules (loopback-only).
            rankingPreferences = (application as SearchMobApplication).rankingPreferences,
            // Powers the served Settings page (preference toggles + history view/clear), loopback-only.
            userPreferences = preferences,
            historyStore = (application as SearchMobApplication).storage.history,
            // In network mode, off-loopback clients must present this token; loopback is exempt.
            accessToken = { runBlocking { preferences.networkAccessToken().ifEmpty { null } } },
        )
    }

    // Single contextual-summary provider (its own privacy HTTP client), reused across queries.
    private val wikiSummaryProvider by lazy { WikiSummaryProvider(HttpClientFactory.create()) }

    // Reads the persisted network-mode preference so the bind host tracks the user's choice. Uses the
    // same DataStore the UI writes to, so a toggle in Settings is observed here.
    private val preferences by lazy {
        PreferencesRepository(DataStorePreferencesStore(applicationContext))
    }

    // Service-scoped coroutine scope for observing the network-mode preference; cancelled on teardown.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Latest known network-mode value, so the server is (re)bound on the correct host on (re)start.
    @Volatile
    private var networkAccessEnabled: Boolean = false

    // Set once so the START_STICKY recreations don't register duplicate observers.
    private var observingPreference = false

    // Set once so the START_STICKY recreations don't register duplicate suggestion observers.
    private var observingSuggestions = false

    override fun onCreate() {
        super.onCreate()
        SearchMobServiceState.markStarting()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP) {
            stopAndCleanup()
            return START_NOT_STICKY
        }
        // The service depends on SearchMobApplication for the shared encrypted stores; if it is ever
        // started under a different Application (e.g. instrumented tests use a stock Application), bail
        // out instead of crashing on the cast. We still satisfy the startForeground() contract first,
        // since the start may have come via startForegroundService() (the system otherwise kills the
        // process with ForegroundServiceDidNotStartInTime). This branch is never taken in production.
        if (application !is SearchMobApplication) {
            promoteToForeground()
            stopSelf()
            return START_NOT_STICKY
        }
        // Covers both a normal start and a START_STICKY recreation with a null intent.
        promoteToForeground()
        val port = searchServer.start(networkAccessEnabled = networkAccessEnabled)
        LocalServerState.setPort(port)
        SearchMobServiceState.markRunning()
        observeNetworkAccessPreference()
        observeSuggestionsPreferences()
        return START_STICKY
    }

    /**
     * Watches the persisted network-mode preference and rebinds the embedded server when it changes,
     * switching between loopback ("127.0.0.1") and all-interfaces ("0.0.0.0") binding. The foreground
     * service itself stays up; only the embedded HTTP server is restarted on the new host. Registered
     * once (idempotent across START_STICKY recreations).
     */
    private fun observeNetworkAccessPreference() {
        if (observingPreference) return
        observingPreference = true
        preferences.networkAccessEnabled
            .distinctUntilChanged()
            // Drop the initial emission: the first server start already used the cached value, so the
            // first value only triggers a rebind if it actually differs from what we started with.
            .onEach { enabled ->
                if (enabled == networkAccessEnabled) return@onEach
                networkAccessEnabled = enabled
                if (searchServer.isRunning) {
                    val port = searchServer.restart(networkAccessEnabled = enabled)
                    LocalServerState.setPort(port)
                }
            }.launchIn(serviceScope)
    }

    /**
     * Watches the suggestion-related preferences so the local source and the upstream gate track the
     * user's choices without a relaunch: history enabled/disabled toggles the local source (also
     * purging stored entries when turned off, per the history store's contract), and the opt-in
     * upstream flag flips the composite provider's gate. Registered once (idempotent across recreations).
     */
    private fun observeSuggestionsPreferences() {
        if (observingSuggestions) return
        observingSuggestions = true
        preferences.preferences
            .map { it.historyEnabled to it.upstreamSuggestionsEnabled }
            .distinctUntilChanged()
            .onEach { (history, upstream) ->
                if (historyStore.enabled != history) historyStore.setEnabled(history)
                upstreamSuggestionsEnabled = upstream
            }.launchIn(serviceScope)
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopAndCleanup() {
        serviceScope.cancel()
        observingPreference = false
        observingSuggestions = false
        searchServer.stop()
        LocalServerState.setPort(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        SearchMobServiceState.markStopped()
        stopSelf()
    }

    override fun onDestroy() {
        // Started under a non-production Application (instrumented tests): nothing was initialized, so
        // touching the lazy server (which casts to SearchMobApplication) must be avoided.
        if (application !is SearchMobApplication) {
            super.onDestroy()
            return
        }
        // If the system tears us down, release the socket and reflect that for observers. A
        // START_STICKY recreation will transition back to running via onStartCommand.
        serviceScope.cancel()
        observingPreference = false
        observingSuggestions = false
        searchServer.stop()
        LocalServerState.setPort(null)
        if (SearchMobServiceState.current != ServiceState.Stopped) {
            SearchMobServiceState.markStopped()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.service_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.service_channel_description)
                    setShowBadge(false)
                }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_stat_search)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                getString(R.string.service_notification_stop),
                stopPendingIntent(),
            )
            .build()

    private fun stopPendingIntent(): PendingIntent {
        val stopIntent = Intent(this, SearchMobService::class.java).setAction(ACTION_STOP)
        return PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_STOP = "org.searchmob.service.action.STOP"
        const val CHANNEL_ID = "searchmob_service"
        const val NOTIFICATION_ID = 1001

        fun stopIntent(context: Context): Intent = Intent(context, SearchMobService::class.java).setAction(ACTION_STOP)
    }
}
