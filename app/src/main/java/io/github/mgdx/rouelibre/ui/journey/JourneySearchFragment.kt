package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.station.WantedBikeKind
import io.github.mgdx.rouelibre.data.OwnBikeKind
import io.github.mgdx.rouelibre.databinding.FragmentJourneySearchBinding
import io.github.mgdx.rouelibre.ui.BikeFleet
import io.github.mgdx.rouelibre.ui.BikeGlyphs
import io.github.mgdx.rouelibre.ui.withBikeFleet
import io.github.mgdx.rouelibre.ui.withFleet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Journey search: from where to where (SPEC §7.3).
 *
 * Two points to designate, each in four ways, a button to swap them, and a
 * switch saying whether the bike is the network's or the user's own.
 * Nothing is computed here: the screen only gathers what the computation needs,
 * and that happens on the result screen.
 *
 * The switch is the one thing on this screen that outlives it: what somebody
 * rides is a fact about them, where the two points are a fact about one trip
 * (SPEC §7.3, §8).
 *
 * None of these designations leaves the device, and none is kept: SPEC §8
 * forbids holding on to a destination.
 */
class JourneySearchFragment : Fragment() {

    private var binding: FragmentJourneySearchBinding? = null

    private var origin: JourneyEndpoint? = null
    private var destination: JourneyEndpoint? = null

    /**
     * Whether the journey is to be ridden on the user's own bike (SPEC §7.3).
     *
     * Held here as well as on the switch, because the sentence and the drawing
     * under it are laid out from it before the switch has been filled in.
     */
    private var usesOwnBike = false

    /**
     * What the network served lends, so the illustration can be drawn again.
     *
     * Two things decide the drawing — the fleet, read from disk a beat after
     * the screen, and the switch, pressed whenever the user likes — and either
     * can move after the other. Each keeps what it knows so the drawing can be
     * laid from both.
     */
    private var fleet = BikeFleet.Mechanical

    /**
     * The kind of bike wanted, or `null` for no kind at all (SPEC §7.3).
     *
     * Held here as well as on the buttons, because it is read back when the
     * journey is asked for, and because the selector may not be on screen: a
     * choice made in a mixed conurbation stays remembered while a mechanical one
     * is being served, and is found again on the way back.
     */
    private var wantedBikeKind: WantedBikeKind? = null

    /**
     * What the rider said their **own** bike is, or `null` if they have not
     * (SPEC §7.6).
     *
     * Not [wantedBikeKind] above, and never to be read for it: that one is a
     * kind asked of the network and narrows the stations §6 may choose, this one
     * is a fact about the rider and reaches only the drawing under the fields.
     * It is declared in the settings rather than here, being asked once and not
     * once a journey, and it is followed rather than read once so a bike
     * declared while this screen sits on the back stack reaches the drawing.
     */
    private var ownBikeKind: OwnBikeKind? = null

    /**
     * Whether the network in service really lends both kinds (SPEC §15).
     *
     * It decides whether the selector exists at all. Read from a flow, so a
     * reading landing after the screen — or a change of city — puts it right
     * without rebuilding anything.
     */
    private var lendsBothKinds = false

    private val preferences
        get() = (requireActivity().application as RoueLibreApplication).container.preferences

    private val picker = JourneyEndpointPicker(
        fragment = this,
        onMessage = ::showMessage,
        onPicked = ::accept,
        onLocating = ::showLocating,
    )

