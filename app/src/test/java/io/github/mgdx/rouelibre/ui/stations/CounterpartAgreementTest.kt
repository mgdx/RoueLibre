package io.github.mgdx.rouelibre.ui.stations

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The two labels standing beside a station's counts, and their agreement
 * (SPEC §9, §14).
 *
 * The sheet and the list row each write a figure in a disc and the word naming
 * it next to it. Those words were fixed strings, so a station holding one free
 * dock read "1 PLACES LIBRES" — "1 free docks" in English — a sentence in
 * disagreement with the very figure it qualifies. They are plurals now.
 *
 * **What makes them unlike every other plural in this application** is that the
 * number is not in them: the disc beside the label already holds it, and an
 * item written `%1$d free docks` would say it twice. They are therefore
 * resolved with the count as a quantity and formatted with no argument at all,
 * and that is the rule a contributor breaks first — adding the placeholder back
 * because every neighbouring plural has one. Hence the second test below.
 *
 * The files are read from the disk, as `IndicatorScaleTest` reads `dimens.xml`
 * and `LocalesTest` the locale folders: what is checked is what the application
 * will be built with, and no Android runtime is involved (SPEC §14).
 */
class CounterpartAgreementTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    @Test
    fun `every language declares the plural categories its grammar asks for`() {
        REQUIRED_CATEGORIES.forEach { (language, expected) ->
            LABELS.forEach { label ->
                assertEquals(
                    "$label in ${folderOf(language)} does not carry the categories of $language",
                    expected,
                    categoriesOf(language, label),
                )
            }
        }
    }

    @Test
    fun `a label posed beside a figure never writes the figure itself`() {
        REQUIRED_CATEGORIES.keys.forEach { language ->
            LABELS.forEach { label ->
                val withAPlaceholder = itemsOf(language, label)
                    .filterValues { it.contains('%') }
                assertEquals(
                    "$label in ${folderOf(language)} repeats the count the disc already holds",
                    emptyMap<String, String>(),
                    withAPlaceholder,
                )
            }
        }
    }

    /**
     * The table above, held against the plurals the repository already ships.
     *
     * `docks_available` was translated language by language before this file
     * existed, so it is the reading of the plural rules this project has already
     * reviewed. Written as an equality so that a mistake in the table — the way
     * a wrong category would enter the two labels unnoticed — fails here rather
     * than being enshrined by the first test.
     */
    @Test
    fun `the table agrees with the plurals already translated`() {
        REQUIRED_CATEGORIES.forEach { (language, expected) ->
            assertEquals(
                "docks_available in ${folderOf(language)} disagrees with the table",
                expected,
                categoriesOf(language, "docks_available"),
            )
        }
    }

    /**
     * A language added since is a language these labels are missing from.
     *
     * The table cannot be derived from the folders — that is the whole of what
     * it knows — so it is the folders that are held against the table.
     */
    @Test
    fun `every translated folder is in the table`() {
        val folders = resources.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "strings.xml").isFile }
            // values-night and values-v31 qualify the resources, not a language.
            .map { it.name.removePrefix("values-").removePrefix("values") }
            .filter { it.isEmpty() || it.length == 2 }
            .toSet()
        assertEquals(REQUIRED_CATEGORIES.keys, folders)
    }

    private fun folderOf(language: String) =
        if (language.isEmpty()) "values" else "values-$language"

    private fun categoriesOf(language: String, name: String): Set<String> =
        itemsOf(language, name).keys

    private fun itemsOf(language: String, name: String): Map<String, String> {
        val file = File(resources, "${folderOf(language)}/strings.xml")
        val text = file.readText()
        val declaration = Regex(
            """<plurals name="$name"[^>]*>(.*?)</plurals>""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(text)
        checkNotNull(declaration) { "$name is not a plurals in ${file.path}" }
        return ITEM.findAll(declaration.groupValues[1])
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private companion object {
        /** The two labels, named as the resources name them. */
        val LABELS = listOf("counterpart_bikes", "counterpart_docks")

        val ITEM = Regex("""<item quantity="([a-z]+)">([^<]*)</item>""")

        /**
         * The plural categories each language distinguishes, after CLDR, keyed
         * by the resource qualifier — the empty name being `values/`, which
         * holds English. A language that agrees nothing has `other` and nothing
         * else; none is given a category its grammar does not draw, which would
         * be a form nobody can write.
         */
        val REQUIRED_CATEGORIES = mapOf(
            "" to setOf("one", "other"),
            "ar" to setOf("zero", "one", "two", "few", "many", "other"),
            "bs" to setOf("one", "few", "other"),
            "ca" to setOf("one", "many", "other"),
            "cs" to setOf("one", "few", "many", "other"),
            "da" to setOf("one", "other"),
            "de" to setOf("one", "other"),
            "es" to setOf("one", "many", "other"),
            "eu" to setOf("one", "other"),
            "fi" to setOf("one", "other"),
            "fr" to setOf("one", "many", "other"),
            "gl" to setOf("one", "other"),
            "hr" to setOf("one", "few", "other"),
            "hu" to setOf("one", "other"),
            "it" to setOf("one", "many", "other"),
            "ja" to setOf("other"),
            "lt" to setOf("one", "few", "many", "other"),
            "lv" to setOf("zero", "one", "other"),
            "nb" to setOf("one", "other"),
            "nl" to setOf("one", "other"),
            "pl" to setOf("one", "few", "many", "other"),
            "pt" to setOf("one", "many", "other"),
            "ro" to setOf("one", "few", "other"),
            "sk" to setOf("one", "few", "many", "other"),
            "sl" to setOf("one", "two", "few", "other"),
            "sq" to setOf("one", "other"),
            "sr" to setOf("one", "few", "other"),
            "sv" to setOf("one", "other"),
            "tr" to setOf("one", "other"),
            "zh" to setOf("other"),
        )
    }
}
