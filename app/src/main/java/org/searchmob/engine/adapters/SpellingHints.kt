package org.searchmob.engine.adapters

import org.jsoup.nodes.Document

/**
 * Returns the text of the first element matching any of [selectors], trimmed, or null. Used by HTML
 * adapters to read an engine's own "did you mean" / "showing results for" suggestion link. Selectors
 * should target the anchor that holds the corrected query so the returned text is the suggestion alone.
 */
internal fun firstText(
    doc: Document,
    vararg selectors: String,
): String? {
    for (selector in selectors) {
        val text = doc.selectFirst(selector)?.text()?.trim()
        if (!text.isNullOrBlank()) return text
    }
    return null
}
