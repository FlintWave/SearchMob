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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.searchmob.R
import org.searchmob.engine.EngineRegistry
import org.searchmob.engine.MetaSearchResultProvider
import org.searchmob.engine.adapters.BraveApiAdapter
import org.searchmob.engine.adapters.DuckDuckGoAdapter
import org.searchmob.engine.adapters.MarginaliaAdapter
import org.searchmob.engine.adapters.MojeekAdapter
import org.searchmob.engine.adapters.MojeekApiAdapter
import org.searchmob.engine.adapters.MwmblAdapter
import org.searchmob.engine.adapters.WikipediaAdapter
import org.searchmob.server.LocalServerState
import org.searchmob.server.SearchServer
import org.searchmob.server.WakeLockRequestGuard
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
    // Loopback HTTP server backed by the metasearch engine; each request acquires a short wake-lock.
    private val searchServer by lazy {
        val registry =
            EngineRegistry(
                listOf(
                    // Free by default:
                    DuckDuckGoAdapter(),
                    MojeekAdapter(),
                    MarginaliaAdapter(),
                    MwmblAdapter(),
                    WikipediaAdapter(),
                    // Bring-your-own-key (inactive until a key is configured):
                    BraveApiAdapter(),
                    MojeekApiAdapter(),
                ),
            )
        SearchServer(
            provider = MetaSearchResultProvider(registry),
            guard = WakeLockRequestGuard(AndroidWorkLock(applicationContext)),
        )
    }

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
        // Covers both a normal start and a START_STICKY recreation with a null intent.
        promoteToForeground()
        val port = searchServer.start(networkAccessEnabled = networkAccessEnabled)
        LocalServerState.setPort(port)
        SearchMobServiceState.markRunning()
        observeNetworkAccessPreference()
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
        searchServer.stop()
        LocalServerState.setPort(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        SearchMobServiceState.markStopped()
        stopSelf()
    }

    override fun onDestroy() {
        // If the system tears us down, release the socket and reflect that for observers. A
        // START_STICKY recreation will transition back to running via onStartCommand.
        serviceScope.cancel()
        observingPreference = false
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
