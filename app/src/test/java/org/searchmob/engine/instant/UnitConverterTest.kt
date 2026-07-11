package org.searchmob.engine.instant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConverterTest {
    @Test
    fun convertsLength() {
        assertEquals("6.213711922 miles", UnitConverter.convert("10 km to miles")!!.result)
        assertEquals("30.48 centimeters", UnitConverter.convert("1 ft in cm")!!.result)
        assertEquals("2.54 centimeters", UnitConverter.convert("1 inch to cm")!!.result)
    }

    @Test
    fun convertsMass() {
        assertEquals("2.204622622 pounds", UnitConverter.convert("1 kg to lb")!!.result)
        assertEquals("453.59237 grams", UnitConverter.convert("1 lb to g")!!.result)
    }

    @Test
    fun convertsTemperatureAffine() {
        assertEquals("22.22222222 °C", UnitConverter.convert("72 f to c")!!.result)
        assertEquals("212 °F", UnitConverter.convert("100 c to f")!!.result)
        assertEquals("273.15 K", UnitConverter.convert("0 celsius to kelvin")!!.result)
        assertEquals("-40 °F", UnitConverter.convert("-40 c to f")!!.result)
    }

    @Test
    fun convertsDataDecimalAndBinary() {
        assertEquals("1000 megabytes", UnitConverter.convert("1 gb to mb")!!.result)
        assertEquals("1024 mebibytes", UnitConverter.convert("1 gib to mib")!!.result)
    }

    @Test
    fun convertsSpeedAndTime() {
        assertEquals("62.13711922 mph", UnitConverter.convert("100 km/h to mph")!!.result)
        assertEquals("90 minutes", UnitConverter.convert("1.5 hours in min")!!.result)
    }

    @Test
    fun acceptsConvertPrefixAndCommaDecimal() {
        assertNotNull(UnitConverter.convert("convert 5 km to mi"))
        assertEquals("2500 meters", UnitConverter.convert("2,5 km to m")!!.result)
    }

    @Test
    fun singularUnitLabelForExactlyOne() {
        assertEquals("1 mile", UnitConverter.convert("1.609344 km to mi")!!.result)
    }

    @Test
    fun rejectsUnknownOrMismatchedUnits() {
        assertNull(UnitConverter.convert("10 km to kg")) // cross-dimension
        assertNull(UnitConverter.convert("10 foo to bar"))
        assertNull(UnitConverter.convert("km to miles")) // no number
        assertNull(UnitConverter.convert("10 km")) // no target
        assertNull(UnitConverter.convert("5 km to km")) // same unit answers nothing
    }
}
