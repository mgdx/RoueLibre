package io.github.mgdx.rouelibre.ui.map

import io.github.mgdx.rouelibre.ui.TRANSLATED_LANGUAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What a screen reader is told the map is (SPEC §9, §11).
 *
 * MapLibre gives its `MapView` a content description of its own, translated
 * into the library's languages rather than into ours: under an Arabic
 * interface, every view on the screen spoke Arabic and the map spoke English.
 * The languages the application declares are its own business, so the sentence
 * has to be one of ours, and it has to exist in all of them.
 *
 * The library sets that description inside the `MapView` constructor, which
 * runs after `View` has read the attributes the layout gave it — an
 * `android:contentDescription` in the layout would be overwritten and would
 * look like a fix without being one. Hence [DescribedMapView], and hence this
 * test watching that the three layouts showing a map go on using it.
 */
class MapDescriptionTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    private val sources = File(resources.parentFile, "java/io/github/mgdx/rouelibre")

    /** The three layouts that show a map: the map screen and both journey results. */
    private val layoutsShowingAMap = listOf(
        "layout/fragment_map.xml",
        "layout/fragment_journey_result.xml",
        "layout-land/fragment_journey_result.xml",
    )

    @Test
    fun `every map on screen is one that describes itself in our words`() {
        for (path in layoutsShowingAMap) {
            val layout = File(resources, path).readText()
            assertTrue(
                "$path shows the map view that carries our own description",
                layout.contains("<io.github.mgdx.rouelibre.ui.map.DescribedMapView"),
            )
            assertTrue(
                "$path no longer leaves the library to describe the map",
                !layout.contains("<org.maplibre.android.maps.MapView"),
            )
        }
    }

    @Test
    fun `the description is set after the library has set its own`() {
        val view = File(sources, "ui/map/DescribedMapView.kt").readText()
        assertTrue(
            "The view takes its description from the application's strings",
            view.contains("contentDescription = context.getString(R.string.map_description)"),
        )
        assertTrue(
            "It is set in an initialiser, which runs once the superclass is built",
            view.substringAfter("init {").contains("contentDescription"),
        )
    }

    @Test
    fun `the description is written in every language the interface speaks`() {
        val english = descriptionIn("values")
        assertTrue("The English sentence explains the drag", english.contains("two fingers"))
        for (language in TRANSLATED_LANGUAGES - ENGLISH) {
            val translation = descriptionIn("values-$language")
            assertNotEquals(
                "$language translates the map's description rather than keeping the English",
                english,
                translation,
            )
        }
    }

    /**
     * The started files hold the English text, which is the convention for a
     * language nobody has translated yet — see `CONTRIBUTING.md`. What matters
     * is that the string is there at all: a missing one would leave that
     * language without a description rather than with an English one.
     */
    @Test
    fun `the started languages hold the English sentence`() {
        val english = descriptionIn("values")
        for (folder in startedFolders()) {
            assertEquals(
                "${folder.name} carries the English sentence, as its file does throughout",
                english,
                descriptionIn(folder.name),
            )
        }
    }

    private fun descriptionIn(folder: String): String {
        val file = File(resources, "$folder/strings.xml")
        assertTrue("$folder has a strings file", file.exists())
        val line = file.readLines().singleOrNull { it.contains("""name="map_description"""") }
        assertTrue("$folder writes the map's description", line != null)
        return checkNotNull(line).substringAfter(">").substringBeforeLast("<")
    }

    /** The language folders whose file is not a translation yet. */
    private fun startedFolders(): List<File> = checkNotNull(resources.listFiles())
        .filter { it.isDirectory && it.name.startsWith("values-") }
        .filter { it.name.removePrefix("values-") !in TRANSLATED_LANGUAGES }
        .filter { File(it, "strings.xml").exists() }

    private companion object {
        /** The language of `res/values/`, which has no folder of its own. */
        private const val ENGLISH = "en"
    }
}
