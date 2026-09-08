package io.github.mgdx.rouelibre.ui.city

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the city screen says when location is refused to it (SPEC §10).
 *
 * Each screen answers a refusal with what **it** can still offer: the station
 * list says the stations stay in alphabetical order, the map says one may pick a
 * starting point by hand. The city screen borrowed the map's sentence — "pick
 * your starting point on the map or by address" — on a screen that carries
 * neither map nor address search, and where the reader was looking for their
 * city. It is the first-run path, and it answered a question nobody had asked.
 *
 * The files are read from the disk, as `OfflineWordingTest` and `LocalesTest`
 * read them: what is checked is what the application will be built with, and no
 * Android runtime is involved (SPEC §14).
 */
class CityLocationRefusalTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    @Test
    fun `the city screen answers a refusal in its own words`() {
        for (folder in listOf("values", "values-fr")) {
            val strings = File(resources, "$folder/strings.xml").readText()

            assertTrue(
                "$CITY is missing from $folder",
                strings.contains("""<string name="$CITY">"""),
            )
            assertNotEquals(
                "$folder answers the city screen with the map's sentence",
                sentence(strings, MAP),
                sentence(strings, CITY),
            )
        }
    }

    @Test
    fun `the screens that had their own sentence keep it`() {
        // The correction adds a sentence, it moves none: the map and the station
        // list are each right where they are used.
        val strings = File(resources, "values/strings.xml").readText()

        assertTrue(strings.contains("""<string name="$MAP">"""))
        assertTrue(strings.contains("""<string name="$STATIONS">"""))
        assertNotEquals(sentence(strings, MAP), sentence(strings, STATIONS))
    }

    private fun sentence(strings: String, name: String): String =
        Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(strings)
            ?.groupValues
            ?.get(1)
            .orEmpty()

    private companion object {
        const val CITY = "city_location_denied"
        const val MAP = "map_location_denied"
        const val STATIONS = "stations_location_denied"
    }
}
