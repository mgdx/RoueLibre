package io.github.mgdx.rouelibre.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The rider's own bike changes no journey (SPEC §6, §7.6).
 *
 * A pedal-assist bike is quicker in the real world, and this application still
 * announces only what the routing engine traced: the ride runs over the same
 * graph with the same profile whatever kind was declared, so the same pair of
 * points must come back with the same track and the same minutes. It is a
 * decision and not an omission — a speed added for the motor would be a figure
 * nobody measured.
 *
 * What holds it is structural rather than a rule anybody has to remember:
 * [OwnBikeKind] lives in the application's settings, and the `core` module —
 * where the algorithm of §6, the router and the planner live — does not depend
 * on the application and could not read it. This test guards the day somebody
 * moves the type "for symmetry" with `WantedBikeKind`, which is a kind asked of
 * the network and does legitimately reach the algorithm.
 */
class OwnBikeKindReachesNoJourneyTest {

    @Test
    fun `the business module never mentions the rider's own bike`() {
        val mentions = coreSources()
            .filter { it.readText().contains("OwnBikeKind") }
            .map { it.name }
            .sorted()
        assertEquals(
            "the rider's own bike must not reach the journey algorithm",
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
