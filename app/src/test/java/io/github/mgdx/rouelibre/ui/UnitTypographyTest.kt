package io.github.mgdx.rouelibre.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * A figure and the unit naming it stay on the same line (SPEC §9, §14).
 *
 * The journey summary read "… · 1,0 km · 5" and then, on the next line, "m de
 * dénivelé": the figure had been left at the end of one line and its unit sent
 * to the start of the next, which reads as two facts rather than one. The join
 * is made **in the resources and nowhere else** — `Distances`, `Durations` and
 * the plurals hand a bare number to a string that places the symbol, since a
 * language is free to put it where it pleases — so the fix belongs there too,
 * as a **non-breaking space** in every one of them.
 *
 * The rule is deliberately narrow: only the space **between the number and the
 * word that names what it counts** is glued. Everything further along the
 * sentence keeps an ordinary space and stays free to wrap, which is what leaves
 * the line somewhere to break.
 *
 * The files are read from the disk, as `LocalesTest` reads the locale folders
 * and `CounterpartAgreementTest` the plurals: what is checked is what the
 * application will be built with, and no Android runtime is involved
 * (SPEC §14).
 */
class UnitTypographyTest {

    /** `app/src/main/res`, handed over by the build — see `app/build.gradle.kts`. */
    private val resources = File(
        checkNotNull(System.getProperty("rouelibre.locales")) {
            "The resource directory was not handed to the test."
        },
    )

    @Test
    fun `no figure is parted from its unit by a breakable space`() {
        val offending = mutableListOf<String>()
        TRANSLATED_LANGUAGES.forEach { language ->
            FIGURE_PLACEHOLDERS.forEach { (name, placeholder) ->
                valuesOf(language, name).forEach { value ->
                    if (value.contains("$placeholder ")) {
                        offending += "${folderOf(language)}/$name: $value"
                    }
                }
            }
        }
        assertEquals(
            "these write a figure and its unit with a breakable space between them",
            emptyList<String>(),
            offending,
        )
    }

    /**
     * "1 h 05" is one reading of a clock, not a figure followed by a second
     * one, so **every** space in it is glued rather than the first alone. The
     * languages that spell the two units out — "1 val. 05 min.", "1 godz.
     * 05 min" — are the reason this is checked as an absence of ordinary
     * spaces rather than as a shape.
     */
    @Test
    fun `an hour and its minutes are never split across two lines`() {
        val offending = TRANSLATED_LANGUAGES
            .flatMap { language ->
                valuesOf(language, "duration_hours_minutes")
                    .filter { it.contains(' ') }
                    .map { "${folderOf(language)}: $it" }
            }
        assertEquals(
            "a duration in hours and minutes may wrap between its two halves",
            emptyList<String>(),
            offending,
        )
    }

    private fun folderOf(language: String) = if (language == "en") "values" else "values-$language"

    /**
     * Every text a resource declares: the one value of a `<string>`, or the
     * items of a `<plurals>`, whose categories differ from one language to the
     * next and are `CounterpartAgreementTest`'s business rather than this
     * one's.
     */
    private fun valuesOf(language: String, name: String): List<String> {
        val text = File(resources, "${folderOf(language)}/strings.xml").readText()
        val declaration = Regex(
            """<(string|plurals) name="$name"[^>]*>(.*?)</\1>""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(text)
        checkNotNull(declaration) { "$name is not declared in ${folderOf(language)}" }
        val body = declaration.groupValues[2]
        if (declaration.groupValues[1] == "string") return listOf(body)
        return ITEM.findAll(body).map { it.groupValues[1] }.toList()
    }

    private companion object {
        /**
         * The resources that write a figure beside its unit, and **which of
         * their placeholders is that figure**.
         *
         * The placeholder has to be named: `station_detail_with_capacity` holds
         * a distance in `%1$s` — already carrying its own unit — and the count
         * of docking points in `%2$d`, and gluing the first would tie a
         * finished fact to the separator behind it.
         */
        private val FIGURE_PLACEHOLDERS = mapOf(
            // A length, written by `Distances` from a bare figure.
            "distance_metres" to "%1\$s",
            "distance_kilometres" to "%1\$s",
            "distance_feet" to "%1\$s",
            "distance_yards" to "%1\$s",
            "distance_miles" to "%1\$s",
            // A file size, on the storage screen and beside a city.
            "size_bytes" to "%1\$s",
            "size_kilobytes" to "%1\$s",
            "size_megabytes" to "%1\$s",
            "size_gigabytes" to "%1\$s",
            // A duration, written by `Durations`.
            "duration_minutes" to "%1\$d",
            // What a station holds, and what it holds them of.
            "bikes_available" to "%1\$d",
            "docks_available" to "%1\$d",
            "docks_total" to "%1\$d",
            "bikes_mechanical" to "%1\$d",
            "bikes_electric" to "%1\$d",
            "station_detail_with_capacity" to "%2\$d",
            // How old the availability figures are.
            "freshness_seconds" to "%1\$d",
            "freshness_minutes" to "%1\$d",
            "freshness_hours" to "%1\$d",
            "freshness_days" to "%1\$d",
            "freshness_months" to "%1\$d",
            // How many stations a city has, on the city screen.
            "city_stations" to "%1\$d",
            "city_detail" to "%1\$d",
            "city_detail_size_unknown" to "%1\$d",
        )

        private val ITEM = Regex("""<item quantity="[^"]+">(.*?)</item>""")
    }
}
