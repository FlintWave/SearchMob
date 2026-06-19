package org.searchmob.engine.rank

/**
 * Pure host matcher for the bundled AI-slop / low-quality domain blocklist. Kept free of any Android
 * dependency so it is plain-JVM unit testable; the asset is loaded separately by [AiSlopBlocklistLoader].
 *
 * A host matches when the host itself or any of its parent domains is in the set, checked at label
 * boundaries so "slopfarm.example" covers "www.slopfarm.example" but a bare TLD never matches on its
 * own. The set is the bare-domain blocklist; matching against it is what the ranker uses to downrank
 * or hide a result.
 */
object AiSlopBlocklist {
    /** True when [host] or a parent domain of it is in [domains] (suffix match at label edges). */
    fun matches(
        host: String,
        domains: Set<String>,
    ): Boolean {
        if (domains.isEmpty()) return false
        val parts = host.split(".")
        // Check the host and each parent (a.b.com, b.com) but never a bare TLD on its own.
        for (i in 0 until parts.size - 1) {
            if (parts.subList(i, parts.size).joinToString(".") in domains) return true
        }
        return false
    }

    /**
     * The effective blocklist: [raw] minus every [allow]listed domain and any subdomain of one. The
     * community lists include the official sites of AI companies and major dev hubs, which a search
     * ranker must never bury, so a search for one of those names returns its real site at the top.
     */
    fun effectiveBlocklist(
        raw: Set<String>,
        allow: Set<String>,
    ): Set<String> {
        if (allow.isEmpty()) return raw
        return raw.filterTo(HashSet()) { domain ->
            allow.none { domain == it || domain.endsWith(".$it") }
        }
    }
}
