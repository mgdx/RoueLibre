package io.github.mgdx.rouelibre.ui.journey

import androidx.lifecycle.ViewModel
import io.github.mgdx.rouelibre.core.journey.JourneyPlan

/**
 * The journey on screen, as the detail screen needs to read it back.
 *
 * @property origin where the journey was asked to start, under the label the
 *   user gave it.
 * @property destination where it was asked to end.
 * @property plan what the algorithm composed between the two.
 */
data class ShownJourney(
    val origin: JourneyEndpoint,
    val destination: JourneyEndpoint,
    val plan: JourneyPlan,
)

/**
 * Hands the journey being shown from the result screen to its detail
 * (SPEC §7.4).
 *
 * A journey carries the two tracks it traced, point by point: some thousands of
 * coordinates. Writing that into a fragment argument would send it through a
 * `Bundle` — and through the saved instance state on every rotation — for a
 * screen that only ever reads it while the result screen it came from is still
 * on the back stack. It travels through an object scoped to the activity
 * instead, so it lives in memory for as long as the two screens do, and no
 * longer (SPEC §8: no journey data is kept).
 *
 * The consequence is accepted and handled: after the process is killed and the
 * back stack restored, this holder comes back empty. The detail screen then
 * works the journey out again from the two ends it kept in its own state, and
 * leaves the result here — so the user finds the page they were reading rather
 * than the screen above it.
 */
class ShownJourneyViewModel : ViewModel() {

    /** The journey the detail screen describes, or `null` when there is none. */
    var journey: ShownJourney? = null
}
