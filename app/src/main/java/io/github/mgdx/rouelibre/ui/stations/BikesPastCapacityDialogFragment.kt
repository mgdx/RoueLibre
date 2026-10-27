package io.github.mgdx.rouelibre.ui.stations

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import io.github.mgdx.rouelibre.core.station.FullStationAnswer
import kotlinx.coroutines.launch

/**
 * That a network takes bikes back at its full stations, said once per network
 * (SPEC §4.1, fourth case; §7.1).
 *
 * The application found it in the feed — stations reading no free dock while
 * holding more bikes than their docks — and reads the network's full stations
 * as taking any bike from then on, its free docks no longer shown. That is an
 * inference, and the rider may know the network's rule better: so the window
 * says what changed and lets them refuse it for this network. **Keeping is
 * the default**, the reading being already in force when the window opens;
 * the choice under the city in the settings changes either answer later.
 *
 * A [DialogFragment] for the reason
 * [io.github.mgdx.rouelibre.ui.ConfirmationDialogFragment] gives: put back
 * when the phone is turned over, where a builder's window would vanish and
 * never be shown again.
 *
 * **Closing it is what records that it was shown** — either button, the back
 * gesture or a tap outside, the last two counting as keeping.
 */
class BikesPastCapacityDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arguments = requireArguments()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                getString(R.string.bikes_past_capacity_title, arguments.getString(NETWORK_NAME)),
            )
            .setMessage(R.string.bikes_past_capacity_body)
            .setPositiveButton(R.string.bikes_past_capacity_keep) { _, _ -> answer(kept = true) }
            .setNegativeButton(R.string.bikes_past_capacity_turn_off) { _, _ ->
                answer(kept = false)
            }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        answer(kept = true)
    }

    /**
     * Writes the answer down, on the **activity's** scope for the reason
     * [StreetBikeIntroDialogFragment] gives: the fragment is destroyed by the
     * very press that answers.
     */
    private fun answer(kept: Boolean) {
        val cityId = requireArguments().getString(CITY_ID) ?: return
        val application = requireActivity().application as RoueLibreApplication
        val preferences = application.container.preferences
        requireActivity().lifecycleScope.launch {
            preferences.setBikesPastCapacityExplained(cityId)
            if (!kept) preferences.setFullStationAnswer(cityId, FullStationAnswer.Never)
        }
    }

    companion object {
        /** The tag the dialog is added to the manager under. */
        const val TAG: String = "bikes-past-capacity"

        private const val CITY_ID = "city-id"
        private const val NETWORK_NAME = "network-name"

        /**
         * Puts the window up for the network [cityId], named [networkName],
         * unless it is already up.
         *
         * **The transaction accepts a state already saved**, as
         * [StreetBikeIntroDialogFragment.explain]'s does: this window follows a
         * reading of the feed, and the screen may have been left meanwhile.
         */
        fun explain(manager: FragmentManager, cityId: String, networkName: String) {
            if (manager.findFragmentByTag(TAG) != null) return
            val dialog = BikesPastCapacityDialogFragment().apply {
                arguments = bundleOf(CITY_ID to cityId, NETWORK_NAME to networkName)
            }
            manager.beginTransaction()
                .setReorderingAllowed(true)
                .add(dialog, TAG)
                .commitAllowingStateLoss()
        }
    }
}
