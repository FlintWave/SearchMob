package org.searchmob.engine.instant

/**
 * A computed, on-device instant answer for a query (a calculation, a unit conversion, ...), shown in
 * an answer card above the search results the way commercial engines answer directly. Everything here
 * is computed locally from the query text alone: no network request, nothing stored, nothing logged.
 */
data class InstantAnswer(
    /** What was computed, e.g. `2 + 2` or `10 km`, normalized for display. */
    val expression: String,
    /** The computed value, formatted for display, e.g. `4` or `6.2137 miles`. */
    val result: String,
    /** The kind of answer, so surfaces can label or style the card ("Calculator", "Convert", ...). */
    val kind: Kind,
) {
    enum class Kind { CALCULATOR, UNIT_CONVERSION, BASE_CONVERSION, PERCENTAGE }
}
