package io.github.mgdx.rouelibre.ui.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the storage screen says when "Check for updates" fails (SPEC §4.4, §14).
 *
 * It answered a check made in flight mode with "No connection. The download
 * picks up where it stopped." — the sentence written for an interrupted
 * transfer, read after a press that starts no transfer at all. A check sends a
 * single request, reads the published manifest and is over; there is nothing to
 * pick up, and the promise that something would was the defect.
 *
 * So the six failures a check can end on have a register of their own, held
 * here: none of them promises a resumption, and every one says the check itself
 * did not happen, which is what tells the reader to press again.
 *
 * The device's own refusal is the one that changes side between the two
 * screens. Downloading writes, and says to free some space; checking only reads
 * the installed versions to hold them against the published ones, so its
 * sentence speaks of a comparison that could not be made.
 *
 * The files are read from the disk, as `DownloadWordingTest` reads them: what is
 * checked is what the application will be built with, and no Android runtime is
 * involved (SPEC §14).
 */
class UpdateCheckWordingTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    /**
     * The defect itself: the check was rendered with `toDownloadMessage`, so
     * every one of its six failures read as the transfer's own sentence.
     */
    @Test
    fun `a failed check never borrows the download's sentence`() {
        LOCALES.forEach { folder ->
            FAMILY.forEach { name ->
                assertNotEquals(
                    "$folder answers a failed check with the download's sentence",
                    stringOf(folder, name.removeSuffix(SUFFIX) + "_download"),
                    stringOf(folder, name),
                )
            }
        }
    }

    /**
     * Nor the availability refresh's, which answers about bikes on a screen
     * that shows files.
     */
    @Test
    fun `a failed check never borrows the availability refresh's sentence`() {
        LOCALES.forEach { folder ->
            FAMILY.forEach { name ->
                assertNotEquals(
                    "$folder answers a failed check with the refresh's sentence",
                    stringOf(folder, name.removeSuffix(SUFFIX)),
                    stringOf(folder, name),
                )
            }
        }
    }

    /**
     * What separates this register from the download's: nothing was being
     * transferred, so nothing can pick up where it stopped.
     */
    @Test
    fun `no sentence of the family promises a resumption`() {
        LOCALES.forEach { folder ->
            val promising = FAMILY.filter { name ->
                RESUMPTION.getValue(folder).containsMatchIn(stringOf(folder, name))
            }
            assertEquals("$folder promises a resumption", emptyList<String>(), promising)
        }
    }

    /**
     * And what every one of them owes its reader instead: the check did not go
     * through, so it is to be made again in whole.
     */
    @Test
    fun `every sentence of the family says nothing was checked`() {
        LOCALES.forEach { folder ->
            val silent = FAMILY.filterNot { name ->
                NOTHING_HAPPENED.getValue(folder).containsMatchIn(stringOf(folder, name))
            }
            assertEquals("$folder says nothing of what happened", emptyList<String>(), silent)
        }
    }

    /**
     * The check reads the manifest from whoever hosts the datasets, and never
     * addresses the bike-share operator's server — the same reason the download
     * family may not name it either.
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
     * The device's refusal, read on the gesture that only reads: telling
     * somebody to free some space answers a write nobody asked for.
     */
    @Test
    fun `the device's refusal does not send the reader to free some space`() {
        assertEquals(
            "",
            stringOf("values", "error_local_storage$SUFFIX")
                .takeIf { it.contains("space", true) }
                .orEmpty(),
        )
        assertEquals(
            "",
            stringOf("values-fr", "error_local_storage$SUFFIX")
                .takeIf { it.contains("espace", true) }
                .orEmpty(),
        )
    }

    /** As for the download family, a sentence cut at an ellipsis says nothing. */
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
        const val SUFFIX = "_check"

        /** The two languages actually written; the rest hold the English text. */
        val LOCALES = listOf("values", "values-fr")

        /**
         * The failures an update check can end on, named as the resources name
         * them. [io.github.mgdx.rouelibre.core.DataError.NoCityChosen] is not
         * one of them: it reads the same on all three gestures and keeps the
         * refresh's sentence, which is what stops the three from drifting apart.
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
         * The longest a sentence of the family may be.
         *
         * The download family is held to eighty because each of its sentences
         * is read behind a dataset's name — "Graphe de routage : ", twenty
         * characters. A check names no dataset: it failed before knowing which
         * ones were concerned, and its sentence fills the snackbar on its own.
         * So it may have those twenty characters back, and no more.
         */
        const val LONGEST = 100

        /** How each language would promise a transfer carries on — and must not. */
        val RESUMPTION = mapOf(
            "values" to Regex("picks up|pick up|resume"),
            "values-fr" to Regex("reprend|reprendra|reprise"),
        )

        /** How each language says the check itself did not happen. */
        val NOTHING_HAPPENED = mapOf(
            "values" to Regex("Nothing"),
            "values-fr" to Regex("Rien"),
        )
    }
}
