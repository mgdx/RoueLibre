package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The person the French translation speaks in (SPEC §9).
 *
 * The French says *tu*: the application speaks to one person walking to a
 * station, not to a customer, and *vous* would put the counter of a commercial
 * notice between them and it. That was the tone of every one of the strings but
 * one — "Choisissez-en une pour afficher les stations", alone among three
 * hundred and twenty-five — which is exactly how such a slip enters: unseen,
 * because nobody rereads a file that size looking for one word.
 *
 * The check is a word list rather than a grammar, and it is deliberately blunt:
 * a string it stops that turns out to be innocent is one string to read, where a
 * rule subtle enough never to be wrong would let the next *vous* through. XML
 * comments are left out — they are written for contributors, whom §9 exempts.
 *
 * The file is read from the disk, as `LocalesTest` reads the locale folders:
 * what is checked is what the application will be built with, and no Android
 * runtime is involved (SPEC §14).
 */
class FrenchToneTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    @Test
    fun `no French string addresses the reader as vous`() {
        val french = File(resources, "values-fr/strings.xml").readText()
        val offending = COMMENTS.replace(french, "")
            .lines()
            .filter { VOUVOIEMENT.containsMatchIn(it) }
            .map { it.trim() }

        assertEquals(
            "The French translation says tu everywhere (SPEC §9)",
            emptyList<String>(),
            offending,
        )
    }

    /**
     * The same sentence was also too long to be read: a snackbar holds two
     * lines, and beside an action label those two came to about sixty
     * characters — "…pour afficher les stati…", cut mid-word.
     *
     * A character count is a budget, not a measurement: what a line holds
     * depends on the text size and on the screen, and the reading on the device
     * is what settles it. The budget is here so that the tail nobody could read
     * cannot be written back in without somebody deciding to.
     */
    @Test
    fun `the sentence read when no city is chosen stays within a snackbar`() {
        listOf("values", "values-fr").forEach { folder ->
            val sentence = stringOf(folder, "error_no_city_chosen")
            assertTrue(
                "$folder: “$sentence” is ${sentence.length} characters, " +
                    "more than a snackbar shows beside an action",
                sentence.length <= SNACKBAR_BUDGET,
            )
        }
    }

    private fun stringOf(folder: String, name: String): String {
        val file = File(resources, "$folder/strings.xml")
        val declaration = Regex("""<string name="$name">(.*?)</string>""")
            .find(file.readText())
        checkNotNull(declaration) { "$name is not declared in ${file.path}" }
        // The apostrophes are escaped for Android, not for the reader.
        return declaration.groupValues[1].replace("\\'", "’")
    }

    private companion object {
        /** Two lines of a snackbar standing beside an action label. */
        const val SNACKBAR_BUDGET = 60

        val COMMENTS = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)

        /**
         * The forms *vous* arrives in: the imperatives this interface actually
         * uses, the pronoun itself, and its possessives. The verbs are written
         * out rather than caught by their `-ez` ending, which "nez" and "assez"
         * carry too; a new one is added here the day a string needs it.
         */
        val VOUVOIEMENT = Regex(
            "Choisissez|Vérifiez|Essayez|Tirez|Appuyez|Sélectionnez|Réessayez|" +
                "[Vv]ous |[Vv]otre |[Vv]os ",
        )
    }
}
