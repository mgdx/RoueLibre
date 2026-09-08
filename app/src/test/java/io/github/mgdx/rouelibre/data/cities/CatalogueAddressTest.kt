package io.github.mgdx.rouelibre.data.cities

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.Continuation

/**
 * Where the catalogue is fetched from (SPEC §15).
 *
 * The city screen used to read that address **in the catalogue in force**, which
 * is the downloaded one as soon as one has been downloaded. A single hostile
 * catalogue therefore named where every later catalogue would come from, and the
 * file that carried it sits in `filesDir` — it outlives an application update,
 * so one compromised answer kept the client for good. The list of cities may
 * still come from a downloaded document; the address may not.
 *
 * The rule is held by the shape of the API rather than by a comparison, and that
 * is what this checks: [CityCatalogueSource.refresh] takes no address, so no
 * caller — hence no document — has one to give. Checking it through a call would
 * take a `Context` for the assets and the cache, and this project runs its unit
 * tests on the JVM with no Android runtime (SPEC §14); the signature is what can
 * be read here, and it is what the defect consisted of.
 */
class CatalogueAddressTest {

    @Test
    fun `refreshing the catalogue takes no address from anybody`() {
        val refreshes = CityCatalogueSource::class.java.declaredMethods
            .filter { it.name == "refresh" }

        assertEquals("one way to refresh, and only one", 1, refreshes.size)
        assertEquals(
            "refresh() must take nothing but the coroutine it runs in",
            listOf(Continuation::class.java),
            refreshes.single().parameterTypes.toList(),
        )
    }
}