    /**
     * Takes the two points back, before anything can read or rewrite them.
     *
     * Here rather than in `onViewCreated`, because a screen left on the back
     * stack — this one, while the result is being read — has no view rebuilt
     * when the activity is: only this runs. Read from the view's arrival, the
     * two points stayed empty through the whole recreation, and the next save
     * wrote that emptiness over the state that still held them. Coming back
     * from the result then landed on a blank form.
     *
     * A bundle that is null is a screen being created for the first time, and
     * only then are the arguments read: a point received from another
     * application (SPEC §7.8) or from a station just consulted (SPEC §7.2),
     * with the other end left to fill. This runs once for the fragment, so
     * nothing here can overwrite a point picked since.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            origin = JourneyEndpoint.readFrom(savedInstanceState, STATE_ORIGIN)
            destination = JourneyEndpoint.readFrom(savedInstanceState, STATE_DESTINATION)
            picker.readFrom(savedInstanceState)
            return
        }
        origin = JourneyEndpoint.readFrom(arguments, ARGUMENT_ORIGIN)
        destination = JourneyEndpoint.readFrom(arguments, ARGUMENT_DESTINATION)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentJourneySearchBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = checkNotNull(binding)

        views.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)
        views.origin.setOnClickListener { picker.choose(true, destination?.position) }
        views.destination.setOnClickListener { picker.choose(false, origin?.position) }
        views.swap.setOnClickListener { swap() }
        views.compute.setOnClickListener { openResult() }

        picker.listen(viewLifecycleOwner)
        showEndpoints()
        // The illustration draws the stations of the network served: bearing a
        // bolt where that network lends pedal-assist bikes (SPEC §15).
        withBikeFleet { lent ->
            fleet = lent
            showShape()
        }
        // The selector only exists where both kinds are lent, and that answer
        // arrives from a flow: a first reading off disk, then the count made on
        // every refresh (SPEC §4.1). Following it rather than asking once is
        // what lets the selector appear, or go, without the screen being rebuilt.
        withFleet { lent ->
            lendsBothKinds = lent?.isMixed == true
            showBikeKindSelector()
        }
        // And the rider's own bike bears the bolt where they said it does
        // (SPEC §7.6). A separate reading from the one above, on purpose: what
        // the network lends and what the rider owns answer different questions.
        withOwnBikeKind { declared ->
            ownBikeKind = declared
            showShape()
        }
        setUpOwnBike()
        setUpBikeKind()
    }

    /**
     * Sets the switch up on the choice the user made last time (SPEC §7.3).
     *
     * The stored value is read once, when the screen is built: it is written
     * from here and nowhere else, so nothing can change it behind this screen's
     * back. The switch is only listened to afterwards, or filling it in would
     * be taken for a press and write back what was just read. Until the read
     * lands — a few milliseconds off disk — the screen says the station
     * journey, which is what the switch says at rest.
     */
    private fun setUpOwnBike() {
        viewLifecycleOwner.lifecycleScope.launch {
            usesOwnBike = preferences.usesOwnBike.first()
            val current = binding ?: return@launch
            current.ownBike.isChecked = usesOwnBike
            showMode()
            current.ownBike.setOnCheckedChangeListener { _, isChecked ->
                usesOwnBike = isChecked
                showMode()
                // Kept for the next journey, and for the next launch: owning a
                // bike is a fact about the person, not about this trip.
                viewLifecycleOwner.lifecycleScope.launch {
                    preferences.setUsesOwnBike(isChecked)
                }
            }
        }
    }

    /**
     * Sets the selector up on the kind chosen last time (SPEC §7.3).
     *
     * Read once when the screen is built, and written from here alone, for the
     * reasons [setUpOwnBike] gives. The buttons are listened to only after the
     * stored answer has been put on them, or checking one would be taken for a
     * press and write back what was just read.
     */
    private fun setUpBikeKind() {
        viewLifecycleOwner.lifecycleScope.launch {
            wantedBikeKind = preferences.wantedBikeKind.first()
            val current = binding ?: return@launch
            current.bikeKind.check(buttonOf(wantedBikeKind))
            showBikeKindSelector()
            current.bikeKind.addOnButtonCheckedListener { _, checkedId, isChecked ->
                // Every change fires twice — the button left, then the one
                // taken — and only the second says what was chosen.
                if (!isChecked) return@addOnButtonCheckedListener
                wantedBikeKind = kindOf(checkedId)
                // Kept for the next journey and the next launch: what somebody
                // wants to ride is a fact about them, not about this trip.
                viewLifecycleOwner.lifecycleScope.launch {
                    preferences.setWantedBikeKind(wantedBikeKind)
                }
            }
        }
    }

    /**
     * Shows the selector where it means something, and hides it elsewhere.
     *
     * Two things take it away, and neither greys it out: a network lending one
     * kind, which could not satisfy the choice (SPEC §7.2), and one's own bike,
     * which looks at no station at all. Offering a choice nobody can collect is
     * a promise the application must not make.
     */
    private fun showBikeKindSelector() {
        val views = binding ?: return
        views.bikeKind.isVisible = lendsBothKinds && !usesOwnBike
    }

