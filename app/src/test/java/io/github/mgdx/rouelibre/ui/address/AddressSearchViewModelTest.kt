package io.github.mgdx.rouelibre.ui.address

import io.github.mgdx.rouelibre.core.Outcome
import io.github.mgdx.rouelibre.core.address.AddressEntryKind
import io.github.mgdx.rouelibre.core.address.AddressResult
import io.github.mgdx.rouelibre.core.address.PositionPrecision
import io.github.mgdx.rouelibre.core.geo.Coordinates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests of the address search screen's three states.
 *
 * The one that matters is the middle one. On Strasbourg's hundred and sixty-two
 * thousand streets a scan runs for seconds, and the screen used to spend them
 * asserting the opposite of what it was about to answer: "Nothing matches", for
 * three seconds, over a street it then found. The state machine is driven here
 * against a search that answers only when the test lets it, which is what makes
 * that window observable at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddressSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** The search in flight, completed by the test when it chooses. */
    private var pending = CompletableDeferred<Outcome<List<AddressResult>>>()

    private var installed = true

    private fun model() = AddressSearchViewModel(
        isIndexInstalled = { installed },
        lookUpAddresses = { pending.await() },
    )

    private fun answer(results: List<AddressResult>) {
        pending.complete(Outcome.Success(results))
    }

    @Before
    fun useTestDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun releaseDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing typed invites typing and asserts no absence`() = runTest(dispatcher) {
        val viewModel = model()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("", state.query)
        assertFalse(state.isSearching)
        assertFalse("an untouched screen must claim nothing", state.hasNoMatch)
    }

    @Test
    fun `a search still running claims no absence`() = runTest(dispatcher) {
        val viewModel = model()
        viewModel.onQueryChanged("Rue Pierre de Martimprey")

        // From the keystroke onwards, before the debounce has even elapsed:
        // this is the instant the screen used to conclude from.
        assertTrue(viewModel.state.value.isSearching)
        assertFalse(viewModel.state.value.hasNoMatch)

        // And through the whole scan, which here is as long as the test likes.
        advanceTimeBy(5_000)
        assertTrue(viewModel.state.value.isSearching)
        assertFalse(
            "the screen must not answer for a search it has not run",
            viewModel.state.value.hasNoMatch,
        )

        answer(listOf(RESULT))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isSearching)
        assertFalse(state.hasNoMatch)
        assertEquals(listOf(RESULT), state.results)
    }

    @Test
    fun `a finished search that found nothing does say so`() = runTest(dispatcher) {
        val viewModel = model()
        viewModel.onQueryChanged("zzzzz")
        answer(emptyList())
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isSearching)
        assertTrue("a real absence must still be announced", state.hasNoMatch)
    }

    @Test
    fun `a query typed over a finished empty one claims no absence again`() = runTest(dispatcher) {
        // The sequence that produced the fault: a first query answers nothing,
        // a second is typed, and the empty result of the first is still in the
        // state while the label already shows the second.
        val viewModel = model()
        viewModel.onQueryChanged("Rue")
        answer(emptyList())
        advanceUntilIdle()
        assertTrue(viewModel.state.value.hasNoMatch)

        pending = CompletableDeferred()
        viewModel.onQueryChanged("Rue Pierre de Martimprey")

        assertTrue(viewModel.state.value.isSearching)
        assertFalse(viewModel.state.value.hasNoMatch)
    }

    @Test
    fun `clearing the field leaves nothing asserted`() = runTest(dispatcher) {
        val viewModel = model()
        viewModel.onQueryChanged("zzzzz")
        answer(emptyList())
        advanceUntilIdle()

        viewModel.onQueryChanged("")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isSearching)
        assertFalse(state.hasNoMatch)
        assertEquals(emptyList<AddressResult>(), state.results)
    }

    @Test
    fun `an index that is not installed is not a fruitless search`() = runTest(dispatcher) {
        installed = false
        val viewModel = model()
        viewModel.onQueryChanged("Rue de la Gare")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isIndexInstalled)
        assertFalse(state.isSearching)
        assertFalse(
            "an absent index calls for installing it, not for retyping",
            state.hasNoMatch,
        )
    }

    private companion object {
        val RESULT = AddressResult(
            streetId = 1,
            houseNumber = null,
            houseNumberSuffix = "",
            streetName = "Rue Pierre de Martimprey",
            city = "Aisonville-et-Bernoville",
            postcode = "02110",
            kind = AddressEntryKind.Street,
            position = Coordinates(49.90, 3.60),
            precision = PositionPrecision.StreetOnly,
            distanceInMetres = null,
        )
    }
}
