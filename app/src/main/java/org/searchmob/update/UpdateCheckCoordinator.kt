package org.searchmob.update

import org.searchmob.ui.prefs.PreferencesRepository

/** Minimum interval between launch-time network checks: about once a day. */
const val UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

/**
 * Pure throttle predicate: a network check is due when at least [UPDATE_CHECK_INTERVAL_MS] has elapsed
 * since [lastCheckMs], or when [lastCheckMs] is 0 or less (never checked), which is always due. Kept
 * pure so the timing rule is unit-testable without a clock or coroutines.
 */
fun isUpdateCheckDue(
    lastCheckMs: Long,
    nowMs: Long,
    intervalMs: Long = UPDATE_CHECK_INTERVAL_MS,
): Boolean = lastCheckMs <= 0L || nowMs - lastCheckMs >= intervalMs

/**
 * Ties the [UpdateChecker], the throttle, and the [PreferencesRepository] together for launch-time use.
 * It does nothing when the user has turned the check off or when the throttle is not yet due, so it can
 * be safely invoked on every launch. Strictly fail-soft: it never throws, and it records the check
 * timestamp after every network attempt (success or failure) so a failing check does not hammer GitHub
 * on every launch.
 *
 * [currentVersionCode] is the running build's version code (read from `PackageInfo`); [nowMs] is the
 * clock, injectable for tests.
 */
class UpdateCheckCoordinator(
    private val preferences: PreferencesRepository,
    private val checker: UpdateChecker,
    private val currentVersionCode: Int,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    /**
     * Runs a check if enabled and due. Returns the [UpdateInfo] when a strictly newer release is found
     * (the signal to prompt), or null when disabled, not due, on any failure, or already up to date.
     */
    suspend fun checkIfDue(): UpdateInfo? =
        runCatching {
            if (!preferences.updateCheckEnabled()) return null
            val now = nowMs()
            if (!isUpdateCheckDue(preferences.lastUpdateCheckMs(), now)) return null

            // Stamp the attempt before the network call returns so a failure still advances the throttle.
            preferences.setLastUpdateCheckMs(now)

            val latest = checker.fetchLatest() ?: return null
            if (latest.isNewerThan(currentVersionCode)) latest else null
        }.getOrNull()
}
