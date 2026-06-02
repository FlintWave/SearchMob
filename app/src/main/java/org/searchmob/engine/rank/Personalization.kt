package org.searchmob.engine.rank

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/*
 * On-device click personalization: a Beta-Bernoulli learning layer over the ranking pass.
 *
 * This is the Android half of a feature kept at parity with the desktop app (engines/rank/
 * personalize.py); the JSON model format (beta_bernoulli_v1) and all the math are identical, so a
 * profile exported on one device imports on the other and produces the same boosts.
 *
 * It learns a bounded ranking adjustment from the owner's own clicks and applies it between the
 * relevance sort and DomainRanker (so explicit pin/raise/lower/block rules always win). The signal
 * is "click greater-than skip-above": when the owner clicks the result at displayed position p, the
 * clicked host gains a click and each distinct host shown above p that was skipped gains a skip;
 * hosts below p are ignored. Each host keeps a Beta(alpha, beta) belief about how often it is clicked
 * when seen; the boost is the posterior mean over a neutral baseline, clamped so personalization
 * nudges ranking rather than dominating it.
 *
 * Everything here is pure: no Android, no storage, no network. It is fail-soft; any error in scoring
 * or reordering returns the input unchanged.
 */

/** Tunables for the learning model. Serialized in the JSON so the model is self-describing. */
@Serializable
data class PersonalizationConfig(
    val alphaPrior: Double = 2.0,
    val betaPrior: Double = 18.0,
    // Neutral baseline click rate; equals the prior mean so an unseen/at-prior host scores boost 1.0.
    val globalMu: Double = 0.10,
    val boostMin: Double = 0.5,
    val boostMax: Double = 2.0,
    val epsilon: Double = 0.10,
    val halfLifeDays: Double = 60.0,
    val minSignalQueries: Int = 5,
    val minDomainImpressions: Int = 3,
    val minQtImpressions: Int = 10,
    val maxDomains: Int = 2000,
    val maxQtPairs: Int = 10000,
)

/**
 * Beta counts for one key (a host, or a `term:host` pair) plus the day it last changed.
 * [alpha]/[beta] include the prior, so a fresh key is `(alphaPrior, betaPrior)`. [lastSeenEpochDays]
 * is integer epoch days, used to fade the excess over the prior toward the prior over time.
 */
@Serializable
data class KeyStats(
    val alpha: Double,
    val beta: Double,
    val lastSeenEpochDays: Long,
)

/**
 * The whole learned state: per-domain and per-(term, host) Beta counts plus config. Mutable on
 * purpose: learning updates the counters in place. [totalClickedQueries] gates cold start. Reads
 * ([boost], [reorder]) never mutate.
 */
class PersonalizationModel(
    val config: PersonalizationConfig = PersonalizationConfig(),
    val domains: MutableMap<String, KeyStats> = LinkedHashMap(),
    val qtPairs: MutableMap<String, KeyStats> = LinkedHashMap(),
    var totalClickedQueries: Int = 0,
) {
    /** True when nothing has been learned yet (a freshly reset or never-used model). */
    fun isEmpty(): Boolean = domains.isEmpty() && qtPairs.isEmpty() && totalClickedQueries == 0
}

/**
 * The pure learning + scoring + serialization operations on a [PersonalizationModel]. Mirrors the
 * functions in the desktop `personalize.py` one-to-one so the two clients stay interoperable.
 */
object Personalizer {
    const val SCHEMA = "beta_bernoulli_v1"
    const val VERSION = 1
    private const val DAY_MS = 86_400_000L
    private const val MAX_QUERY_TERMS = 8

    // ASCII on purpose so Python and Kotlin tokenize identically (the same `term:host` keys matter).
    private val TOKEN_RE = Regex("[a-z0-9]+")
    private val json = Json { ignoreUnknownKeys = true }

    // --- key construction (must stay byte-identical to the desktop port) -------------------------

    /** Lowercase, NFC-normalize, and strip a leading `www.` from a host. */
    fun normalizeHost(host: String): String =
        Normalizer.normalize(host.trim(), Normalizer.Form.NFC).lowercase().removePrefix("www.")

    /** The per-(term, host) key: `"<term>:<host>"` with both parts normalized. */
    fun qtKey(
        term: String,
        host: String,
    ): String = "$term:${normalizeHost(host)}"

    /**
     * Tokenize a query into distinct lowercase alphanumeric terms (length >= 2), capped. ASCII-only
     * so it matches the desktop tokenizer exactly.
     */
    fun queryTerms(query: String): List<String> {
        val norm = Normalizer.normalize(query, Normalizer.Form.NFC).lowercase()
        val out = ArrayList<String>()
        for (match in TOKEN_RE.findAll(norm)) {
            val token = match.value
            if (token.length >= 2 && token !in out) {
                out.add(token)
                if (out.size >= MAX_QUERY_TERMS) break
            }
        }
        return out
    }

