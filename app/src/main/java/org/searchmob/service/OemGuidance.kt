package org.searchmob.service

/**
 * Maps a device manufacturer to its dontkillmyapp.com autostart/never-sleep guidance page, with a
 * generic fallback for unlisted vendors. These vendor settings can be reset by firmware updates.
 */
object OemGuidance {
    const val BASE_URL = "https://dontkillmyapp.com"

    const val FIRMWARE_RESET_WARNING =
        "Heads up: some manufacturers reset these settings after a firmware/OS update, " +
            "so you may need to re-apply them."

    data class Guidance(val manufacturerKey: String, val url: String)

    /** Returns manufacturer-specific guidance, or generic guidance for unlisted/unknown vendors. */
    fun forManufacturer(manufacturer: String?): Guidance {
        val normalized = manufacturer?.trim()?.lowercase().orEmpty()
        val slug =
            when {
                normalized.contains("samsung") -> "samsung"
                normalized.contains("xiaomi") || normalized.contains("redmi") || normalized.contains("poco") -> "xiaomi"
                normalized.contains("oneplus") -> "oneplus"
                normalized.contains("huawei") || normalized.contains("honor") -> "huawei"
                else -> null
            }
        return if (slug != null) {
            Guidance(slug, "$BASE_URL/$slug")
        } else {
            Guidance("generic", BASE_URL)
        }
    }
}
