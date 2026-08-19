package io.github.mgdx.rouelibre.ui.about

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Where the networks' credits are written, and where they are not (SPEC §4.5).
 *
 * The "about" screen wrote the credit of the network served — "Données V'lille
 * — Ilevia / MEL, licence ODbL" — under its availability paragraph, and the
 * sources page it opens three lines lower wrote the very same sentence, since
 * that page credits every city of the catalogue, the installed one included.
 * The same words twice, on two screens read one after the other.
 *
 * The credit itself is owed, not offered: the feeds' licences require it. So
 * this test holds both halves — that it left the "about" screen, and that
 * everything still carrying it stayed in place.
 *
 * The files are read from the disk, as `LocalesTest` reads the locale folders:
 * what is checked is what the application will be built with, and no Android
 * runtime is involved (SPEC §14).
 */
class AboutAttributionsTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    /** `app/src/main/java`, the sibling of the resources. */
    private val sources = File(resources.parentFile, "java/io/github/mgdx/rouelibre")

    @Test
    fun `the about screen holds no credit of the network served`() {
        assertFalse(
            "The about screen still declares a view for the network's credit.",
            layout("fragment_about").contains("network_attribution"),
        )
        // The credit only ever came from the active city's configuration:
        // reading one here again is how the duplicate would come back.
        val fragment = source("ui/about/AboutFragment.kt")
        assertFalse(
            "The about screen reads a city configuration again.",
            fragment.contains("activeCity") || fragment.contains("gbfs"),
        )
    }

    @Test
    fun `the about screen still says where availability comes from`() {
        assertTrue(
            "The availability paragraph left the about screen.",
            layout("fragment_about").contains("@string/about_attribution_gbfs"),
        )
        assertTrue(
            "The availability paragraph no longer names its source.",
            english("about_attribution_gbfs").contains("availability", ignoreCase = true),
        )
    }

    @Test
    fun `the sources page is reachable from the about screen`() {
        assertTrue(
            "The way to the sources page left the about screen.",
            layout("fragment_about").contains("@string/about_open_sources"),
        )
        assertTrue(
            "The about screen no longer opens the sources page.",
            source("ui/about/AboutFragment.kt").contains("DataSourcesFragment()"),
        )
    }

    /**
     * The credits have to be somewhere, and that somewhere is the sources page
     * — for every network, from the configurations shipped in the APK.
     */
    @Test
    fun `the sources page credits every network of the catalogue`() {
        val page = source("ui/about/DataSourcesFragment.kt")
        assertTrue(
            "The sources page no longer writes the networks' credits.",
            page.contains("gbfs.attribution"),
        )
        assertTrue(
            "The sources page no longer runs over the whole catalogue.",
            page.contains("catalogue.cities"),
        )
    }

    /**
     * A distinct obligation, and one that never moved: OpenStreetMap is
     * credited on the map itself (SPEC §4.5).
     */
    @Test
    fun `the map keeps its own attribution`() {
        assertTrue(
            "The map lost its attribution.",
            layout("fragment_map").contains("@string/map_attribution"),
        )
    }

    private fun layout(name: String): String = File(resources, "layout/$name.xml").readText()

    private fun source(path: String): String = File(sources, path).readText()

    private fun english(name: String): String {
        val line = File(resources, "values/strings.xml").readLines()
            .firstOrNull { it.contains("name=\"$name\"") }
        return checkNotNull(line) { "$name is not written in values/strings.xml." }
    }
}
