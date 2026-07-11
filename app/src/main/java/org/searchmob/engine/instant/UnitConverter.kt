package org.searchmob.engine.instant

/**
 * On-device unit conversion for the instant-answer card: `10 km to miles`, `72 f in c`,
 * `2.5 kg to lb`, `100 mb in gb`, ... Linear units convert through a per-dimension base unit;
 * temperature is affine and special-cased. Everything is a fixed local table - no network, no locale
 * lookup - and anything unrecognized simply returns null so the query falls through to normal search.
 */
object UnitConverter {
    /** One recognized unit: its dimension, factor to the dimension's base unit, and display name. */
    private data class Unit(
        val dimension: String,
        val toBase: Double,
        val singular: String,
        val plural: String = singular + "s",
    )

    // Aliases map many spellings to one unit. Base units: meter, kilogram, liter, second, byte, m/s.
    private val UNITS: Map<String, Unit> =
        buildMap {
            fun register(
                unit: Unit,
                vararg aliases: String,
            ) = aliases.forEach { put(it, unit) }

            // Length (base: meter)
            register(Unit("length", 0.001, "millimeter"), "mm", "millimeter", "millimeters", "millimetre")
            register(Unit("length", 0.01, "centimeter"), "cm", "centimeter", "centimeters", "centimetre")
            register(Unit("length", 1.0, "meter"), "m", "meter", "meters", "metre", "metres")
            register(Unit("length", 1000.0, "kilometer"), "km", "kilometer", "kilometers", "kilometre", "kilometres")
            register(Unit("length", 0.0254, "inch", "inches"), "in", "inch", "inches")
            register(Unit("length", 0.3048, "foot", "feet"), "ft", "foot", "feet")
            register(Unit("length", 0.9144, "yard"), "yd", "yard", "yards")
            register(Unit("length", 1609.344, "mile"), "mi", "mile", "miles")
            register(Unit("length", 1852.0, "nautical mile"), "nmi")
            // Mass (base: kilogram)
            register(Unit("mass", 0.000001, "milligram"), "mg", "milligram", "milligrams")
            register(Unit("mass", 0.001, "gram"), "g", "gram", "grams")
            register(Unit("mass", 1.0, "kilogram"), "kg", "kilogram", "kilograms", "kilo", "kilos")
            register(Unit("mass", 1000.0, "tonne"), "t", "tonne", "tonnes", "ton", "tons")
            register(Unit("mass", 0.028349523125, "ounce"), "oz", "ounce", "ounces")
            register(Unit("mass", 0.45359237, "pound"), "lb", "lbs", "pound", "pounds")
            register(Unit("mass", 6.35029318, "stone"), "st", "stone", "stones")
            // Volume (base: liter)
            register(Unit("volume", 0.001, "milliliter"), "ml", "milliliter", "milliliters", "millilitre")
            register(Unit("volume", 1.0, "liter"), "l", "liter", "liters", "litre", "litres")
            register(Unit("volume", 3.785411784, "US gallon"), "gal", "gallon", "gallons")
            register(Unit("volume", 0.946352946, "US quart"), "qt", "quart", "quarts")
            register(Unit("volume", 0.473176473, "US pint"), "pt", "pint", "pints")
            register(Unit("volume", 0.2365882365, "cup"), "cup", "cups")
            register(Unit("volume", 0.0295735295625, "fluid ounce"), "floz")
            // Speed (base: m/s). "m/s" only - a bare "ms" means milliseconds.
            register(Unit("speed", 1.0, "m/s", "m/s"), "m/s", "mps")
            register(Unit("speed", 1000.0 / 3600.0, "km/h", "km/h"), "km/h", "kmh", "kph")
            register(Unit("speed", 0.44704, "mph", "mph"), "mph")
            register(Unit("speed", 1852.0 / 3600.0, "knot"), "kn", "knot", "knots")
            // Time (base: second)
            register(Unit("time", 0.001, "millisecond"), "ms", "millisecond", "milliseconds")
            register(Unit("time", 1.0, "second"), "s", "sec", "secs", "second", "seconds")
            register(Unit("time", 60.0, "minute"), "min", "mins", "minute", "minutes")
            register(Unit("time", 3600.0, "hour"), "h", "hr", "hrs", "hour", "hours")
            register(Unit("time", 86400.0, "day"), "day", "days")
            register(Unit("time", 604800.0, "week"), "week", "weeks")
            register(Unit("time", 31557600.0, "year"), "year", "years")
            // Data (SI decimal for kb/mb/...; binary for kib/mib/...; base: byte)
            register(Unit("data", 1.0, "byte"), "byte", "bytes")
            register(Unit("data", 1000.0, "kilobyte"), "kb", "kilobyte", "kilobytes")
            register(Unit("data", 1000_000.0, "megabyte"), "mb", "megabyte", "megabytes")
            register(Unit("data", 1000_000_000.0, "gigabyte"), "gb", "gigabyte", "gigabytes")
            register(Unit("data", 1000_000_000_000.0, "terabyte"), "tb", "terabyte", "terabytes")
            register(Unit("data", 1024.0, "kibibyte"), "kib")
            register(Unit("data", 1048576.0, "mebibyte"), "mib")
            register(Unit("data", 1073741824.0, "gibibyte"), "gib")
            register(Unit("data", 1099511627776.0, "tebibyte"), "tib")
            // Area (base: square meter)
            register(Unit("area", 1.0, "square meter"), "m2", "sqm")
            register(Unit("area", 1000000.0, "square kilometer"), "km2", "sqkm")
            register(Unit("area", 2589988.110336, "square mile"), "mi2", "sqmi")
            register(Unit("area", 0.09290304, "square foot", "square feet"), "ft2", "sqft")
            register(Unit("area", 4046.8564224, "acre"), "acre", "acres")
            register(Unit("area", 10000.0, "hectare"), "ha", "hectare", "hectares")
        }

