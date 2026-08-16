package io.github.mgdx.rouelibre.core.data

/**
 * Decides whether a dataset transfer may run over the connection in use
 * (SPEC §4.4).
 *
 * What a network bills is a question only Android can answer; what to do about
 * it is a rule, and a rule is worth a test. So the rule lives here, knowing
 * nothing of Android: it is handed two booleans and answers one.
 *
 * **The exemption covers one transfer, never the setting.** Somebody in a hotel
 * with no Wi-Fi must be able to install their city, and what they agree to is
 * that download. It is spent when the transfer ends, so the next one asks again.
 */
public class MeteredTransferGate {

    private var exempted: Boolean = false

    /** Whether the transfer under way, or about to start, has been exempted. */
    public val isExempted: Boolean
        get() = exempted

    /** Lets one transfer run whatever the connection bills. */
    public fun exemptOneTransfer() {
        exempted = true
    }

    /**
     * Whether a transfer may run right now.
     *
     * The same question decides whether a transfer starts and whether one under
     * way carries on: a Wi-Fi lost for a mobile plan in the middle of a
     * gigabyte is the moment this rule exists for.
     *
     * @param unmeteredOnly what the setting says (SPEC §7.6).
     * @param metered whether the connection in use bills what goes over it.
     */
    public fun mayRun(unmeteredOnly: Boolean, metered: Boolean): Boolean =
        exempted || !unmeteredOnly || !metered

    /** Notes that the transfer is over, spending the exemption with it. */
    public fun transferEnded() {
        exempted = false
    }
}