    /** The button standing for a kind, the leftmost one asking for nothing. */
    private fun buttonOf(kind: WantedBikeKind?): Int = when (kind) {
        null -> R.id.bike_kind_any
        WantedBikeKind.Mechanical -> R.id.bike_kind_mechanical
        WantedBikeKind.Electric -> R.id.bike_kind_electric
    }

    /** What a checked button asks for, `null` being a choice that asks nothing. */
    private fun kindOf(buttonId: Int): WantedBikeKind? = when (buttonId) {
        R.id.bike_kind_mechanical -> WantedBikeKind.Mechanical
        R.id.bike_kind_electric -> WantedBikeKind.Electric
        else -> null
    }

    /** Says, in words and in the drawing, what kind of journey is being asked for. */
    private fun showMode() {
        val views = binding ?: return
        views.hint.setText(
            if (usesOwnBike) R.string.journey_hint_own_bike else R.string.journey_hint,
        )
        showBikeKindSelector()
        showShape()
    }

    /**
     * The illustration of the journey being asked for.
     *
     * On one's own bike it holds no station, so what the network lends says
     * nothing about it — but what the rider said they own says everything, and
     * the bolt comes from there instead (SPEC §7.6).
     */
    private fun showShape() {
        val views = binding ?: return
        views.shape.setImageResource(
            if (usesOwnBike) {
                OwnBikeGlyphs.journeyShape(ownBikeKind)
            } else {
                BikeGlyphs.journeyShape(fleet)
            },
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        origin?.writeTo(outState, STATE_ORIGIN)
        destination?.writeTo(outState, STATE_DESTINATION)
        picker.writeTo(outState)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun accept(endpoint: JourneyEndpoint, isOrigin: Boolean) {
        if (isOrigin) origin = endpoint else destination = endpoint
        showEndpoints()
    }

    /**
     * Shows, in the field itself, that the position is being looked for.
     *
     * The end of the wait restores what the field said: the point found
     * overwrites it a moment later, and a failed search must not leave the
     * screen claiming to be still searching.
     */
    private fun showLocating(isOrigin: Boolean, searching: Boolean) {
        val views = binding ?: return
        if (!searching) {
            showEndpoints()
            return
        }
        val field = if (isOrigin) views.origin else views.destination
        field.setText(R.string.journey_locating)
    }

    private fun swap() {
        val previousOrigin = origin
        origin = destination
        destination = previousOrigin
        showEndpoints()
    }

    private fun showEndpoints() {
        val views = binding ?: return
        views.origin.text = origin?.label ?: getString(R.string.journey_origin_empty)
        views.destination.text = destination?.label
            ?: getString(R.string.journey_destination_empty)
        views.compute.isEnabled = origin != null && destination != null
    }

    /**
     * Opens the result for what has been composed here.
     *
     * The kind asked for goes with it only where it can be honoured: on one's
     * own bike no station is chosen, and a network lending one kind has nothing
     * to filter — passing it anyway would leave a journey to be refused for a
     * choice the screen was not even showing.
     */
    private fun openResult() {
        val from = origin ?: return
        val to = destination ?: return
        val kind = wantedBikeKind.takeIf { lendsBothKinds && !usesOwnBike }
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, JourneyResultFragment.newInstance(from, to, usesOwnBike, kind))
            .addToBackStack(null)
            .commit()
    }

    private fun showMessage(message: String) {
        val views = binding ?: return
        Snackbar.make(views.root, message, Snackbar.LENGTH_LONG).show()
    }

    companion object {
        private const val STATE_ORIGIN = "origin"
        private const val STATE_DESTINATION = "destination"
        private const val ARGUMENT_DESTINATION = "received-destination"
        private const val ARGUMENT_ORIGIN = "received-origin"

        /**
         * Opens the search, possibly with one end already known.
         *
         * @param origin the point one leaves from, if it is already designated.
         * @param destination the point one goes to, if it is already
         *   designated. Both null gives a blank screen.
         */
        fun newInstance(
            origin: JourneyEndpoint? = null,
            destination: JourneyEndpoint? = null,
        ): JourneySearchFragment = JourneySearchFragment().apply {
            if (origin == null && destination == null) return@apply
            arguments = Bundle().apply {
                origin?.writeTo(this, ARGUMENT_ORIGIN)
                destination?.writeTo(this, ARGUMENT_DESTINATION)
            }
        }
    }
}
