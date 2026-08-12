package io.github.mgdx.rouelibre.ui.journey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.databinding.FragmentJourneySearchBinding

/**
 * Journey search: from where to where (SPEC §7.3).
 *
 * Two points to designate, each in four ways, and a button to swap them.
 * Nothing is computed here: the screen only gathers what the computation needs,
 * and that happens on the result screen.
 *
 * None of these designations leaves the device, and none is kept: SPEC §8
 * forbids holding on to a destination.
 */
class JourneySearchFragment : Fragment() {

    private var binding: FragmentJourneySearchBinding? = null

    private var origin: JourneyEndpoint? = null
    private var destination: JourneyEndpoint? = null

    private val picker = JourneyEndpointPicker(
        fragment = this,
        onMessage = ::showMessage,
        onPicked = ::accept,
        onLocating = ::showLocating,
    )

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

        // Only when the screen is rebuilt after being destroyed — rotation, a
        // return from the background. Going through the address search destroys
        // the VIEW alone: the fields already filled still live in the fragment,
        // and re-reading them from an absent bundle erased them. The second
        // point then overwrote the first.
        if (savedInstanceState != null) {
            origin = JourneyEndpoint.readFrom(savedInstanceState, STATE_ORIGIN)
            destination = JourneyEndpoint.readFrom(savedInstanceState, STATE_DESTINATION)
            picker.readFrom(savedInstanceState)
        } else {
            // A point received from elsewhere: from another application
            // (SPEC §7.8) or from a station just consulted (SPEC §7.2). Only
            // the other end remains to be filled.
            if (origin == null) origin = JourneyEndpoint.readFrom(arguments, ARGUMENT_ORIGIN)
            if (destination == null) {
                destination = JourneyEndpoint.readFrom(arguments, ARGUMENT_DESTINATION)
            }
        }

        views.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        views.toolbar.navigationContentDescription = getString(R.string.action_back)
        views.origin.setOnClickListener { picker.choose(true, destination?.position) }
        views.destination.setOnClickListener { picker.choose(false, origin?.position) }
        views.swap.setOnClickListener { swap() }
        views.compute.setOnClickListener { openResult() }

        picker.listen(viewLifecycleOwner)
        showEndpoints()
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

    private fun openResult() {
        val from = origin ?: return
        val to = destination ?: return
        parentFragmentManager.beginTransaction()
            .replace(R.id.content, JourneyResultFragment.newInstance(from, to))
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
