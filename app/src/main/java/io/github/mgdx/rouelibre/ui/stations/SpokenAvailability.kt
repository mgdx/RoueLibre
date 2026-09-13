package io.github.mgdx.rouelibre.ui.stations

import android.content.Context
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.station.StationWithAvailability

/**
 * What a screen reader is told a station holds (SPEC §7.1).
 *
 * The two counts are the subject of both screens that show them, and both
 * **draw** them: [AvailabilityIndicatorView] paints its figure onto a canvas,
 * so nothing in the accessibility tree carries it unless it is said in words.
 * A reader who cannot see the discs was given "BIKES" and "FREE DOCKS" and
 * never a number — the one thing they had come for.
 *
 * The sentence is built here rather than at each screen so that the list row
 * and the station's sheet cannot drift apart: they show the same state and owe
 * the same answer, down to the plurals that make it agree.
 *
 * **It names its station**, which the row needs — a row is read on its own, out
 * of a list of hundreds — and which the sheet keeps rather than trim, because
 * `station_content_description` is already translated into every language the
 * application ships: Japanese joins its parts with 、, Arabic with ، behind a
 * right-to-left mark. A separator of our own would read in English in the
 * twenty-eight languages nobody has translated it into yet, and the name spoken
 * twice costs a word where that would cost the sentence.
 *
 * @param entry the station and its last known state.
 * @param isOutOfService whether the service being counted is refused. The row
 *   asks it of the single count it shows, the sheet of the station as a whole:
 *   a station that has stopped lending may still be taking bikes back.
 */
fun Context.spokenAvailability(entry: StationWithAvailability, isOutOfService: Boolean): String {
    val availability = entry.availability
    if (isOutOfService || availability == null) {
        val state = getString(
            if (isOutOfService) {
                R.string.station_out_of_service
            } else {
                R.string.station_availability_unknown
            },
        )
        return "${entry.station.name}, $state"
    }
    return getString(
        R.string.station_content_description,
        entry.station.name,
        resources.getQuantityString(
            R.plurals.bikes_available,
            availability.bikesAvailable,
            availability.bikesAvailable,
        ),
        resources.getQuantityString(
            R.plurals.docks_available,
            availability.docksAvailable,
            availability.docksAvailable,
        ),
    )
}
