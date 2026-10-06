package io.github.mgdx.rouelibre.data.cities

import io.github.mgdx.rouelibre.core.DataError
import io.github.mgdx.rouelibre.core.config.CityCatalogue

/**
 * What came of asking the publication host for the catalogue again.
 *
 * Three answers rather than the [io.github.mgdx.rouelibre.core.Outcome] used
 * everywhere else, because "it has not changed" is neither of the other two: it
 * is the ordinary answer to a conditional request, and the screen has to tell it
 * apart. Publishing a catalogue again measures the data installed for each of
 * its several hundred cities on the disk — work worth doing when the list has
 * really moved, and not when the server has just confirmed it has not.
 */
sealed interface CatalogueRefresh {

    /**
     * The catalogue in force changed, and [catalogue] is the one in force from
     * now on: the one that arrived, or the shipped one when what arrived is
     * older than it and replaced the copy that was in force.
     */
    data class Updated(val catalogue: CityCatalogue) : CatalogueRefresh

    /**
     * The catalogue in force did not change: the host answered `304`, or sent a
     * document older than the shipped catalogue, which outranks it.
     */
    data object Unchanged : CatalogueRefresh

    /**
     * Nothing arrived, for the reason given. The catalogue in force still
     * stands, and the screen keeps showing it.
     */
    data class Failed(val error: DataError) : CatalogueRefresh
}
