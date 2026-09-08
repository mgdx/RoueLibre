package io.github.mgdx.rouelibre.ui.stations

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.mgdx.rouelibre.R
import io.github.mgdx.rouelibre.RoueLibreApplication
import kotlinx.coroutines.launch

/**
 * What the bikes outside stations are, said once (SPEC §7.6, §7.2.1).
 *
 * **It informs and does not confirm.** The switch is already on when this
 * opens, one button closes it, and nothing here can refuse anything: the rule
 * of §7.6 against guarding a reversible press with a question stands, since
 * turning the switch off is one press and a dialog protecting it would cost
 * more than it protects.
 *
 * Three short paragraphs and no more, because SPEC §7.9 has already settled
 * that a modal window is no place for dense text. What they carry is the one
 * thing the application cannot read from any feed: whether a bike standing in
 * a street may be taken, which is the network's rule, and which GBFS does not
 * publish — `geofencing_zones` and `return_constraint` are served by 51 and 31
 * feeds of 405, and by none of the nextbike networks (SPEC §4.1).
 *
 * It is a [DialogFragment] rather than a builder's window for the reason
 * [io.github.mgdx.rouelibre.ui.ConfirmationDialogFragment] gives at length: a
 * window belonging to no fragment manager vanishes without a word when the
 * phone is turned over, and this one would then never be shown again.
 *
 * **Closing it is what records that it was read** — the button, the back
 * gesture and a tap outside alike, which is why both ways are wired below.
 * Turning the phone over is not one of them: the dialog is rebuilt and the
 * reader has not finished with it.
 */
class StreetBikeIntroDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.street_bike_intro_title)
            // The message of an alert dialog is laid in a scrolling view by the
            // framework itself, which is what carries these three paragraphs at
            // the largest text sizes rather than pushing the button off the
            // screen.
            .setMessage(R.string.street_bike_intro_body)
            .setPositiveButton(R.string.street_bike_intro_dismiss) { _, _ -> noteRead() }
            .create()

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        noteRead()
    }

    /**
     * Writes down that the explanation has been shown.
     *
     * On the **activity's** scope and not on this fragment's: closing the
     * dialog destroys the fragment, and a coroutine cancelled halfway would
     * leave the settings file unwritten and the dialog waiting to appear a
     * second time.
     */
    private fun noteRead() {
        val application = requireActivity().application as RoueLibreApplication
        requireActivity().lifecycleScope.launch {
            application.container.preferences.setStreetBikesExplained()
        }
    }

    companion object {
        /** The tag the dialog is added to the manager under. */
        const val TAG: String = "street-bikes-explained"

        /**
         * Puts the explanation up, in [manager] so that [manager] can put it
         * back.
         *
         * **The transaction accepts a state already saved**, where
         * [DialogFragment.show] would throw: this window follows a setting
         * being written, and losing an explanation nobody is looking at is the
         * mild half of that bargain where crashing on it is not.
         */
        fun explain(manager: FragmentManager) {
            manager.beginTransaction()
                .setReorderingAllowed(true)
                .add(StreetBikeIntroDialogFragment(), TAG)
                .commitAllowingStateLoss()
        }
    }
}
