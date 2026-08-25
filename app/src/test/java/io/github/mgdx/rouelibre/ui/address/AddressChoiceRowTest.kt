package io.github.mgdx.rouelibre.ui.address

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the chooser for an address shared in a sentence shows on each row
 * (SPEC §7.8).
 *
 * The defect this holds shut was measured on the device: "Rendez-vous ici :
 * 12 rue Nationale, Lille" put up five rows reading "12 Rue Nationale" and
 * nothing else. The conurbation has a rue Nationale in several of its
 * municipalities, each with a number 12, and a list of five identical rows is
 * not the choice the fallback promises — it is a draw.
 *
 * The wording itself takes a `Context` and is left to the device, as
 * `JourneySummariesTest` leaves it; what is checked here is the decision
 * behind it — that a row carries what tells its address apart — and the
 * resource that decides it can never be written with one half missing.
 */
class AddressChoiceRowTest {

    /** Stands in for the string resource, which needs a device to be read. */
    private fun write(title: String, detail: String) = "$title\n$detail"

    /** What `toTitle` writes for either of two namesakes: the same thing. */
    private val sameTitle = "12 Rue Nationale"

    @Test
    fun `two namesakes in two municipalities are two different rows`() {
        // Their supporting lines are what the address search already shows
        // under each result, and what the chooser had dropped.
        val lille = addressChoiceRow(sameTitle, "59000 Lille · 450 m", ::write)
        val roubaix = addressChoiceRow(sameTitle, "59100 Roubaix · 11 km", ::write)

        assertNotEquals(lille, roubaix)
        // And neither is the bare title the chooser used to show.
        assertNotEquals(sameTitle, lille)
        assertTrue(lille.startsWith(sameTitle))
    }

    @Test
    fun `an address the index knows nothing more about stays its own title`() {
        // No supporting line, no empty half trailing behind the address.
        assertEquals(sameTitle, addressChoiceRow(sameTitle, "", ::write))
        assertEquals(sameTitle, addressChoiceRow(sameTitle, "   ", ::write))
    }

    @Test
    fun `every language writes the row from both halves`() {
        // A translation that drops one placeholder drops what tells two rows
        // apart, and the defect is back in that language alone (SPEC §9).
        startedFiles().forEach { folder ->
            val row = stringOf(folder.name, CHOICE_ROW)
            assertTrue("${folder.name} does not write the address", "%1\$s" in row)
            assertTrue("${folder.name} drops what tells it apart", "%2\$s" in row)
        }
    }

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    /** The folders that hold a `strings.xml`, `values/` included. */
    private fun startedFiles(): List<File> = resources.listFiles().orEmpty()
        .filter { it.isDirectory && File(it, "strings.xml").isFile }
        .sortedBy { it.name }

    private fun stringOf(folder: String, name: String): String {
        val file = File(resources, "$folder/strings.xml")
        val declaration = Regex("""<string name="$name">(.*?)</string>""")
            .find(file.readText())
        checkNotNull(declaration) { "$name is not declared in ${file.path}" }
        return declaration.groupValues[1]
    }

    private companion object {
        /** The resource that joins an address and what tells it apart. */
        const val CHOICE_ROW = "incoming_address_choice"
    }
}
