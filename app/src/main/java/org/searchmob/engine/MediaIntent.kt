package org.searchmob.engine

import java.net.URI
import java.net.URLEncoder

/** The media categories the actions row supports. */
enum class MediaCategory { MUSIC, FILM_TV, BOOKS, GAMES }

/** One destination: a display name and a query-URL template with a single `{q}` placeholder. */
data class Platform(val name: String, val template: String, val host: String)

/** One destination in the actions row: a display label and a ready-to-render URL. */
data class ActionLink(val label: String, val url: String)

/** The media actions row for an entity: its category and its destination links. */
data class ActionsRow(val category: MediaCategory, val links: List<ActionLink>)

/**
 * Media intent: detect a query's media category from the resolved entity and surface its platforms.
 *
 * When a query resolves to a piece of media (film, musician, album, song, book, or video game), this
 * turns the Wikipedia/Wikidata summary the app already fetches into a [MediaCategory] (parsed from
 * the entity's short description, so no extra network call) and an [ActionsRow] — the entity's
 * Wikipedia article followed by per-platform deep links built locally from the entity name. Free/open
 * platforms lead each category; the order is fixed and disclosed, with no affiliate/tracking params.
 *
 * Detection is resolved-entity-only: with no confident entity there is no category, no row, and no
 * ranking change. The same category drives a bounded positive promotion of canonical-platform
 * results in the ranking (the mirror of the AI-slop downrank). Ported 1:1 from the desktop app's
 * `engines/media_intent.py` so the two apps detect, map, and link identically.
 */
object MediaIntent {
    // Per-category platforms, free/open first, then mainstream, then neutral reference. `{q}` is the
    // URL-encoded entity name. No affiliate/tracking params. The entity's Wikipedia article is
    // prepended separately by buildActionsRow.
    private val PLATFORMS: Map<MediaCategory, List<Platform>> =
        mapOf(
            MediaCategory.MUSIC to
                listOf(
                    Platform("Bandcamp", "https://bandcamp.com/search?q={q}", "bandcamp.com"),
                    Platform("YouTube Music", "https://music.youtube.com/search?q={q}", "youtube.com"),
                    Platform("SoundCloud", "https://soundcloud.com/search?q={q}", "soundcloud.com"),
                    Platform("Discogs", "https://www.discogs.com/search/?q={q}", "discogs.com"),
                    Platform("Spotify", "https://open.spotify.com/search/{q}", "spotify.com"),
                    Platform("Apple Music", "https://music.apple.com/us/search?term={q}", "music.apple.com"),
                    Platform("Genius", "https://genius.com/search?q={q}", "genius.com"),
                    Platform("Last.fm", "https://www.last.fm/search?q={q}", "last.fm"),
                ),
            MediaCategory.FILM_TV to
                listOf(
                    Platform("YouTube", "https://www.youtube.com/results?search_query={q}", "youtube.com"),
                    Platform("JustWatch", "https://www.justwatch.com/us/search?q={q}", "justwatch.com"),
                    Platform("IMDb", "https://www.imdb.com/find/?q={q}", "imdb.com"),
                    Platform("TMDB", "https://www.themoviedb.org/search?query={q}", "themoviedb.org"),
                    Platform("Letterboxd", "https://letterboxd.com/search/{q}/", "letterboxd.com"),
                    Platform(
                        "Rotten Tomatoes",
                        "https://www.rottentomatoes.com/search?search={q}",
                        "rottentomatoes.com",
                    ),
                ),
            MediaCategory.BOOKS to
                listOf(
                    Platform("Open Library", "https://openlibrary.org/search?q={q}", "openlibrary.org"),
                    Platform(
                        "Project Gutenberg",
                        "https://www.gutenberg.org/ebooks/search/?query={q}",
                        "gutenberg.org",
                    ),
                    Platform("StoryGraph", "https://app.thestorygraph.com/browse?search_term={q}", "thestorygraph.com"),
                    Platform("Goodreads", "https://www.goodreads.com/search?q={q}", "goodreads.com"),
                    Platform("Google Books", "https://www.google.com/search?tbm=bks&q={q}", "books.google.com"),
                ),
            MediaCategory.GAMES to
                listOf(
                    Platform("GOG", "https://www.gog.com/games?query={q}", "gog.com"),
                    Platform("Steam", "https://store.steampowered.com/search/?term={q}", "steampowered.com"),
                    Platform("Metacritic", "https://www.metacritic.com/search/{q}/", "metacritic.com"),
                    Platform("IGDB", "https://www.igdb.com/search?type=1&q={q}", "igdb.com"),
                    Platform("Epic", "https://store.epicgames.com/en-US/browse?q={q}", "epicgames.com"),
                ),
        )

