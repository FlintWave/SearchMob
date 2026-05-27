package org.searchmob.widget

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Verifies the widget -> Search deep-link parsing without needing a launched Activity. */
@RunWith(RobolectricTestRunner::class)
class SearchDeepLinkTest {
    @Test
    fun `null intent does not open search`() {
        assertFalse(SearchDeepLink.shouldOpenSearch(null))
    }

    @Test
    fun `plain intent does not open search`() {
        assertFalse(SearchDeepLink.shouldOpenSearch(Intent()))
    }

    @Test
    fun `extra true opens search`() {
        val intent = Intent().putExtra(SearchDeepLink.EXTRA_OPEN_SEARCH, true)
        assertTrue(SearchDeepLink.shouldOpenSearch(intent))
    }

    @Test
    fun `searchmob search uri opens search`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("searchmob://search"))
        assertTrue(SearchDeepLink.shouldOpenSearch(intent))
    }

    @Test
    fun `unrelated uri does not open search`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/search"))
        assertFalse(SearchDeepLink.shouldOpenSearch(intent))
    }

    @Test
    fun `wrong host does not open search`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("searchmob://settings"))
        assertFalse(SearchDeepLink.shouldOpenSearch(intent))
    }

    @Test
    fun `built intent targets MainActivity and is recognised`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = SearchDeepLink.intent(context)

        assertEquals("org.searchmob.MainActivity", intent.component?.className)
        assertEquals(SearchDeepLink.searchUri, intent.data)
        assertTrue(SearchDeepLink.shouldOpenSearch(intent))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }
}
