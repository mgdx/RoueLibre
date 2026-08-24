package io.github.mgdx.rouelibre.core.message

/**
 * What a message put on the screen's single banner is about (SPEC §7.8).
 *
 * The application shows one banner at a time and a new one takes the place of
 * the one before it rather than piling on top of it, so two messages raised
 * within the same second are a contest and not a queue. This is the ladder
 * that settles it: the entries are declared **from the one most easily given
 * up to the one that has to be read**, which is the order [takesTheBanner]
 * compares them in.
 */
public enum class MessageSubject {

    /**
     * How the availability refresh went — no connection, a feed that answered
     * badly.
     *
     * The one that gives way. It describes the age of the counters and not the
     * gesture the user has just made; the map states that age on its own
     * freshness line and goes on stating it (SPEC §7.1), and the refresh comes
     * round again by itself, so this message is deferred rather than lost.
     */
    Refresh,

    /**
     * The answer to something the user has just done — a place received from
     * another application that lies outside the covered area, a text matching
     * no address, an index that is not installed.
     *
     * The one that has to be read: nothing else on the screen carries it, and
     * it is the answer the user is waiting for.
     */
    Answer,
}

/**
 * Whether a message about [incoming] may take the banner from the message
 * about [showing] — `null` when the banner is free.
 *
 * Working offline is the application's ordinary state (SPEC §2 C5), so the
 * failed refresh is the message most apt to turn up at the wrong moment: a
 * place received from another application while the phone had no network
 * opened the map, said the place was out of reach, and had that wiped a few
 * hundred milliseconds later by "no connection", which was then all the user
 * ever saw.
 *
 * Two messages on the same subject still replace one another: of two answers,
 * as of two refreshes, the later one is the truer one.
 */
public fun takesTheBanner(showing: MessageSubject?, incoming: MessageSubject): Boolean =
    showing == null || incoming >= showing