    // Type words (from a Wikipedia short description / lead) that map to a category, ordered by
    // specificity: "video game" beats bare "game"; "graphic novel" / "comic" go to Books.
    private val TYPE_CUES: List<Pair<String, MediaCategory>> =
        listOf(
            "video game" to MediaCategory.GAMES,
            "studio album" to MediaCategory.MUSIC,
            "album" to MediaCategory.MUSIC,
            "song" to MediaCategory.MUSIC,
            "single by" to MediaCategory.MUSIC,
            "extended play" to MediaCategory.MUSIC,
            "rock band" to MediaCategory.MUSIC,
            "band" to MediaCategory.MUSIC,
            "musician" to MediaCategory.MUSIC,
            "singer" to MediaCategory.MUSIC,
            "rapper" to MediaCategory.MUSIC,
            "composer" to MediaCategory.MUSIC,
            "discography" to MediaCategory.MUSIC,
            "television series" to MediaCategory.FILM_TV,
            "tv series" to MediaCategory.FILM_TV,
            "miniseries" to MediaCategory.FILM_TV,
            "sitcom" to MediaCategory.FILM_TV,
            "anime" to MediaCategory.FILM_TV,
            "film" to MediaCategory.FILM_TV,
            "documentary" to MediaCategory.FILM_TV,
            "graphic novel" to MediaCategory.BOOKS,
            "novel" to MediaCategory.BOOKS,
            "novella" to MediaCategory.BOOKS,
            "memoir" to MediaCategory.BOOKS,
            "book by" to MediaCategory.BOOKS,
            "comic" to MediaCategory.BOOKS,
        )

    // How many positions a canonical-platform result may be lifted. Small and bounded on purpose.
    private const val PROMOTE_BOOST = 3

    /** Map a resolved entity's short description to a [MediaCategory], or null when it is not media. */
    fun detectCategory(description: String): MediaCategory? {
        if (description.isBlank()) return null
        val text = description.lowercase().replace(Regex("\\s+"), " ")
        return TYPE_CUES.firstOrNull { text.contains(it.first) }?.second
    }

    /** Build the actions row for [entityName] in [category], leading with its Wikipedia article. */
    fun buildActionsRow(
        category: MediaCategory,
        entityName: String,
        wikipediaUrl: String?,
    ): ActionsRow {
        val q = URLEncoder.encode(entityName, "UTF-8")
        val links = mutableListOf<ActionLink>()
        if (!wikipediaUrl.isNullOrBlank()) links.add(ActionLink("Wikipedia", wikipediaUrl))
        PLATFORMS.getValue(category).forEach { links.add(ActionLink(it.name, it.template.replace("{q}", q))) }
        return ActionsRow(category, links)
    }

    /** Whether [url]'s host is (or is a subdomain of) one of [category]'s platform hosts. */
    fun hostInCategory(
        url: String,
        category: MediaCategory,
    ): Boolean {
        val host = (runCatching { URI(url).host }.getOrNull() ?: "").lowercase().removePrefix("www.")
        return PLATFORMS.getValue(category).any { host == it.host || host.endsWith("." + it.host) }
    }

    /**
     * Stably lift results whose host is in [category]'s platform set by at most [boost] slots. A
     * positive mirror of the AI-slop downrank: bounded and order-preserving, applied after relevance
     * and before the user's domain rules so pin/raise/block still win.
     */
    fun <T> promoteMedia(
        results: List<T>,
        category: MediaCategory,
        urlOf: (T) -> String,
        boost: Int = PROMOTE_BOOST,
    ): List<T> =
        results
            .mapIndexed { index, item ->
                Triple(index - (if (hostInCategory(urlOf(item), category)) boost else 0), index, item)
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }
}