    // --- math ------------------------------------------------------------------------------------

    private fun clip(
        value: Double,
        low: Double,
        high: Double,
    ): Double = max(low, min(high, value))

    /** (alpha, beta) with the excess over the prior faded toward the prior by age. */
    private fun decay(
        stats: KeyStats,
        nowDays: Long,
        cfg: PersonalizationConfig,
    ): Pair<Double, Double> {
        val age = nowDays - stats.lastSeenEpochDays
        if (age <= 0) return stats.alpha to stats.beta
        val factor = 0.5.pow(age.toDouble() / cfg.halfLifeDays)
        val alpha = cfg.alphaPrior + (stats.alpha - cfg.alphaPrior) * factor
        val beta = cfg.betaPrior + (stats.beta - cfg.betaPrior) * factor
        return alpha to beta
    }

    /** The per-key boost, or 1.0 below the cold-start impression gate or when the key is unseen. */
    private fun keyBoost(
        table: Map<String, KeyStats>,
        key: String,
        minImpressions: Int,
        nowDays: Long,
        cfg: PersonalizationConfig,
    ): Double {
        val stats = table[key] ?: return 1.0
        val (alpha, beta) = decay(stats, nowDays, cfg)
        val observed = (alpha - cfg.alphaPrior) + (beta - cfg.betaPrior)
        if (observed < minImpressions) return 1.0
        val mu = alpha / (alpha + beta)
        return clip(mu / cfg.globalMu, cfg.boostMin, cfg.boostMax)
    }

    /** The combined, bounded boost for [host] under [terms]. 1.0 (neutral) during cold start. */
    fun boost(
        model: PersonalizationModel,
        host: String?,
        terms: List<String>,
        nowMs: Long,
    ): Double {
        val cfg = model.config
        if (host == null || model.totalClickedQueries < cfg.minSignalQueries) return 1.0
        val norm = normalizeHost(host)
        if (norm.isEmpty()) return 1.0
        val nowDays = nowMs / DAY_MS
        var factor = keyBoost(model.domains, norm, cfg.minDomainImpressions, nowDays, cfg)
        for (term in terms) {
            factor *= keyBoost(model.qtPairs, "$term:$norm", cfg.minQtImpressions, nowDays, cfg)
        }
        return clip(factor, cfg.boostMin, cfg.boostMax)
    }

    // --- learning --------------------------------------------------------------------------------

    private fun bump(
        table: MutableMap<String, KeyStats>,
        key: String,
        click: Boolean,
        nowDays: Long,
        cfg: PersonalizationConfig,
    ) {
        val stats = table[key]
        var (alpha, beta) =
            if (stats == null) cfg.alphaPrior to cfg.betaPrior else decay(stats, nowDays, cfg)
        if (click) alpha += 1.0 else beta += 1.0
        table[key] = KeyStats(alpha = alpha, beta = beta, lastSeenEpochDays = nowDays)
    }

    /** Trim [table] to [cap] entries, dropping the least-observed (lowest alpha+beta) first. */
    private fun evict(
        table: MutableMap<String, KeyStats>,
        cap: Int,
    ) {
        if (table.size <= cap) return
        val keep =
            table.entries
                .sortedByDescending { it.value.alpha + it.value.beta }
                .take(cap)
                .associate { it.key to it.value }
        table.clear()
        table.putAll(keep)
    }

    /**
     * Learn from one click: the clicked host gains a click, each distinct skipped-above host a skip.
     * [orderedHosts] is the final displayed order (hosts may be null for unparsable URLs).
     * [clickedPos] indexes into it. Hosts below the click are ignored. Mutates [model] in place; a
     * malformed call (out-of-range position, unparsable clicked host) is a safe no-op.
     */
    fun updateFromClick(
        model: PersonalizationModel,
        orderedHosts: List<String?>,
        clickedPos: Int,
        terms: List<String>,
        nowMs: Long,
    ) {
        if (clickedPos < 0 || clickedPos >= orderedHosts.size) return
        val raw = orderedHosts[clickedPos]
        val clicked = if (raw != null) normalizeHost(raw) else ""
        if (clicked.isEmpty()) return
        val cfg = model.config
        val nowDays = nowMs / DAY_MS

        val skipped = ArrayList<String>()
        for (i in 0 until clickedPos) {
            val rawAbove = orderedHosts[i]
            val h = if (rawAbove != null) normalizeHost(rawAbove) else ""
            if (h.isNotEmpty() && h != clicked && h !in skipped) skipped.add(h)
        }

        bump(model.domains, clicked, click = true, nowDays = nowDays, cfg = cfg)
        for (h in skipped) bump(model.domains, h, click = false, nowDays = nowDays, cfg = cfg)
        for (term in terms) {
            bump(model.qtPairs, "$term:$clicked", click = true, nowDays = nowDays, cfg = cfg)
            for (h in skipped) bump(model.qtPairs, "$term:$h", click = false, nowDays = nowDays, cfg = cfg)
        }

        model.totalClickedQueries += 1
        evict(model.domains, cfg.maxDomains)
        evict(model.qtPairs, cfg.maxQtPairs)
    }

