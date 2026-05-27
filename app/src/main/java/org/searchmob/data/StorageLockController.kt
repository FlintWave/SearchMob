package org.searchmob.data

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.searchmob.data.history.HistoryStore

/**
 * The unlock/lock eviction state machine for zero-knowledge mode. Registered against the process
 * lifecycle (e.g. `ProcessLifecycleOwner.get().lifecycle.addObserver(controller)`), it evicts the
 * in-memory DEK and closes the encrypted history handle on:
 *
 *  - app background (`ON_STOP`),
 *  - an inactivity timeout (armed on foreground, fired off the main thread), and
 *  - explicit [lockNow].
 *
 * Eviction zeroes the DEK bytes (via [Vault.lock], which calls [org.searchmob.data.crypto.DekHolder.zero])
 * and disables the [HistoryStore] handle so locked-state history reads/writes are unavailable until the
 * user re-enters the passphrase. Only applies in [WrapMode.PASSPHRASE]; in the Keystore mode
 * the DEK is recoverable without a prompt, so there is nothing to evict here.
 */
class StorageLockController(
    private val vault: Vault,
    private val history: HistoryStore,
    private val modeProvider: () -> WrapMode?,
    private val inactivityTimeoutMs: Long = DEFAULT_INACTIVITY_TIMEOUT_MS,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : DefaultLifecycleObserver {
    private val timeoutRunnable = Runnable { lockNow() }

    override fun onStart(owner: LifecycleOwner) {
        // Foreground: cancel any pending inactivity lock.
        handler.removeCallbacks(timeoutRunnable)
    }

    override fun onStop(owner: LifecycleOwner) {
        // Background: in zero-knowledge mode, evict immediately.
        if (modeProvider() == WrapMode.PASSPHRASE) {
            lockNow()
        }
    }

    /** Call on each user interaction to (re)arm the inactivity timeout while in the foreground. */
    fun onUserActivity() {
        if (modeProvider() != WrapMode.PASSPHRASE) return
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, inactivityTimeoutMs)
    }

    /** Explicit lock: close the encrypted history handle (data preserved) then zero the DEK. */
    fun lockNow() {
        handler.removeCallbacks(timeoutRunnable)
        // Close the SQLCipher handle first (it needs the DEK), then zero the key. Data is NOT deleted
        // on lock, only on explicit "clear history" / "disable history".
        runCatching { history.closeHandle() }
        vault.lock()
    }

    companion object {
        const val DEFAULT_INACTIVITY_TIMEOUT_MS = 5L * 60 * 1000 // 5 minutes
    }
}
