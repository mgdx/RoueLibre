package io.github.mgdx.rouelibre.data.location

/**
 * Decides whether the application may put the location permission dialog up
 * of its own accord (SPEC §10).
 *
 * One request belongs to no button: the map's, when it opens, because the
 * point that follows the device is that screen's own subject (SPEC §7.1).
 * Everything else asks on a press. This class keeps that single unprompted
 * request from becoming insistence: it is made at most **once per session**,
 * and **never again once any request has been refused** — the map's or a
 * button's, that session or an earlier one. The refusal is written down for
 * good, because a dialog that returns at the next launch after a "no" is the
 * same insistence with a delay, and it is exactly what a Permissions reviewer
 * reads as asking twice for one refusal.
 *
 * The buttons are untouched by all of this: a press is the user asking, and
 * it is the system, not the application, that stops answering one after a
 * "don't ask again".
 *
 * Granted later, the memory is left as it stands and is simply without
 * effect: an unprompted request is only ever weighed while the permission is
 * missing. The one consequence is accepted — somebody who refused once,
 * granted from a button, then revoked from the system settings is never asked
 * unprompted again, and keeps the buttons.
 *
 * Pure Kotlin: where the refusal is kept is the caller's business, handed in
 * as functions so the rule stays testable on the JVM (SPEC §14).
 *
 * @param isRefusalRemembered whether a refusal has ever been recorded.
 * @param rememberRefusal records one, for good.
 */
class AutomaticLocationRequest(
    private val isRefusalRemembered: suspend () -> Boolean,
    private val rememberRefusal: suspend () -> Unit,
) {

    private var askedThisSession = false

    /**
     * True if the dialog may go up unprompted, and a yes is spent by being
     * given: saying yes counts as having asked, so the next call answers no
     * for the rest of the session whatever became of the dialog.
     */
    suspend fun mayAskUnprompted(): Boolean {
        if (askedThisSession || isRefusalRemembered()) return false
        askedThisSession = true
        return true
    }

    /** Records a refusal, wherever it was pronounced. */
    suspend fun noteRefused() {
        rememberRefusal()
    }
}
