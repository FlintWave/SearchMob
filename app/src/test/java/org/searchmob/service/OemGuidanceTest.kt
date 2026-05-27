package org.searchmob.service

import org.junit.Assert.assertEquals
import org.junit.Test

class OemGuidanceTest {
    @Test
    fun mapsKnownManufacturers() {
        assertEquals("https://dontkillmyapp.com/samsung", OemGuidance.forManufacturer("Samsung").url)
        assertEquals("https://dontkillmyapp.com/xiaomi", OemGuidance.forManufacturer("Xiaomi").url)
        assertEquals("https://dontkillmyapp.com/xiaomi", OemGuidance.forManufacturer("Redmi").url)
        assertEquals("https://dontkillmyapp.com/xiaomi", OemGuidance.forManufacturer("POCO").url)
        assertEquals("https://dontkillmyapp.com/oneplus", OemGuidance.forManufacturer("OnePlus").url)
        assertEquals("https://dontkillmyapp.com/huawei", OemGuidance.forManufacturer("HUAWEI").url)
        assertEquals("https://dontkillmyapp.com/huawei", OemGuidance.forManufacturer("Honor").url)
    }

    @Test
    fun fallsBackForUnknownOrNull() {
        assertEquals(OemGuidance.BASE_URL, OemGuidance.forManufacturer("Google").url)
        assertEquals(OemGuidance.BASE_URL, OemGuidance.forManufacturer(null).url)
        assertEquals(OemGuidance.BASE_URL, OemGuidance.forManufacturer("").url)
        assertEquals("generic", OemGuidance.forManufacturer("Nokia").manufacturerKey)
    }
}
