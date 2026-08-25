package io.github.mgdx.rouelibre.core.routing

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests of the country-to-driving-side table (SPEC §5). */
class DrivingSideTest {

    @Test
    fun `the fleet's left-driving countries answer left`() {
        // The four countries of the served networks that drive on the left as
        // of August 2026. A regression here is not abstract: it hands Dublin
        // or Canterbury a lane painted on the far side of the road.
        listOf("GB", "IE", "JP", "CY").forEach { country ->
            assertEquals(country, DrivingSide.Left, DrivingSide.ofCountry(country))
        }
    }

    @Test
    fun `right-driving countries answer right`() {
        listOf("FR", "DE", "US", "TR", "XK").forEach { country ->
            assertEquals(country, DrivingSide.Right, DrivingSide.ofCountry(country))
        }
    }

    @Test
    fun `an unknown or absent country reads as the right`() {
        // The commoner side, and the behaviour of every version before the
        // per-side tags were read: a wrong left would claim provision on the
        // wrong side of the road, a wrong right keeps the older reading.
        assertEquals(DrivingSide.Right, DrivingSide.ofCountry(null))
        assertEquals(DrivingSide.Right, DrivingSide.ofCountry(""))
        assertEquals(DrivingSide.Right, DrivingSide.ofCountry("ZZ"))
    }

    @Test
    fun `the case of the code does not decide the side`() {
        // The table is consulted with whatever the configuration holds; the
        // configuration reader uppercases, but this table must not depend on
        // every caller having gone through it.
        assertEquals(DrivingSide.Left, DrivingSide.ofCountry("gb"))
    }
}
