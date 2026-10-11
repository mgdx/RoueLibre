package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The flag drawn before a network's name, and taken off again for speech. */
class CountryFlagsTest {

    @Test
    fun `an upper-case code spells its flag`() {
        assertEquals("🇫🇷", countryFlag("FR"))
    }

    @Test
    fun `a lower-case code spells the same flag`() {
        assertEquals(countryFlag("FR"), countryFlag("fr"))
    }

    @Test
    fun `spaces around the code are ignored`() {
        assertEquals("🇩🇪", countryFlag(" de "))
    }

    @Test
    fun `no code draws no flag`() {
        assertNull(countryFlag(null))
        assertNull(countryFlag(""))
    }

    @Test
    fun `anything but two letters draws no flag`() {
        assertNull(countryFlag("XYZ"))
        assertNull(countryFlag("1A"))
        assertNull(countryFlag("F"))
        assertNull(countryFlag("É1"))
    }

    @Test
    fun `a label without a country stays as it is`() {
        assertEquals("Vélib' — Paris", withCountryFlag("Vélib' — Paris", null))
        assertEquals("Vélib' — Paris", withCountryFlag("Vélib' — Paris", "1A"))
    }

    @Test
    fun `the flag goes before the label and comes off again`() {
        val flagged = withCountryFlag("Vélib' — Paris", "FR")
        assertEquals("🇫🇷 Vélib' — Paris", flagged)
        assertEquals("Vélib' — Paris", withoutCountryFlag(flagged))
    }

    @Test
    fun `a label that starts with no flag keeps every character`() {
        assertEquals("V'lille — Lille", withoutCountryFlag("V'lille — Lille"))
        assertEquals("", withoutCountryFlag(""))
        // An emoji that is not a flag belongs to the name.
        val bike = "🚲🚲 Bikes"
        assertEquals(bike, withoutCountryFlag(bike))
    }
}
