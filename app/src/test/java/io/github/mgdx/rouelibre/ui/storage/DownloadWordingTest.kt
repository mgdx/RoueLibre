package io.github.mgdx.rouelibre.ui.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the storage screen says when a dataset transfer fails (SPEC §4.4, §14).
 *
 * It reported an interrupted download with the sentences written for the
 * availability refresh — "No connection. The last known availability stays on
 * screen.", "The network's server returned an error (503)." — read under the
 * dataset's own name, "Map data: …". Two things were wrong with that, and this
 * test holds both.
 *
 * **The screen was answering about bikes.** Somebody who had just pressed
 * "Download 35.0 MB" was told what stays on screen, and nothing of the transfer
 * they were watching.
 *
 * **It was naming the wrong server.** "The network's server" is the bike-share
 * operator's, which this screen never talks to: the files come from whoever
 * hosts them, and sending somebody to the wrong end of a breakdown is worse
 * than telling them nothing.
 *
 * Each failure that can reach the download path has its own sentence now, and
 * every one of them says what happens next — the transfer picks up where it
 * stopped.
 *
 * The files are read from the disk, as `LocalesTest` reads the locale folders:
 * what is checked is what the application will be built with, and no Android
 * runtime is involved (SPEC §14).
 */
class DownloadWordingTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    @Test
    fun `a failed download never borrows the availability refresh's sentence`() {
        LOCALES.forEach { folder ->
            FAMILY.forEach { name ->
                assertNotEquals(
                    "$folder answers a failed download with the refresh's sentence",
                    stringOf(folder, name.removeSuffix(SUFFIX)),
                    stringOf(folder, name),
                )
            }
        }
    }

    /**
     * The first half of the defect: the storage screen downloads files, and has
     * no availability to report on.
     */
    @Test
    fun `no sentence of the family speaks of availability`() {
        assertEquals(
            emptyList<String>(),
            FAMILY.filter { stringOf("values", it).contains("availability", true) },
        )
        assertEquals(
            emptyList<String>(),
            FAMILY.filter { stringOf("values-fr", it).contains("disponibilit", true) },
        )
    }

    /**
     * The second half: the server these sentences describe is the one hosting
     * the datasets. It is not the bike-share network's, and calling it that
     * sends whoever reads it looking for a breakdown at the wrong end.
     */
    @Test
    fun `no sentence of the family blames the bike network`() {
        assertEquals(
            emptyList<String>(),
            FAMILY.filter { stringOf("values", it).contains("network", true) },
        )
        assertEquals(
            emptyList<String>(),
            FAMILY.filter { stringOf("values-fr", it).contains("réseau", true) },
        )
    }

    /**
     * What every one of them owes its reader: the transfer is resumable
     * (SPEC §4.4), so a failure here is never an invitation to start over.
     */
    @Test
    fun `every sentence of the family says the transfer picks up again`() {
        LOCALES.forEach { folder ->
            val silent = FAMILY.filterNot { name ->
                RESUMPTION.getValue(folder).containsMatchIn(stringOf(folder, name))
            }
            assertEquals("$folder says nothing of what happens next", emptyList<String>(), silent)
        }
    }

    /**
     * The sentences were right and unreadable: the four written for the server
     * failures ran to 119 characters where the bar showed about eighty, and
     * were cut at an ellipsis — the very defect this campaign fixed on the
     * station list. The screen gained a third line and a fourth, and they lost
     * "try again later", which told somebody who had just been told the
     * download resumes nothing they did not already have.
     */
    @Test
    fun `no sentence of the family outgrows the snackbar that shows it`() {
        LOCALES.forEach { folder ->
            val overlong = FAMILY
                .associateWith { stringOf(folder, it).length }
                .filterValues { it > LONGEST }
            assertEquals("$folder outgrows the snackbar", emptyMap<String, Int>(), overlong)
        }
    }

    /**
     * A string missing from a started file is a string that language reads
     * nowhere, translated or not (SPEC §9) — and one `lint` fails the build on.
     */
    @Test
    fun `every language carries every sentence of the family`() {
        resources.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "strings.xml").isFile }
            .forEach { folder ->
                val text = File(folder, "strings.xml").readText()
                FAMILY.forEach { name ->
                    assertTrue(
                        "$name is missing from ${folder.name}",
                        text.contains("""<string name="$name">"""),
                    )
                }
            }
    }

    private fun stringOf(folder: String, name: String): String {
        val file = File(resources, "$folder/strings.xml")
        val declaration = Regex("""<string name="$name">(.*?)</string>""")
            .find(file.readText())
        checkNotNull(declaration) { "$name is not declared in ${file.path}" }
        // The apostrophes are escaped for Android, not for the reader, and the
        // budget below counts what is read rather than what is written.
        return declaration.groupValues[1].replace("\\'", "’")
    }

    private companion object {
        const val SUFFIX = "_download"

        /** The two languages actually written; the rest hold the English text. */
        val LOCALES = listOf("values", "values-fr")

        /**
         * The failures a dataset transfer can end on, named as the resources
         * name them. Each is the download's own reading of the refresh string
         * of the same name without the suffix, which is what the first test
         * holds them against.
         */
        val FAMILY = listOf(
            "error_offline$SUFFIX",
            "error_timeout$SUFFIX",
            "error_server_refused$SUFFIX",
            "error_untrusted_server$SUFFIX",
            "error_malformed$SUFFIX",
            "error_local_storage$SUFFIX",
        )

        /**
         * The longest a sentence of the family may be, and where the figure
         * comes from.
         *
         * Measured on a Fairphone FP3, 1080 wide, French, text at its ordinary
         * size: a snackbar on this screen is 954 px across, and sixty-four
         * characters filled two lines of it — about forty to the line. Each of
         * these is read behind a dataset's name, "Graphe de routage : ", twenty
         * characters more. Eighty keeps the longest of them inside the three
         * lines the screen now gives, with a fourth in hand for whoever reads at
         * twice the text size.
         *
         * A count of characters is a budget and not a measurement — what a line
         * holds depends on which characters they are — and it is here so that a
         * sentence cannot grow back past what was measured without somebody
         * deciding to.
         */
        const val LONGEST = 80

        /** How each language says the transfer carries on rather than restarts. */
        val RESUMPTION = mapOf(
            "values" to Regex("picks up|pick up"),
            "values-fr" to Regex("reprend|reprendra"),
        )
    }
}
