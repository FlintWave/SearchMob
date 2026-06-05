package org.searchmob.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the machine-authored per-locale `values-<q>/strings.xml` catalogs against the two failures
 * that would actually break the app: a translation that drops or mangles a format placeholder (which
 * would throw at `getString(id, ...)` time) and a stray key not present in the English source. Missing
 * keys are allowed — Android falls back to the base string — so this stays valid while authoring is
 * still topping a locale up.
 */
class StringCatalogIntegrityTest {
    private val resDir = File("src/main/res")
    private val stringRegex = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val placeholderRegex = Regex("""%\d*\$?[sd]""")

    private fun parse(file: File): Map<String, String> =
        stringRegex.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }

    private fun placeholders(value: String): List<String> =
        placeholderRegex.findAll(value).map {
            it.value
        }.sorted().toList()

    @Test
    fun translatedCatalogsHaveNoStrayKeysAndKeepPlaceholders() {
        val base = parse(File(resDir, "values/strings.xml"))
        assertTrue("base strings.xml should parse", base.isNotEmpty())

        val localeDirs = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty()
        // The translation catalogs are committed; if none exist yet the test is vacuously about parsing.
        for (dir in localeDirs) {
            val file = File(dir, "strings.xml")
            if (!file.exists()) continue
            val translated = parse(file)
            for ((key, value) in translated) {
                val baseValue = base[key]
                assertTrue("${dir.name}: key '$key' is not in the English source", baseValue != null)
                assertEquals(
                    "${dir.name}: placeholders for '$key' must match the English source",
                    placeholders(baseValue!!),
                    placeholders(value),
                )
            }
        }
    }
}
