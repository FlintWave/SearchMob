package org.searchmob.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLocalesTest {
    @Test
    fun normalizeTagReducesToSupportedPrimarySubtag() {
        assertEquals("es", SupportedLocales.normalizeTag("es-MX"))
        assertEquals("zh", SupportedLocales.normalizeTag("zh-Hans-CN"))
        assertEquals("pt", SupportedLocales.normalizeTag("pt_BR"))
        assertEquals("ar", SupportedLocales.normalizeTag("AR"))
    }

    @Test
    fun normalizeTagFallsBackToEnglishForUnsupportedOrBlank() {
        assertEquals("en", SupportedLocales.normalizeTag("de"))
        assertEquals("en", SupportedLocales.normalizeTag(""))
        assertEquals("en", SupportedLocales.normalizeTag(null))
    }

    @Test
    fun normalizeTagMapsTheIndonesianLegacyCode() {
        // A JVM Locale built for Indonesian reports its language as the legacy "in"; it must resolve
        // back to the shipped "id" tag, not fall through to English.
        assertEquals("id", SupportedLocales.normalizeTag("in"))
        assertEquals("id", SupportedLocales.normalizeTag("in-ID"))
        assertEquals("id", SupportedLocales.normalizeTag("id"))
    }

    @Test
    fun isSupportedTracksTheShippedSet() {
        assertTrue(SupportedLocales.isSupported("fr"))
        assertTrue(SupportedLocales.isSupported("zh-Hans"))
        assertTrue(SupportedLocales.isSupported("in")) // Indonesian legacy code
        assertFalse(SupportedLocales.isSupported("de"))
        assertFalse(SupportedLocales.isSupported(""))
        assertFalse(SupportedLocales.isSupported(null))
    }

    @Test
    fun rtlOnlyForArabicAndUrdu() {
        assertTrue(SupportedLocales.isRtl("ar"))
        assertTrue(SupportedLocales.isRtl("ur"))
        assertFalse(SupportedLocales.isRtl("en"))
        assertFalse(SupportedLocales.isRtl("fr"))
        assertFalse(SupportedLocales.isRtl("he")) // not shipped -> normalizes to en -> ltr
    }

    @Test
    fun javaLocaleForRegionQualifiesSimplifiedChinese() {
        // zh resources live in values-zh-rCN, so the config locale must carry the CN region to match.
        assertEquals("zh-CN", SupportedLocales.javaLocaleFor("zh").toLanguageTag())
        assertEquals("es", SupportedLocales.javaLocaleFor("es").toLanguageTag())
        assertEquals("ar", SupportedLocales.javaLocaleFor("ar").toLanguageTag())
    }

    @Test
    fun effectiveTagPrefersAPinnedSupportedTag() {
        assertEquals("es", SupportedLocales.effectiveTag("es"))
        assertEquals("ar", SupportedLocales.effectiveTag("ar-EG"))
    }

    @Test
    fun tenLocalesShippedWithEnglishFirst() {
        assertEquals(10, SupportedLocales.SUPPORTED.size)
        assertEquals("en", SupportedLocales.SUPPORTED.first().tag)
        // Every locale carries an English name and an endonym for the picker.
        assertTrue(SupportedLocales.SUPPORTED.all { it.englishName.isNotBlank() && it.nativeName.isNotBlank() })
    }
}
