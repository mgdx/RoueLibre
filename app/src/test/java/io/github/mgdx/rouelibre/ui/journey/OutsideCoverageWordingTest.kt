package io.github.mgdx.rouelibre.ui.journey

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * What the planner says when an end of the journey falls outside the installed
 * data (SPEC §4, §7.8, §14).
 *
 * One sentence used to answer for both ends — "This point lies outside the area
 * covered by the installed data" — and the reader took "this point" to mean the
 * end they had just chosen, the arrival. It is almost always the other one: a
 * journey prepared before setting off departs from the device's position, which
 * is still elsewhere. The three sentences below are only worth having as long as
 * they say three different things, and a translation pass that copies one over
 * another puts the defect straight back.
 *
 * The files are read from the disk, as `OfflineWordingTest` and `LocalesTest`
 * read them: what is checked is what the application will be built with, and no
 * Android runtime is involved (SPEC §14).
 */
class OutsideCoverageWordingTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    /**
     * The two languages this repository is written and read in. The other
     * thirty are filled by the translation pass, and are held to this by
     * `tools/check_translations.py`.
     */
    private val authored = listOf("values", "values-fr")

    @Test
    fun `each end of the journey has a refusal of its own`() {
        authored.forEach { folder ->
            val sentences = REFUSALS.map { stringOf(folder, it) }
            assertEquals(
                "$folder answers two ends with one sentence",
                REFUSALS.size,
                sentences.toSet().size,
            )
        }
    }

    /**
     * The whole point of the split: the sentence for a departure outside the
     * area must not be the demonstrative that started this. "This point" names
     * nothing, and the reader supplies the wrong end.
     */
    @Test
    fun `the refusals name an end rather than pointing at one`() {
        val demonstratives = mapOf(
            "values" to "this point",
            "values-fr" to "ce point",
        )
        demonstratives.forEach { (folder, vague) ->
            NAMED_REFUSALS.forEach { name ->
                assertEquals(
                    "$folder still says \"$vague\" in $name",
                    false,
                    stringOf(folder, name).contains(vague, ignoreCase = true),
                )
            }
        }
    }

    private fun stringOf(folder: String, name: String): String {
        val file = File(resources, "$folder/strings.xml")
        val declaration = Regex("""<string name="$name">(.*?)</string>""")
            .find(file.readText())
        checkNotNull(declaration) { "$name is not declared in ${file.path}" }
        return declaration.groupValues[1]
    }

    private companion object {

        /** The refusals that name the end at fault. */
        val NAMED_REFUSALS = listOf(
            "journey_outside_coverage_start",
            "journey_outside_coverage_destination",
            "journey_outside_coverage_both",
        )

        /**
         * Those, plus the one kept for the case where the end is unknown — the
         * routing engine refusing a whole leg without saying which of its two
         * points it stumbled on.
         */
        val REFUSALS = NAMED_REFUSALS + "journey_outside_coverage"
    }
}