    // --- apply pass ------------------------------------------------------------------------------

    /**
     * Re-order [items] by a learned, bounded boost on the relevance-rank base score. [items] is
     * assumed already in relevance order. With probability `epsilon` (exploration) or in cold start,
     * the input is returned unchanged. Otherwise each item's weight is `1/(rank+1) * boost(host)` and
     * the list is stable-sorted by weight, so the clamped boost can move an item at most a rank or
     * two. Fail-soft: any error returns [items].
     */
    fun <T> reorder(
        items: List<T>,
        hostOf: (T) -> String?,
        query: String,
        model: PersonalizationModel,
        nowMs: Long,
        rng: () -> Double = { Math.random() },
    ): List<T> {
        val cfg = model.config
        if (items.isEmpty() || model.totalClickedQueries < cfg.minSignalQueries) return items
        if (rng() < cfg.epsilon) return items
        return try {
            val terms = queryTerms(query)
            val weights =
                items.mapIndexed { rank, item ->
                    (1.0 / (rank + 1)) * boost(model, hostOf(item), terms, nowMs)
                }
            items.indices.sortedByDescending { weights[it] }.map { items[it] }
        } catch (_: Exception) {
            items
        }
    }

    // --- serialization (beta_bernoulli_v1) -------------------------------------------------------

    @Serializable
    private data class StatsDto(val alpha: Double, val beta: Double, val lastSeenEpochDays: Long)

    @Serializable
    private data class ModelDto(
        val version: Int = VERSION,
        val schema: String = SCHEMA,
        val config: PersonalizationConfig = PersonalizationConfig(),
        val totalClickedQueries: Int = 0,
        val domains: Map<String, StatsDto> = emptyMap(),
        val qtPairs: Map<String, StatsDto> = emptyMap(),
    )

    private fun round6(value: Double): Double = (value * 1_000_000.0).roundToLong() / 1_000_000.0

    private fun KeyStats.toDto(): StatsDto = StatsDto(round6(alpha), round6(beta), lastSeenEpochDays)

    private fun StatsDto.toStats(): KeyStats = KeyStats(alpha, beta, lastSeenEpochDays)

    /** Serialize to the JSON shared with the desktop client. Fail-soft: returns `"{}"` on error. */
    fun toJson(model: PersonalizationModel): String =
        runCatching {
            json.encodeToString(
                ModelDto(
                    config = roundedConfig(model.config),
                    totalClickedQueries = model.totalClickedQueries,
                    domains = model.domains.mapValues { it.value.toDto() },
                    qtPairs = model.qtPairs.mapValues { it.value.toDto() },
                ),
            )
        }.getOrDefault("{}")

    private fun roundedConfig(cfg: PersonalizationConfig): PersonalizationConfig =
        cfg.copy(
            alphaPrior = round6(cfg.alphaPrior),
            betaPrior = round6(cfg.betaPrior),
            globalMu = round6(cfg.globalMu),
            boostMin = round6(cfg.boostMin),
            boostMax = round6(cfg.boostMax),
            epsilon = round6(cfg.epsilon),
            halfLifeDays = round6(cfg.halfLifeDays),
        )

    /** Parse a model from JSON. Fail-soft: malformed JSON yields an empty model. */
    fun fromJson(text: String): PersonalizationModel {
        val dto = runCatching { json.decodeFromString<ModelDto>(text) }.getOrNull() ?: return PersonalizationModel()
        return PersonalizationModel(
            config = dto.config,
            domains = dto.domains.mapValuesTo(LinkedHashMap()) { it.value.toStats() },
            qtPairs = dto.qtPairs.mapValuesTo(LinkedHashMap()) { it.value.toStats() },
            totalClickedQueries = dto.totalClickedQueries,
        )
    }

    /** A fresh empty model that keeps the existing config. */
    fun reset(model: PersonalizationModel): PersonalizationModel = PersonalizationModel(config = model.config)
}
