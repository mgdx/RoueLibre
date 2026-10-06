package io.github.mgdx.rouelibre.data.cities

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.config.ActiveCity
import io.github.mgdx.rouelibre.core.config.CityCatalogue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests of how long the active city's verdict is held (SPEC §15.1).
 *
 * The defect: held for the life of the process, keyed by the identifier alone,
 * so a refresh that withdrew the network or brought it back left every screen
 * on the old verdict until the application was restarted.
 */
class ActiveCityMemoTest {

    private val gone = ActiveCity.NoLongerServed(PAU, withdrawal = null)

    @Test
    fun `a verdict is settled once per identifier`() = runTest {
        val memo = ActiveCityMemo()
        var settled = 0
        val settle: suspend (String) -> ActiveCity = {
            settled++
            gone
        }

        memo.of(PAU, settle)
        memo.of(PAU, settle)
        memo.of("lille", settle)

        assertEquals(2, settled)
    }

    @Test
    fun `a new catalogue in force is read against a new verdict (B2)`() = runTest {
        val memo = ActiveCityMemo()
        val verdicts = ArrayDeque(listOf<ActiveCity>(gone, ActiveCity.None))
        val settle: suspend (String) -> ActiveCity = { verdicts.removeFirst() }

        assertEquals(gone, memo.of(PAU, settle))
        memo.after(CatalogueRefresh.Updated(CATALOGUE))

        assertEquals(ActiveCity.None, memo.of(PAU, settle))
    }

    @Test
    fun `a refresh that changed nothing keeps the verdict`() = runTest {
        val memo = ActiveCityMemo()
        var settled = 0
        val settle: suspend (String) -> ActiveCity = {
            settled++
            gone
        }

        memo.of(PAU, settle)
        memo.after(CatalogueRefresh.Unchanged)
        memo.after(CatalogueRefresh.Failed(DataError.Offline))
        memo.of(PAU, settle)

        assertEquals(1, settled)
    }

    @Test
    fun `a verdict settled while the catalogue changed is not kept`() = runTest {
        val memo = ActiveCityMemo()
        var settled = 0

        // The refresh lands while the old catalogues are still being read.
        memo.of(PAU) {
            settled++
            memo.forget()
            gone
        }
        memo.of(PAU) {
            settled++
            gone
        }

        assertEquals(2, settled)
    }

    @Test
    fun `the refresh is handed back as it came`() {
        val refresh = CatalogueRefresh.Updated(CATALOGUE)

        assertEquals(refresh, ActiveCityMemo().after(refresh))
    }

    private companion object {
        const val PAU = "idecycle"
        val CATALOGUE = CityCatalogue(
            catalogueVersion = 1,
            generatedAt = "2026-10-06T14:50:45Z",
            catalogueUrl = null,
            cities = emptyList(),
        )
    }
}
