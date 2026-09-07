package io.github.mgdx.rouelibre.ui.settings

import io.github.mgdx.rouelibre.core.geo.BoundingBox
import io.github.mgdx.rouelibre.core.geo.Coordinates
import io.github.mgdx.rouelibre.data.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What one row of "my places" says (SPEC §7.6).
 *
 * The wording itself takes a `Context` and is left to the device, as
 * `AddressChoiceRowTest` leaves it; what is checked here is the decision behind
 * it — which of the two the row shows, what the ear is told, and whether a place
 * is called outside the city served — and that no reading of a place ever
 * erases one.
 */
class SettingsPlaceRowTest {

    /** Stands in for `settings_place_set`, which needs a device to be read. */
    private fun invite(setting: String) = "Set $setting"

    /** Stands in for `settings_place_description`. */
    private fun describe(setting: String, value: String) = "$setting: $value"

    /** Lille, roughly, and the box the city's datasets are cut from. */
    private val lille = BoundingBox(50.55, 2.90, 50.75, 3.25)

    private val home = SavedPlace("12 rue Nationale", Coordinates(50.63, 3.06))

    /** Lyon: a real address, and a long way outside the box above. */
    private val lyon = SavedPlace("5 rue de la République", Coordinates(45.76, 4.83))

    private fun row(place: SavedPlace?, area: BoundingBox?) = settingsPlaceRow(
        setting = "Home",
        place = place,
        coveredArea = area,
        none = "Not set",
        invite = ::invite,
        describe = ::describe,
    )

    @Test
    fun `a named place is what the row shows, and the ear is told what it answers`() {
        val shown = row(home, lille)

        // The row names the value and not the action (SPEC §7.6).
        assertEquals("12 rue Nationale", shown.label)
        // An address on its own does not say what question it answers, and this
        // screen puts the same question twice.
        assertEquals("Home: 12 rue Nationale", shown.spokenLabel)
        assertTrue(shown.canBeForgotten)
        assertFalse(shown.isOutsideCityServed)
    }

    @Test
    fun `where nothing is named the row invites, and there is nothing to forget`() {
        val shown = row(null, lille)

        // The invitation, and that one alone, says what pressing it does.
        assertEquals("Set Home", shown.label)
        assertEquals("Home: Not set", shown.spokenLabel)
        assertFalse(shown.canBeForgotten)
        // Nothing named is not a place outside the city served.
        assertFalse(shown.isOutsideCityServed)
    }

    @Test
    fun `a place outside the city served is said to be, and is not erased`() {
        val shown = row(lyon, lille)

        assertTrue(shown.isOutsideCityServed)
        // SPEC §7.3: ignored, never applied in silence — and never erased
        // either. The place is still the one the user named, it is still shown,
        // and it can still be forgotten from here.
        assertEquals("5 rue de la République", shown.label)
        assertEquals("Home: 5 rue de la République", shown.spokenLabel)
        assertTrue(shown.canBeForgotten)
    }

    @Test
    fun `with no city chosen nothing is called outside the city served`() {
        // No city, no box, nothing known: saying a place is outside would be
        // saying something we cannot know.
        val shown = row(lyon, null)

        assertFalse(shown.isOutsideCityServed)
        assertEquals("5 rue de la République", shown.label)
        assertTrue(shown.canBeForgotten)
    }

    @Test
    fun `a box that says nothing puts no place outside it`() {
        // A configuration whose box was never filled in reads as a point, and a
        // point covers nothing at all — every place would be "outside".
        val empty = BoundingBox(0.0, 0.0, 0.0, 0.0)

        assertFalse(row(home, empty).isOutsideCityServed)
        assertFalse(row(lyon, empty).isOutsideCityServed)
    }

    @Test
    fun `both languages write the spoken label from both halves`() {
        // A translation that drops one placeholder drops what the label is for:
        // the setting, or the value it settles. Only the two languages this
        // section was written in are checked — the other thirty are owed the
        // whole section and are a translation of their own (SPEC §9).
        listOf("values", "values-fr").forEach { folder ->
            val description = stringOf(folder, PLACE_DESCRIPTION)
            assertTrue("$folder does not name the setting", "%1\$s" in description)
            assertTrue("$folder does not name the value", "%2\$s" in description)

            listOf(PLACE_SET, PLACE_CHANGE, PLACE_CLEAR, PLACE_CLEARED).forEach { name ->
                assertTrue(
                    "$folder: $name does not name the setting",
                    "%1\$s" in stringOf(folder, name),
                )
            }
        }
    }

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    private fun stringOf(folder: String, name: String): String {
        val file = File(resources, "$folder/strings.xml")
        val declaration = Regex("""<string name="$name">(.*?)</string>""")
            .find(file.readText())
        checkNotNull(declaration) { "$name is not declared in ${file.path}" }
        return declaration.groupValues[1]
    }

    private companion object {
        const val PLACE_DESCRIPTION = "settings_place_description"
        const val PLACE_SET = "settings_place_set"
        const val PLACE_CHANGE = "settings_place_change"
        const val PLACE_CLEAR = "settings_place_clear"
        const val PLACE_CLEARED = "settings_place_cleared"
    }
}
