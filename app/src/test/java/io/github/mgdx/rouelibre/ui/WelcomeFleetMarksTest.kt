package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The three station marks of the fleet page (SPEC §7.9).
 *
 * The page used to show the three marks in one drawing with a paragraph above
 * naming them in order: the reader matched a sentence to a place in a row.
 * Each mark now faces its own sentence, and two things have to hold for that to
 * be worth anything — the sentences must exist in every language the
 * application ships, and they must live where a sentence lives.
 *
 * **Where they live is the point the test is really about.** The layout offers
 * two places: the container that scrolls, and the space a drawing is given
 * below it. That space shrinks as the font grows and goes altogether at the
 * largest sizes, which is right for a decoration and wrong for a sentence
 * carrying the meaning. A future hand moving the marks down there would lose
 * the page at the very font sizes that need it most, on a device nobody tests
 * at ×2.0 — so the placement is asserted here rather than left to be noticed.
 *
 * The files are read from the disk, as `LocalesTest` and `FrenchToneTest` read
 * them: what is checked is what the application will be built with, and no
 * Android runtime is involved (SPEC §14).
 */
class WelcomeFleetMarksTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    private val welcomeLayout by lazy {
        File(resources, "layout/fragment_welcome.xml").readText()
    }

    @Test
    fun `each mark is drawn with the disc the map uses for that fleet`() {
        for ((_, marker) in MARKS) {
            assertTrue(
                "The fleet page draws $marker, the map's own disc, rather than one of its own",
                """android:src="@drawable/$marker"""" in welcomeLayout,
            )
        }
    }

    @Test
    fun `each mark faces the sentence naming what its network lends`() {
        for ((sentence, _) in MARKS) {
            assertTrue(
                "The fleet page shows $sentence",
                """android:text="@string/$sentence"""" in welcomeLayout,
            )
        }
    }

    /**
     * The marks belong to the text that scrolls, not to the space below it that
     * a drawing may give up.
     */
    @Test
    fun `the marks stand inside the container that scrolls`() {
        val scroller = welcomeLayout.indexOf("<androidx.core.widget.NestedScrollView")
        val closed = welcomeLayout.indexOf("</androidx.core.widget.NestedScrollView>")
        assertTrue("The page still has a scrolling container", scroller in 0 until closed)

        for ((sentence, marker) in MARKS) {
            for (declaration in listOf("""@string/$sentence"""", """@drawable/$marker"""")) {
                val at = welcomeLayout.indexOf(declaration)
                assertTrue(
                    "$declaration is placed where a drawing goes, which disappears at the " +
                        "largest font sizes",
                    at in scroller until closed,
                )
            }
        }
    }

    /**
     * The mark is given a size of ours rather than the map's.
     *
     * A compound drawable would be laid out at the resource's intrinsic size,
     * 36dp, which is the size a marker takes among fifty on a map and not the
     * size at which a cog and a bolt are told apart by somebody meeting them
     * for the first time. `lint` prefers the compound drawable and is told not
     * to here; this is the assertion that says why, so the saving is not taken
     * back later by a hand that only sees two views where one would do.
     */
    @Test
    fun `each mark is given the page's own size`() {
        assertEquals(
            "Each mark is sized by the page, not by the drawable",
            MARKS.size * 2,
            """@dimen/welcome_mark"""".toRegex().findAll(welcomeLayout).count(),
        )
    }

    /**
     * The text hangs from the title on the fleet page as on the others.
     *
     * The container and the drawing under it constrain one another, so they
     * form a chain, and a spread chain ignores the bias — which is the whole
     * reason a bias of 0 can be written on the container without moving the
     * three pages that do carry a drawing. Break the chain and that reasoning
     * goes with it, silently, on pages this test is the only reader of. So
     * both halves are pinned: the bias, and the chain it depends on.
     */
    @Test
    fun `the text hangs from the title rather than floating under it`() {
        assertTrue(
            "The body container is biased to the top",
            """app:layout_constraintVertical_bias="0"""" in welcomeLayout,
        )
        assertTrue(
            "The container is constrained down to the drawing",
            """app:layout_constraintBottom_toTopOf="@id/illustration"""" in welcomeLayout,
        )
        assertTrue(
            "The drawing is constrained back up to the container, which is what makes a chain",
            """app:layout_constraintTop_toBottomOf="@id/body_container"""" in welcomeLayout,
        )
    }

    /**
     * A string missing from a `values-xx/` file is a build failure — lint runs
     * with `warningsAsErrors` — but it fails on the day somebody happens to
     * build, naming a resource rather than the page it belongs to. Checked here
     * so the sentences of this page are said to be missing, and from where.
     */
    @Test
    fun `every language file carries the three sentences`() {
        val folders = resources.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .map { it.name to File(it, "strings.xml") }
            .filter { (_, file) ->
                file.isFile && """name="welcome_fleet_body"""" in file.readText()
            }

        assertTrue("The welcome strings were found", folders.isNotEmpty())
        for ((folder, file) in folders) {
            val text = file.readText()
            val missing = MARKS.map { it.first }.filterNot { """name="$it"""" in text }
            assertEquals("$folder is missing sentences", emptyList<String>(), missing)
        }
    }

    /**
     * The paragraph kept its first and last sentences; the enumeration between
     * them became the three lines, and leaving it in would say the same thing
     * twice.
     */
    @Test
    fun `the paragraph no longer enumerates the three fleets`() {
        for (folder in listOf("values", "values-fr")) {
            val body = File(resources, "$folder/strings.xml").readText()
                .substringAfter("""<string name="welcome_fleet_body">""")
                .substringBefore("</string>")
            assertTrue(
                "$folder: the paragraph still enumerates what the marks now say",
                body.count { it == '.' } <= SENTENCES_KEPT,
            )
        }
    }

    private companion object {
        /**
         * The three offers of §7, each with the disc the map draws for it. The
         * order is the page's: what one pedals alone, what a motor helps to
         * pedal, and both lent side by side.
         */
        val MARKS = listOf(
            "welcome_fleet_mechanical_only" to "marker_journey_station",
            "welcome_fleet_electric_only" to "marker_journey_station_electric",
            "welcome_fleet_mixed" to "marker_journey_station_mixed",
        )

        /**
         * Two sentences, hence at most two full stops — the enumeration that
         * was there held five.
         */
        const val SENTENCES_KEPT = 2
    }
}
