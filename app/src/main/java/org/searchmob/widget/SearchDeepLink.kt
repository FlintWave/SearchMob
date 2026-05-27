package org.searchmob.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.searchmob.MainActivity

/**
 * Single source of truth for the "open the in-app Search screen" deep link used by the home-screen
 * widget (and any future launch surface). Defining the scheme/host/extra here keeps [MainActivity]'s
 * handling tiny and makes the parsing unit-testable without an Activity.
 *
 * Two equivalent triggers are recognised, so the launch works regardless of how the [Intent] was
 * constructed:
 *  - an [Intent] data URI of `searchmob://search`, and
 *  - a boolean extra [EXTRA_OPEN_SEARCH] = true.
 *
 * The widget intentionally carries NO query, history, or result data, only the affordance to open
 * Search, so nothing query-related ever lives on the launcher surface.
 */
object SearchDeepLink {
    const val SCHEME = "searchmob"
    const val HOST_SEARCH = "search"

    /** Boolean intent extra: when true, route navigation to the Search screen on launch. */
    const val EXTRA_OPEN_SEARCH = "org.searchmob.extra.OPEN_SEARCH"

    /** The canonical `searchmob://search` URI. */
    val searchUri: Uri = Uri.parse("$SCHEME://$HOST_SEARCH")

    /**
     * Builds the explicit [Intent] that launches [MainActivity] straight to Search. Used by the
     * widget's `actionStartActivity`. `SINGLE_TOP` + `CLEAR_TOP` reuse an existing task so tapping the
     * widget doesn't stack duplicate activities.
     */
    fun intent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = searchUri
            putExtra(EXTRA_OPEN_SEARCH, true)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    /**
     * Returns true when [intent] asks to open the Search screen, either via the `searchmob://search`
     * data URI or the [EXTRA_OPEN_SEARCH] extra. Null-safe so callers can pass `getIntent()` directly.
     */
    fun shouldOpenSearch(intent: Intent?): Boolean {
        if (intent == null) return false
        if (intent.getBooleanExtra(EXTRA_OPEN_SEARCH, false)) return true
        val data = intent.data ?: return false
        return data.scheme == SCHEME && data.host == HOST_SEARCH
    }
}
