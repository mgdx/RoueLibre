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
 * A list of choices, put up so that it survives the phone turning over.
 *
 * [ConfirmationDialogFragment]'s reasoning applied to the other shape of
 * question this application asks. A dialog built where it is asked belongs to
 * no fragment manager, so nothing puts it back when the activity is rebuilt:
 * the language chooser and the "which leg are you setting off on" menu both
 * vanished without a word on a rotation, exactly as "Delete this data?" did.
 *
 * The rows travel in the arguments, so the rebuilt dialog offers the same list
 * in the same order, and **the row ticked travels with them** — the language
 * chooser shows the language in service, and a list that came back with
 * nothing ticked would say the interface follows no language at all.
 *
 * The answer is the **index** chosen, sent back as a fragment result. What the
 * index has to be read against is the caller's business: a list computed the
 * same way every time is derived again, and one that could not be is carried
 * in the payload.
 *
 * Leaving without choosing — the back gesture, a tap outside, the second
 * button where there is one — sends nothing, as a builder's dialog did.
 */
class ChoiceDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val asked = requireArguments()
        val labels = checkNotNull(asked.getStringArray(LABELS))
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(asked.getInt(TITLE))
        val ticked = asked.getInt(TICKED)
        if (ticked == NOTHING_TICKED) {
            // A plain list, which closes on the press by itself.
            builder.setItems(labels) { _, chosen -> answer(chosen) }
        } else {
            // A list with one of its rows already the answer: it shows radio
            // buttons, and it does not close on a press of its own accord.
            builder.setSingleChoiceItems(labels, ticked) { dialog, chosen ->
                dialog.dismiss()
                answer(chosen)
            }
        }
        val dismiss = asked.getInt(DISMISS)
        if (dismiss != NO_BUTTON) builder.setNegativeButton(dismiss, null)
        return builder.create()
    }

    private fun answer(chosen: Int) {
        val asked = requireArguments()
        val answer = Bundle(asked.getBundle(PAYLOAD) ?: Bundle.EMPTY)
        answer.putInt(CHOSEN, chosen)
        setFragmentResult(checkNotNull(asked.getString(REQUEST_KEY)), answer)
    }

    companion object {
        private const val TITLE = "title"
        private const val LABELS = "labels"
        private const val TICKED = "ticked"
        private const val DISMISS = "dismiss"
        private const val PAYLOAD = "payload"
        private const val REQUEST_KEY = "request-key"
        private const val CHOSEN = "chosen"

        /**
         * What [ask] is given when no row of the list is the current answer.
         *
         * The list is then a plain one rather than a set of radio buttons: a
         * menu of three legs is not a setting with one of them in force.
         */
        const val NOTHING_TICKED = -1

        /** What [ask] is given when the list is left by the back gesture alone. */
        const val NO_BUTTON = 0

        /**
         * Puts the list up, in [manager] so that [manager] can put it back.
         *
         * The transaction accepts a state already saved for the reason
         * [ConfirmationDialogFragment.ask] gives: losing a list nobody is
         * looking at is the mild half of that bargain, and crashing is not.
         *
         * @param requestKey what the answer will be delivered under, and what
         *   the dialog is tagged with. Unique within [manager].
         * @param ticked the row already in force, or [NOTHING_TICKED].
         * @param dismiss the label of the button that leaves without choosing,
         *   or [NO_BUTTON] where the list offers none.
         * @param payload what the caller will need in order to read the index
         *   it gets back, and may no longer have to hand by then.
         */
        fun ask(
            manager: FragmentManager,
            requestKey: String,
            @StringRes title: Int,
            labels: List<String>,
            ticked: Int = NOTHING_TICKED,
            @StringRes dismiss: Int = R.string.action_cancel,
            payload: Bundle = Bundle(),
        ) {
            val question = ChoiceDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(REQUEST_KEY, requestKey)
                    putInt(TITLE, title)
                    putStringArray(LABELS, labels.toTypedArray())
                    putInt(TICKED, ticked)
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
         * Registered where the screen is built and not where the list is put
         * up: after a rebuild the list is already back, and its answer would
         * otherwise arrive with nobody listening for it.
         *
         * @param chose told which row was chosen, and handed back the payload
         *   the list was put up with.
         */
        fun onAnswer(
            manager: FragmentManager,
            owner: LifecycleOwner,
            requestKey: String,
            chose: (chosen: Int, payload: Bundle) -> Unit,
        ) {
            manager.setFragmentResultListener(requestKey, owner) { _, answer ->
                chose(answer.getInt(CHOSEN), answer)
            }
        }
    }
}
