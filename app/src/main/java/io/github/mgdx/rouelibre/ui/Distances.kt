package io.github.mgdx.rouelibre.ui

import android.content.Context
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.measure.LengthUnit
import io.github.mgdx.rouelibre.core.measure.WrittenLength
import io.github.mgdx.rouelibre.core.measure.writeAltitude
import io.github.mgdx.rouelibre.core.measure.writeClimb
import io.github.mgdx.rouelibre.core.measure.writeDistance

/**
 * Puts a distance into words, in the interface's language and in the reader's
 * units.
 *
 * The arithmetic — which unit, what rounding, where the mile takes over — lives
 * in `core`, in plain Kotlin, and is the **only** place the application ever
 * leaves the metre (SPEC §14). What is left here is the one thing that needs a
 * `Context`: fetching the unit's symbol from the resources, since not a single
 * string is written in the code (SPEC §9).
 *
 * @param metres the distance to write, as everything upstream measures it.
 * @return a distance ready to show, "250 m", "1,4 km" or "820 ft" for instance.
 */
fun Context.formatDistance(metres: Double): String =
    write(writeDistance(metres, DisplayedUnits.current(), textLocale()))

/**
 * Puts a climb into words, or says there is none worth naming.
 *
 * The two silences and the rounding are `core`'s (see `writeClimb`), and they
 * are measured in metres whatever the reader's units: they describe what the
 * SRTM samples can hold, not how the figure is written.
 *
 * @param metres the climb the routing engine measured.
 * @param overMetres the ground it was gained over: one leg's length, or the
 *   whole journey's.
 * @return the climb ready to show, or null where saying anything would be
 *   saying too much.
 */
fun Context.formatClimb(metres: Int, overMetres: Int): String? =
    writeClimb(metres, overMetres, DisplayedUnits.current(), textLocale())?.let { write(it) }

/**
 * Writes a height above sea level.
 *
 * @param metres the height the routing graph holds.
 */
fun Context.formatAltitude(metres: Double): String =
    write(writeAltitude(metres, DisplayedUnits.current(), textLocale()))

/**
 * Hangs a unit's symbol on a figure `core` has already written.
 *
 * One resource per unit rather than one symbol looked up and concatenated:
 * a language is free to place its symbol where it pleases, and word order is
 * exactly what positional placeholders exist for (SPEC §9).
 */
private fun Context.write(length: WrittenLength): String = getString(
    when (length.unit) {
        LengthUnit.Metre -> R.string.distance_metres
        LengthUnit.Kilometre -> R.string.distance_kilometres
        LengthUnit.Foot -> R.string.distance_feet
        LengthUnit.Yard -> R.string.distance_yards
        LengthUnit.Mile -> R.string.distance_miles
    },
    length.amount,
)
