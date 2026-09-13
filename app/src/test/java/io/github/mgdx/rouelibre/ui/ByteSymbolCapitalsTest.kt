package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A line carrying a size or a date is not set in capitals (SPEC §9, SPEC §14).
 *
 * In French a megabyte is written "Mo" and a kilobyte "ko". Upper-cased they
 * are no longer unit symbols, and the storage screen read "3,7 MO · 31 AOÛT
 * 2026" while the sentence above it, set in ordinary text, had "9,1 Mo à
 * télécharger" right. The cause is `TextAppearance.RoueLibre.Label`, whose
 * small spaced capitals dress nineteen labels — and which English never caught
 * out, "MB" and "KB" being capitals already.
 *
 * What is held here is the separation itself: the labels made of words keep
 * the capitals, since the typography is a decision of the project, and the
 * three carrying a formatted value take `TextAppearance.RoueLibre.Label.Value`
 * instead. Both halves are asserted, because either one alone can be undone
 * without the other noticing — a hand emptying the parent style would fix
 * these three lines and silently unshout the other sixteen.
 *
 * The files are read from the disk, as `MapScreenRoomTest` and
 * `WelcomeFleetMarksTest` read them: what is checked is what the application
 * will be built with, and no Android runtime is involved (SPEC §14).
 */
class ByteSymbolCapitalsTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    private val typography by lazy { File(resources, "values/type.xml").readText() }

    /**
     * The three lines of the application that write a size, a date, or both,
     * in the face the short labels are set in: the storage screen's total, one
     * row of the sets it lists, and what a city occupies in the catalogue.
     */
    private val linesCarryingAValue = listOf(
        "layout/fragment_storage.xml" to "storage_total",
        "layout/item_dataset.xml" to "dataset_state",
        "layout/item_city.xml" to "city_installed",
    )

    /** The attributes of the view declaring [identity] in [layout]. */
    private fun attributesOf(layout: String, identity: String): String {
        val text = File(resources, layout).readText()
        val declared = text.indexOf("""android:id="@+id/$identity"""")
        assertTrue("$layout still declares $identity", declared > 0)
        return text.substring(declared, text.indexOf("/>", declared))
    }

    @Test
    fun `a line writing a size or a date is not upper-cased`() {
        for ((layout, identity) in linesCarryingAValue) {
            assertFalse(
                "$identity writes a unit symbol in capitals, which is no longer a symbol",
                attributesOf(layout, identity)
                    .contains("""@style/TextAppearance.RoueLibre.Label""""),
            )
            assertTrue(
                "$identity no longer takes the face that leaves its value alone",
                attributesOf(layout, identity)
                    .contains("""@style/TextAppearance.RoueLibre.Label.Value""""),
            )
        }
    }

    @Test
    fun `the face left to those lines gives up the capitals and nothing else`() {
        val declared = typography.indexOf("""<style name="TextAppearance.RoueLibre.Label.Value"""")
        assertTrue("The typography still declares the value face", declared > 0)
        val body = typography.substring(declared, typography.indexOf("</style>", declared))
        assertTrue(
            "The value face no longer switches the capitals off",
            """<item name="android:textAllCaps">false</item>""" in body,
        )
        assertFalse(
            "The value face redefines more than the capitals, and drifts from the labels it stands beside",
            "letterSpacing" in body || "textSize" in body || "textColor" in body,
        )
    }

    @Test
    fun `the labels made of words keep their capitals`() {
        val declared = typography.indexOf("""<style name="TextAppearance.RoueLibre.Label"""")
        assertTrue("The typography still declares the label face", declared > 0)
        val body = typography.substring(declared, typography.indexOf("</style>", declared))
        assertTrue(
            "The shared label face has lost its capitals, which is a change of the whole interface",
            """<item name="android:textAllCaps">true</item>""" in body,
        )
    }
}
