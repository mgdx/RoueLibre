package io.github.mgdx.rouelibre.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The rider's own bike is a preference, and it stays on this side (SPEC §7.6).
 *
 * Until 17 August 2026 this test guarded a stronger claim: that the declared
 * kind changed no journey at all, held structurally by the fact that the `core`
 * module could not see the type. That claim fell with the decision it protected
 * — a pedal-assist bike really is quicker, and the ride is now traced with a
 * profile that describes it.
 *
 * **What the test guards is what remains true, and it is not a leftover.** The
 * algorithm needs one thing about the bike, `RiddenBike`, and that lives in
 * `core`. [OwnBikeKind] is something else: a question put to the rider, with a
 * wording, a default and a storage key of its own, none of which the algorithm
 * has any business knowing. SPEC §7.6 requires it to stay distinguishable from
 * the kind asked of the network — two questions, two names, two keys — and the
 * day either is moved into `core` "for symmetry" that distinction starts eroding
 * from the bottom.
 */
class OwnBikeKindReachesNoJourneyTest {

    @Test
    fun `the business module never mentions the rider's own bike`() {
        val mentions = coreSources()
            .filter { it.readText().contains("OwnBikeKind") }
            .map { it.name }
            .sorted()
        assertEquals(
            "the rider's own bike is a preference, not a notion of the algorithm",
            emptyList<String>(),
            mentions,
        )
    }

    @Test
    fun `the business module never reads the setting either`() {
        // The type could be kept out and the stored word read in anyway. The
        // key is the other end of the same thread.
        val mentions = coreSources()
            .filter { it.readText().contains("own_bike_kind") }
            .map { it.name }
            .sorted()
        assertEquals(emptyList<String>(), mentions)
    }

    private fun coreSources(): List<File> {
        val root = listOf(File("../core/src/main"), File("core/src/main"))
            .firstOrNull(File::isDirectory)
        checkNotNull(root) { "core sources not found from ${File(".").absolutePath}" }
        val sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        check(sources.isNotEmpty()) { "no Kotlin source found under $root" }
        return sources
    }
}
