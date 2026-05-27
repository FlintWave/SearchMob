package org.searchmob.service

import android.content.Context
import android.os.PowerManager

/**
 * Minimal lock abstraction so the work-bounded wake-lock policy is unit-testable off-device.
 */
interface WorkLock {
    fun acquire(timeoutMs: Long)

    fun release()
}

/**
 * Runs [block] while holding a short, timed partial wake-lock, releasing it in a `finally` so the
 * lock is released on both normal and exceptional completion.
 *
 * Battery discipline: this is invoked ONLY around an actual unit of work. An idle service never
 * calls it and therefore holds no wake-lock, letting the CPU sleep during device idle/Doze.
 */
inline fun <T> withWorkWakeLock(
    lock: WorkLock,
    timeoutMs: Long = 60_000L,
    block: () -> T,
): T {
    lock.acquire(timeoutMs)
    try {
        return block()
    } finally {
        lock.release()
    }
}

/** Android-backed [WorkLock] wrapping a `PARTIAL_WAKE_LOCK`. */
class AndroidWorkLock(context: Context, tag: String = "SearchMob:work") : WorkLock {
    private val wakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
            .apply { setReferenceCounted(false) }

    override fun acquire(timeoutMs: Long) {
        wakeLock.acquire(timeoutMs)
    }

    override fun release() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }
}