    private val TEMPERATURE_ALIASES =
        mapOf(
            "c" to "c", "°c" to "c", "celsius" to "c", "centigrade" to "c",
            "f" to "f", "°f" to "f", "fahrenheit" to "f",
            "k" to "k", "kelvin" to "k",
        )

    // "<number> <unit> to|in|as <unit>", optionally prefixed with "convert". The unit spellings allow
    // letters, digits (m2), degree signs, slashes (m/s), and a single internal space is not allowed -
    // multi-word names ("nautical mile") are covered by their compact aliases instead.
    private val PATTERN =
        Regex(
            """^(?:convert\s+)?(-?\d+(?:[.,]\d+)?)\s*([a-z°/][a-z0-9°/]*)\s+(?:to|in|as)\s+([a-z°/][a-z0-9°/]*)$""",
            RegexOption.IGNORE_CASE,
        )

    /** Convert per the query shape above, or null when the query is not a recognizable conversion. */
    fun convert(query: String): InstantAnswer? {
        val match = PATTERN.find(query.trim().lowercase()) ?: return null
        val (rawValue, fromRaw, toRaw) = match.destructured
        val value = rawValue.replace(",", ".").toDoubleOrNull() ?: return null

        temperature(value, fromRaw, toRaw)?.let { return it }

        val from = UNITS[fromRaw] ?: return null
        val to = UNITS[toRaw] ?: return null
        if (from.dimension != to.dimension) return null
        if (from == to) return null
        val converted = value * from.toBase / to.toBase
        return InstantAnswer(
            expression = "${Numbers.format(value)} ${unitLabel(from, value)}",
            result = "${Numbers.format(converted)} ${unitLabel(to, converted)}",
            kind = InstantAnswer.Kind.UNIT_CONVERSION,
        )
    }

    private fun temperature(
        value: Double,
        fromRaw: String,
        toRaw: String,
    ): InstantAnswer? {
        val from = TEMPERATURE_ALIASES[fromRaw] ?: return null
        val to = TEMPERATURE_ALIASES[toRaw] ?: return null
        if (from == to) return null
        val celsius =
            when (from) {
                "c" -> value
                "f" -> (value - 32.0) * 5.0 / 9.0
                else -> value - 273.15
            }
        val converted =
            when (to) {
                "c" -> celsius
                "f" -> celsius * 9.0 / 5.0 + 32.0
                else -> celsius + 273.15
            }
        fun label(symbol: String) =
            when (symbol) {
                "c" -> "°C"
                "f" -> "°F"
                else -> "K"
            }
        return InstantAnswer(
            expression = "${Numbers.format(value)} ${label(from)}",
            result = "${Numbers.format(converted)} ${label(to)}",
            kind = InstantAnswer.Kind.UNIT_CONVERSION,
        )
    }

    private fun unitLabel(
        unit: Unit,
        value: Double,
    ): String = if (value == 1.0) unit.singular else unit.plural
}
