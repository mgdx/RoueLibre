package io.github.mgdx.rouelibre.ui.map

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.mgdx.rouelibre.AppContainer
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.core.config.CityConfiguration
import io.github.mgdx.rouelibre.core.config.CityEntry
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.ui.ConfirmationDialogFragment
import io.github.mgdx.rouelibre.ui.city.CityFragment
import io.github.mgdx.rouelibre.ui.cityLabel
import io.github.mgdx.rouelibre.ui.storage.StorageFragment
import kotlinx.coroutines.launch

/**
 * What the main map says to somebody standing outside the city it serves, and
 * the change of city it offers them (SPEC §7.1, §15.1).
 *
 * The camera is held inside the city's box, so "locate me" would stop at the
 * edge nearest the user and show that as their position — a point on a street
 * they are a hundred kilometres from, with nothing saying so. What the moment
 * calls for is the other city, not a framing; this is where it is offered,
 * accepted and turned down.
 *
 * @param fragment the map screen, whose dialogues and back stack these are.
 * @param container the application's container, while the screen is attached.
 * @param servedCity the city the map is drawn for, as soon as it is known.
 * @param isPicking whether the map was opened to designate a point.
 * @param onMessage says something back on the activity's banner.
 * @param open shows another screen over the map.
 */
internal class CityElsewhereOffer(
    private val fragment: Fragment,
    private val container: () -> AppContainer,
    private val servedCity: () -> CityConfiguration?,
    private val isPicking: () -> Boolean,
    private val onMessage: (CharSequence) -> Unit,
    private val open: (Fragment) -> Unit,
) {

    /**
     * Tells the user their position is off this map, and offers the way out.
     *
     * The way out is the network of the conurbation they are actually in, when
     * the catalogue knows one and its data is published — the same offer
     * §15.1 makes on opening the application, asked for here rather than
     * volunteered. Failing that, the city list, which is where one changes map.
     *
     * The catalogue read is the one already on the device: no request goes out,
     * and the position serves this single question before being forgotten
     * (SPEC §2, C3).
     *
     * A city already offered and turned down is not offered again: the press
     * is answered in one line instead. Reopening the dialogue on every press
     * was what made "locate me" unusable to anybody who meant to keep the city
     * they had chosen.
     */
    suspend fun sayWeAreElsewhere(position: Coordinates) {
        val served = servedCity() ?: return
        val context = fragment.requireContext()
        val servedLabel = context.cityLabel(served.network.displayName, served.network.city)
        // Designating a point is composing a journey in the city served:
        // changing map there would throw away the endpoint already chosen. All
        // that is left to do is say where we are.
        if (isPicking()) {
            onMessage(context.getString(R.string.map_outside_city_brief, servedLabel))
            return
        }
        val here = cityAround(position)
        // Asked before the answer is chosen: standing outside the network one
        // declined is what forgets that refusal, whatever is said next.
        val mayPropose = container().rememberCityProposal(here?.id)
        when (elsewhereAnswer(cityHereKnown = here != null, mayPropose = mayPropose)) {
            // Where we are is still worth saying: the press asked a question,
            // and silence would read as a button that does nothing.
            ElsewhereAnswer.SayWhereWeAre ->
                onMessage(context.getString(R.string.map_outside_city_brief, servedLabel))

            ElsewhereAnswer.OfferTheCityList -> ConfirmationDialogFragment.ask(
                manager = fragment.childFragmentManager,
                requestKey = ELSEWHERE_ANSWER,
                title = R.string.map_outside_city_title,
                message = context.getString(R.string.map_outside_city_message, servedLabel),
                confirm = R.string.city_choose,
            )

            ElsewhereAnswer.OfferTheCityHere -> offer(checkNotNull(here))
        }
    }

    /**
     * Collects the answers to the two questions this screen asks.
     *
     * Registered as the screen is built, and not where each question is put:
     * a question the phone turned over on is already back up by then, and its
     * answer would arrive with nobody listening for it.
     *
     * Refusing the city one is standing in is an answer like the other, and is
     * remembered as such: a "no" that lasted only until the screen was rebuilt
     * would put the same question again at the next press.
     */
    fun listenForAnswers() {
        val owner = fragment.viewLifecycleOwner
        ConfirmationDialogFragment.onAnswer(
            fragment.childFragmentManager,
            owner,
            ELSEWHERE_ANSWER,
        ) { confirmed, _ ->
            if (confirmed) open(CityFragment())
        }
        ConfirmationDialogFragment.onAnswer(
            fragment.childFragmentManager,
            owner,
            CITY_HERE_ANSWER,
        ) { confirmed, payload ->
            val cityId = payload.getString(CITY_ID) ?: return@onAnswer
            if (confirmed) {
                serve(cityId, payload.getBoolean(CITY_INSTALLED))
            } else {
                declineCity(cityId)
            }
        }
    }

    /** Offers to serve the network of the place the user stands in. */
    private fun offer(here: CityEntry) {
        val context = fragment.requireContext()
        val installed = container().datasetStore.occupiedBytesOf(here.id) > 0
        ConfirmationDialogFragment.ask(
            manager = fragment.childFragmentManager,
            requestKey = CITY_HERE_ANSWER,
            title = R.string.city_here_title,
            message = context.getString(
                if (installed) R.string.city_here_installed_body else R.string.city_here_body,
                context.cityLabel(here.displayName, here.mainCity),
            ),
            confirm = if (installed) R.string.city_here_use else R.string.city_here_install,
            payload = Bundle().apply {
                putString(CITY_ID, here.id)
                putBoolean(CITY_INSTALLED, installed)
            },
        )
    }

    /**
     * Takes note that the offer was turned down.
     *
     * Kept beyond the session, like the one the application makes on opening: a
     * refusal forgotten at the next launch is a question asked again.
     */
    private fun declineCity(cityId: String) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            container().rememberCityRefusal(cityId)
        }
    }

    /**
     * The network serving where the user stands, if it is worth proposing.
     *
     * A proposal made on its own account is only worth making where the weight
     * can be named, so a city the catalogue announces none for is left out; the
     * city already in service is not a change of map, and is left out too. The
     * list of cities is less strict, because there the user asked.
     */
    private suspend fun cityAround(position: Coordinates): CityEntry? =
        container().cityCatalogueSource.catalogue()
            .suggestionFor(position)
            ?.takeIf { it.hasAnnouncedSize && it.id != servedCity()?.network?.id }

    /**
     * Serves the city the user accepted.
     *
     * Its data already there, the map has everything it needs and is built
     * again on it — the map screen loads its style once and keeps it, so the
     * new city's tiles need a new screen. Otherwise the storage screen takes
     * over: it announces the weight before fetching anything (SPEC §4.4).
     */
    private fun serve(cityId: String, installed: Boolean) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            container().switchToCity(cityId)
            if (installed) {
                fragment.parentFragmentManager.beginTransaction()
                    .replace(R.id.content, MapFragment())
                    .commit()
            } else {
                open(StorageFragment.checkingForUpdates())
            }
        }
    }

    private companion object {
        /** The keys the two questions of this screen answer under. */
        const val ELSEWHERE_ANSWER = "map-outside-city"
        const val CITY_HERE_ANSWER = "map-city-here"

        /** What a question carries across a rebuild about the city it names. */
        const val CITY_ID = "city-id"
        const val CITY_INSTALLED = "city-installed"
    }
}

/** What the map answers somebody standing outside the city it serves. */
internal enum class ElsewhereAnswer {
    /** One line saying where the map is, and no question. */
    SayWhereWeAre,

    /** The question that leads to the list of cities. */
    OfferTheCityList,

    /** The question that offers the network of the place one stands in. */
    OfferTheCityHere,
}

/**
 * Settles what is said to somebody outside the city served, once it is known
 * that the screen is not designating a point.
 *
 * Pure, so that it can be tested on the JVM (SPEC §14).
 *
 * @param cityHereKnown whether the catalogue names a network worth proposing
 *   where the user stands.
 * @param mayPropose whether that network may be proposed — it may not once it
 *   has been turned down.
 */
internal fun elsewhereAnswer(cityHereKnown: Boolean, mayPropose: Boolean): ElsewhereAnswer = when {
    !cityHereKnown -> ElsewhereAnswer.OfferTheCityList
    !mayPropose -> ElsewhereAnswer.SayWhereWeAre
    else -> ElsewhereAnswer.OfferTheCityHere
}
