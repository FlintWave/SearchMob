package org.searchmob.ui.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.searchmob.ui.theme.DEFAULT_DARK_ID
import org.searchmob.ui.theme.DEFAULT_FONT_POINT_SIZE
import org.searchmob.ui.theme.DEFAULT_LIGHT_ID
import org.searchmob.ui.theme.ThemeMode
import org.searchmob.ui.theme.clampFontPointSize

/**
 * Typed facade over [PreferencesStore]. Exposes a single [preferences] [Flow] that the UI observes for
 * apply-immediately re-composition, plus suspend writers for each setting. Engine defaults (all
 * enabled) are supplied at construction from the live engine registry so newly added engines default
 * to enabled.
 */
class PreferencesRepository(
    private val store: PreferencesStore,
    private val knownEngineIds: List<String> = emptyList(),
) {
    private val engineDefaults: Map<String, Boolean> = knownEngineIds.associateWith { true }

    /** Local presentation prefs grouped into one combine slot (no typed combine past five flows). */
    private data class Presentation(
        val lightId: String,
        val darkId: String,
        val font: String,
        val language: String,
        val mediaActions: Boolean,
    )

    val preferences: Flow<UserPreferences> =
        combine(
            // First five flows feed the typed five-arg combine; the rest are folded in below because
            // there is no typed combine overload past five. Keep this in sync with the data class fields.
            combine(
                store.getString(PreferenceKeys.THEME_MODE, ThemeMode.SYSTEM.name),
                store.getBoolean(PreferenceKeys.DYNAMIC_COLOR, true),
                store.getBooleanMap(PreferenceKeys.ENGINE_ENABLED, engineDefaults),
                store.getBoolean(PreferenceKeys.HISTORY_ENABLED, false),
                store.getBoolean(PreferenceKeys.NETWORK_ACCESS_ENABLED, false),
            ) { themeRaw, dynamic, engines, history, networkAccess ->
                UserPreferences(
                    themeMode = ThemeMode.fromName(themeRaw),
                    dynamicColor = dynamic,
                    engineEnabled = engines,
                    historyEnabled = history,
                    networkAccessEnabled = networkAccess,
                )
            },
            store.getBoolean(PreferenceKeys.UPSTREAM_SUGGESTIONS_ENABLED, false),
            store.getBoolean(PreferenceKeys.SUMMARY_ENABLED, true),
            // The typed combine tops out at five flows, so the update-check flag and the AI-slop mode
            // share one sub-flow here; destructured below into their respective fields.
            combine(
                store.getBoolean(PreferenceKeys.UPDATE_CHECK_ENABLED, true),
                store.getString(PreferenceKeys.AI_SLOP_MODE, "downrank"),
                store.getString(PreferenceKeys.SORT_MODE, "fresh"),
            ) { updateCheck, aiSlop, sortMode -> Triple(updateCheck, aiSlop, sortMode) },
            // The two theme slots, the font size, and the UI language share one sub-flow (the local
            // presentation prefs); the typed combine tops out at five flows but four fit here.
            combine(
                store.getString(PreferenceKeys.LIGHT_THEME, DEFAULT_LIGHT_ID),
                store.getString(PreferenceKeys.DARK_THEME, DEFAULT_DARK_ID),
                store.getString(PreferenceKeys.FONT_POINT_SIZE, DEFAULT_FONT_POINT_SIZE.toString()),
                store.getString(PreferenceKeys.LANGUAGE, ""),
                store.getBoolean(PreferenceKeys.MEDIA_ACTIONS_ENABLED, true),
            ) { lightId, darkId, font, language, media ->
                Presentation(lightId, darkId, font, language, media)
            },
        ) { base, upstreamSuggestions, summary, updateAndSlopSort, presentation ->
            val (updateCheck, aiSlop, sortMode) = updateAndSlopSort
            base.copy(
                upstreamSuggestionsEnabled = upstreamSuggestions,
                summaryEnabled = summary,
                sortMode = sortMode,
                updateCheckEnabled = updateCheck,
                aiSlopMode = aiSlop,
                lightThemeId = presentation.lightId,
                darkThemeId = presentation.darkId,
                fontPointSize = clampFontPointSize(presentation.font.toIntOrNull() ?: DEFAULT_FONT_POINT_SIZE),
                language = presentation.language,
                mediaActionsEnabled = presentation.mediaActions,
            )
        }

    /** Whether the first-run wizard has been completed or skipped. Gates the wizard in navigation. */
    val onboardingCompleted: Flow<Boolean> =
        store.getBoolean(PreferenceKeys.ONBOARDING_COMPLETED, false)

    suspend fun setOnboardingCompleted(completed: Boolean) =
        store.setBoolean(PreferenceKeys.ONBOARDING_COMPLETED, completed)

    /** The onboarding revision the user last saw (0 if never). Drives re-onboarding after updates. */
    val onboardingVersion: Flow<Int> =
        store.getString(PreferenceKeys.ONBOARDING_VERSION, "0").map { it.toIntOrNull() ?: 0 }

    suspend fun setOnboardingVersion(version: Int) =
        store.setString(PreferenceKeys.ONBOARDING_VERSION, version.toString())

    /**
     * Whether click personalization is enabled. OFF by default (store-nothing): with it off, no
     * engagement is recorded and ranking is untouched. A dedicated flow rather than a field on
     * [UserPreferences] because the typed combine there is already at capacity.
     */
    val personalizationEnabled: Flow<Boolean> =
        store.getBoolean(PreferenceKeys.PERSONALIZATION_ENABLED, false)

    suspend fun setPersonalizationEnabled(enabled: Boolean) =
        store.setBoolean(PreferenceKeys.PERSONALIZATION_ENABLED, enabled)

    /** One-shot read for the search providers (in-app + server) deciding whether to personalize. */
    suspend fun personalizationEnabled(): Boolean = personalizationEnabled.first()

    /**
     * Whether the opt-in network mode is enabled. OFF by default; the server binds to loopback only
     * unless this is true. The service observes this to (re)bind the embedded server.
     */
    val networkAccessEnabled: Flow<Boolean> =
        store.getBoolean(PreferenceKeys.NETWORK_ACCESS_ENABLED, false)

    suspend fun setNetworkAccessEnabled(enabled: Boolean) {
        // Mint a per-install access token the first time network mode is enabled, so off-loopback
        // clients must present it (loopback is always exempt). Mirrors the desktop behavior.
        if (enabled) ensureNetworkAccessToken()
        store.setBoolean(PreferenceKeys.NETWORK_ACCESS_ENABLED, enabled)
    }

    /** The network-mode access token ("" until network mode has been enabled at least once). */
    val networkAccessToken: Flow<String> =
        store.getString(PreferenceKeys.NETWORK_ACCESS_TOKEN, "")

    suspend fun networkAccessToken(): String = networkAccessToken.first()

    /** Return the access token, minting and persisting a fresh 24-byte URL-safe one if none exists. */
    suspend fun ensureNetworkAccessToken(): String {
        val existing = networkAccessToken.first()
        if (existing.isNotEmpty()) return existing
        val bytes = ByteArray(24).also { java.security.SecureRandom().nextBytes(it) }
        val token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        store.setString(PreferenceKeys.NETWORK_ACCESS_TOKEN, token)
        return token
    }

    /**
     * Whether the opt-in upstream (web) suggestions source is enabled. OFF by default: with it off,
     * suggestions come only from the local encrypted history and nothing is sent off-device. When ON,
     * partial queries are sent to DuckDuckGo's suggestion service through the privacy proxy as the user
     * types. The /suggest route observes this to decide whether to contact the upstream provider.
     */
    val upstreamSuggestionsEnabled: Flow<Boolean> =
        store.getBoolean(PreferenceKeys.UPSTREAM_SUGGESTIONS_ENABLED, false)

    suspend fun setUpstreamSuggestionsEnabled(enabled: Boolean) =
        store.setBoolean(PreferenceKeys.UPSTREAM_SUGGESTIONS_ENABLED, enabled)

    /**
     * Whether the contextual Wikipedia summary box is shown for entity-like queries. ON by default;
     * adds at most one extra request to Wikipedia (already a search engine here) through the proxy.
     */
    val summaryEnabled: Flow<Boolean> =
        store.getBoolean(PreferenceKeys.SUMMARY_ENABLED, true)

    suspend fun setSummaryEnabled(enabled: Boolean) = store.setBoolean(PreferenceKeys.SUMMARY_ENABLED, enabled)

    /** One-shot read for the server's summary fetcher. */
    suspend fun summaryEnabled(): Boolean = summaryEnabled.first()

    /** Result sort order ("fresh"/"date"/"relevance"). Default "fresh" (freshness+relevance blend). */
    val sortMode: Flow<String> = store.getString(PreferenceKeys.SORT_MODE, "fresh")

    suspend fun setSortMode(mode: String) = store.setString(PreferenceKeys.SORT_MODE, mode)

    /**
     * AI-slop / low-quality domain filter mode ("downrank"/"hide"/"off"). Default "downrank": the
     * filter is on out of the box but only sinks listed domains rather than hiding them, so a result
     * the user might want is never silently dropped. Applied entirely on-device in the ranking pass.
     */
    val aiSlopMode: Flow<String> = store.getString(PreferenceKeys.AI_SLOP_MODE, "downrank")

    suspend fun setAiSlopMode(mode: String) = store.setString(PreferenceKeys.AI_SLOP_MODE, mode)

    /** One-shot read for the server / engine slop-filter pass. */
    suspend fun aiSlopMode(): String = aiSlopMode.first()

    /**
     * Whether the opt-out launch-time update check is enabled. ON by default: about once a day the app
     * asks GitHub Releases for a newer version through the privacy proxy. This is the only outbound
     * traffic that is not a search; turning it off disables it entirely.
     */
    val updateCheckEnabled: Flow<Boolean> =
        store.getBoolean(PreferenceKeys.UPDATE_CHECK_ENABLED, true)

    suspend fun setUpdateCheckEnabled(enabled: Boolean) = store.setBoolean(PreferenceKeys.UPDATE_CHECK_ENABLED, enabled)

    /** One-shot read of the update-check enabled flag for the launch-time coordinator. */
    suspend fun updateCheckEnabled(): Boolean = updateCheckEnabled.first()

    /**
     * Timestamp (epoch millis) of the last launch-time update-check attempt, used to throttle to about
     * once a day. Defaults to 0 (never checked). Persisted as a string because the store has no Long
     * type; an unparseable value is treated as 0 so a corrupt value never blocks a check.
     */
    suspend fun lastUpdateCheckMs(): Long =
        store.getString(PreferenceKeys.LAST_UPDATE_CHECK_MS, "0").first().toLongOrNull() ?: 0L

    suspend fun setLastUpdateCheckMs(value: Long) =
        store.setString(PreferenceKeys.LAST_UPDATE_CHECK_MS, value.toString())

    /**
     * The newest release a prior check found (version name + release URL), or empty strings when none
     * is pending. Observed by the in-app banner and read by the served-page banner; persisted so a
     * found update survives a restart. Set via [setPendingUpdate] and cleared by [clearPendingUpdate].
     */
    val pendingUpdateVersion: Flow<String> =
        store.getString(PreferenceKeys.PENDING_UPDATE_VERSION, "")

    val pendingUpdateUrl: Flow<String> =
        store.getString(PreferenceKeys.PENDING_UPDATE_URL, "")

    suspend fun pendingUpdateVersion(): String = pendingUpdateVersion.first()

    suspend fun pendingUpdateUrl(): String = pendingUpdateUrl.first()

    suspend fun setPendingUpdate(
        version: String,
        url: String,
    ) {
        store.setString(PreferenceKeys.PENDING_UPDATE_VERSION, version)
        store.setString(PreferenceKeys.PENDING_UPDATE_URL, url)
    }

    suspend fun clearPendingUpdate() {
        store.setString(PreferenceKeys.PENDING_UPDATE_VERSION, "")
        store.setString(PreferenceKeys.PENDING_UPDATE_URL, "")
    }

    suspend fun setThemeMode(mode: ThemeMode) = store.setString(PreferenceKeys.THEME_MODE, mode.name)

    suspend fun setDynamicColor(enabled: Boolean) = store.setBoolean(PreferenceKeys.DYNAMIC_COLOR, enabled)

    /** Set the named theme filling the light slot (the two-slot model). Persists immediately. */
    suspend fun setLightTheme(themeId: String) = store.setString(PreferenceKeys.LIGHT_THEME, themeId)

    /** Set the named theme filling the dark slot (the two-slot model). Persists immediately. */
    suspend fun setDarkTheme(themeId: String) = store.setString(PreferenceKeys.DARK_THEME, themeId)

    /** Set the base UI font size in points; the value is clamped to the supported 8..24 range. */
    suspend fun setFontPointSize(pointSize: Int) =
        store.setString(PreferenceKeys.FONT_POINT_SIZE, clampFontPointSize(pointSize).toString())

    /**
     * The UI language as a shipped locale tag (e.g. "es"), or "" to follow the OS language. Drives
     * the in-app locale/layout direction and the per-engine result tailoring.
     */
    val language: Flow<String> = store.getString(PreferenceKeys.LANGUAGE, "")

    suspend fun setLanguage(tag: String) = store.setString(PreferenceKeys.LANGUAGE, tag)

    /** One-shot read of the saved language tag ("" = follow OS) for the search providers. */
    suspend fun language(): String = language.first()

    /** Whether the media actions row + canonical-platform promotion are on. Default on. */
    val mediaActionsEnabled: Flow<Boolean> =
        store.getBoolean(PreferenceKeys.MEDIA_ACTIONS_ENABLED, true)

    suspend fun setMediaActionsEnabled(enabled: Boolean) =
        store.setBoolean(PreferenceKeys.MEDIA_ACTIONS_ENABLED, enabled)

    /** One-shot read for the search providers deciding whether to surface media actions. */
    suspend fun mediaActionsEnabled(): Boolean = mediaActionsEnabled.first()

    suspend fun setHistoryEnabled(enabled: Boolean) = store.setBoolean(PreferenceKeys.HISTORY_ENABLED, enabled)

    /**
     * Enable/disable a single engine. Applied as an atomic read-modify-write inside the store so two
     * quick toggles can never lose each other: composing a whole-map write over a snapshot (e.g. a
     * stateIn mirror that is stale until the previous write round-trips) drops the earlier toggle,
     * and toggling before the mirror's first emission would wipe every saved toggle.
     */
    suspend fun setEngineEnabled(
        engineId: String,
        enabled: Boolean,
    ) {
        store.updateBooleanMap(PreferenceKeys.ENGINE_ENABLED) { it + (engineId to enabled) }
    }
}
