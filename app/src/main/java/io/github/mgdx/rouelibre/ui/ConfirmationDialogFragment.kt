package io.github.mgdx.rouelibre.ui

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.mgdx.rouelibre.R

/**
 * A question with two answers, asked so that it survives the phone turning
 * over.
 *
 * **Why it exists at all.** These questions used to be built where they were
 * asked, `MaterialAlertDialogBuilder(...).show()`, which puts up a window
 * belonging to nothing: no fragment manager knows about it, so nothing puts it
 * back when the activity is rebuilt. "Delete this data?" therefore vanished
 * without a word the moment somebody turned their phone over while thinking
 * about it, and they found the list of cities again with neither a deletion
 * nor a cancellation to show for it. Everything else here survives a rotation
 * — the welcome sequence, the list, a station's card, a journey — and a
 * question ought to as well.
 *
 * A [DialogFragment] is put back by the fragment manager it was shown in, out
 * of the arguments it was given: title, sentence and button labels all travel
 * in the [Bundle], so the rebuilt dialog asks exactly the same question.
 *
 * **The answer comes back as a fragment result**, and that is the other half
 * of it: a listener handed over at the call would be a listener held by an
 * object the rebuild has thrown away, which is the very defect being fixed.
 * The manager delivers the result to whoever is registered for the key when
 * the screen is next started, whether or not that is the same instance.
 *
 * What a caller has to carry across the rebuild — which city, which dataset —
 * travels in the payload and comes back with the answer, so nothing has to be
 * found again in a list that may have been read afresh meanwhile.
 *
 * Leaving without answering — the back gesture, a tap outside — sends nothing,
 * exactly as a builder's dialog did nothing when it was cancelled.
 */
class ConfirmationDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val asked = requireArguments()
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(asked.getInt(TITLE))
            .setMessage(asked.getCharSequence(MESSAGE))
            .setPositiveButton(asked.getInt(CONFIRM)) { _, _ -> answer(confirmed = true) }
            .setNegativeButton(asked.getInt(DISMISS)) { _, _ -> answer(confirmed = false) }
            .create()
    }

    private fun answer(confirmed: Boolean) {
        val asked = requireArguments()
        val answer = Bundle(asked.getBundle(PAYLOAD) ?: Bundle.EMPTY)
        answer.putBoolean(CONFIRMED, confirmed)
        setFragmentResult(checkNotNull(asked.getString(REQUEST_KEY)), answer)
    }

    companion object {
        private const val TITLE = "title"
        private const val MESSAGE = "message"
        private const val CONFIRM = "confirm"
        private const val DISMISS = "dismiss"
        private const val PAYLOAD = "payload"
        private const val REQUEST_KEY = "request-key"
        private const val CONFIRMED = "confirmed"

        /**
         * Puts the question up, in [manager] so that [manager] can put it back.
         *
         * **The transaction accepts a state already saved**, where
         * [DialogFragment.show] would throw. Three of these questions are
         * asked at the end of a wait — a position fixed, a catalogue read, a
         * transfer refused — and the screen may have been left in the
         * meantime. Losing a question nobody is looking at is the mild half of
         * that bargain; crashing on it is not.
         *
         * @param requestKey what the answer will be delivered under, and what
         *   the dialog is tagged with. Unique within [manager].
         * @param message written out by the caller: most of these name a city,
         *   a dataset or a size, and the sentence is what travels rather than
         *   the pieces it was made of.
         * @param payload what the caller will need in order to act on the
         *   answer, and may no longer have to hand by then.
         */
        fun ask(
            manager: FragmentManager,
            requestKey: String,
            @StringRes title: Int,
            message: CharSequence,
            @StringRes confirm: Int,
            @StringRes dismiss: Int = R.string.action_cancel,
            payload: Bundle = Bundle(),
        ) {
            val question = ConfirmationDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(REQUEST_KEY, requestKey)
                    putInt(TITLE, title)
                    putCharSequence(MESSAGE, message)
                    putInt(CONFIRM, confirm)
                    putInt(DISMISS, dismiss)
                    putBundle(PAYLOAD, payload)
                }
            }
            manager.beginTransaction()
                .setReorderingAllowed(true)
                .add(question, requestKey)
                .commitAllowingStateLoss()
        }

        /**
         * Listens for the answer to [requestKey], for as long as [owner] lives.
         *
         * Registered where the screen is built and not where the question is
         * put: after a rebuild the question is already back up, and its answer
         * would otherwise arrive with nobody listening for it.
         *
         * @param answered told whether the question was confirmed, and handed
         *   back the payload it was asked with.
         */
        fun onAnswer(
            manager: FragmentManager,
            owner: LifecycleOwner,
            requestKey: String,
            answered: (confirmed: Boolean, payload: Bundle) -> Unit,
        ) {
            manager.setFragmentResultListener(requestKey, owner) { _, answer ->
                answered(answer.getBoolean(CONFIRMED), answer)
            }
        }
    }
}
