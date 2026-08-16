package io.github.mgdx.rouelibre.core.journey

/**
 * How fast the person this journey is worked out for walks (SPEC §6, §7.6).
 *
 * The algorithm of §6 optimises a **pair** of stations by comparing times, and
 * two of the three legs it compares are walked. A walking pace is therefore not
 * a matter of presentation: it decides which pair wins. Somebody who walks
 * slowly is owed a nearer departure station even at the cost of pedalling
 * further, and that is arithmetic the algorithm cannot guess.
 *
 * **Three named levels, and no figure to type.** A pace is a fact one knows
 * about oneself — one knows one walks slowly, with a suitcase, with a child, on
 * a knee that hurts — where a speed in kilometres an hour is a figure nobody has
 * ever measured about themselves. The levels are named in words for that
 * reason, and no speed is written in the interface: SPEC §6 announces only what
 * it has computed, and a speed shown on a button would be read as a promise
 * about the minutes to come.
 *
 * **Expressed as a factor on a duration, never as a speed.** What is stable from
 * one version to the next is the ratio to the pace the routing engine walks at,
 * not an absolute value that would have to be chased every time the engine's
 * model changes. The reference pace is the engine's own: BRouter walks a
 * pedestrian profile under Tobler's hiking function, capped at its default six
 * kilometres an hour, which our profile does not override. Measured over six
 * legs of the Lille graph on 16 August 2026 — 314 m to 10.2 km — it traces
 * between 1.39 and 1.44 m/s, that is **5.0 to 5.2 km/h**, the spread coming from
 * the descents where the function reaches its ceiling.
 *
 * @property id the value written to disk, stable from one release to the next.
 * @property durationFactor what every walking leg's duration is multiplied by.
 *   Only walking legs: the ride is untouched, this setting saying how one walks
 *   and nothing about how one pedals.
 */
public enum class WalkingPace(public val id: String, public val durationFactor: Double) {

    /**
     * A slow walk — about 3.6 km/h, or one metre a second.
     *
     * Forty per cent longer than the reference. It is the pace pedestrian
     * crossing times are designed around, and the one that describes walking
     * with a suitcase, with a small child, or with a leg that no longer does
     * what it used to. Someone walking it is sent to a nearer station, which is
     * the whole point of the setting.
     */
    Slow("slow", 1.40),

    /**
     * The pace the application has always used, and the default.
     *
     * A factor of exactly one: the engine's own estimate, passed on untouched.
     * That is what makes this the value a journey worked out before this setting
     * existed comes back identical under — and what makes an absent or
     * unreadable preference safe to read as this one.
     */
    Normal("normal", 1.00),

    /**
     * A brisk walk — about 6 km/h, or 1.67 m/s.
     *
     * Fifteen per cent shorter than the reference, which is where the literature
     * on urban walking puts a sustained pace. It is deliberately not faster than
     * that: the algorithm holds an optimistic bound on how quickly a walk could
     * possibly be done (see `JourneyPlanner`), and a pace approaching that bound
     * would cost it its meaning.
     */
    Brisk("brisk", 0.85),
    ;

    public companion object {

        /**
         * Reads a stored pace back; anything unknown walks at [Normal].
         *
         * An absent value and an unreadable one give the same answer, and it is
         * the one that changes nothing: a word written by another version, or by
         * a hand, must not silently send somebody to a different station than
         * the one the application would have chosen for them.
         */
        public fun fromId(id: String?): WalkingPace = entries.firstOrNull { it.id == id } ?: Normal
    }
}
