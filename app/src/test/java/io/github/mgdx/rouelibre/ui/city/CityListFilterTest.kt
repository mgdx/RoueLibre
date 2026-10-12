package io.github.mgdx.rouelibre.ui.city

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the city list goes back to its head. */
class CityListFilterTest {

    @Test
    fun `the first rows shown keep the list where it is`() {
        assertFalse(shouldReturnToTop(null, CityListFilter("", null)))
        assertFalse(shouldReturnToTop(null, CityListFilter("Bergen", "NO")))
    }

    @Test
    fun `rows rebuilt under the same filter keep the list where it is`() {
        val filter = CityListFilter("lyon", "FR")
        assertFalse(shouldReturnToTop(filter, filter.copy()))
    }

    @Test
    fun `widening to every country goes back to the head`() {
        assertTrue(shouldReturnToTop(CityListFilter("", "DE"), CityListFilter("", null)))
    }

    @Test
    fun `choosing another country goes back to the head`() {
        assertTrue(shouldReturnToTop(CityListFilter("", "DE"), CityListFilter("", "FR")))
    }

    @Test
    fun `clearing the search goes back to the head`() {
        assertTrue(shouldReturnToTop(CityListFilter("Bergen", null), CityListFilter("", null)))
    }

    @Test
    fun `typing a letter goes back to the head`() {
        assertTrue(shouldReturnToTop(CityListFilter("Ber", null), CityListFilter("Berg", null)))
    }
}
